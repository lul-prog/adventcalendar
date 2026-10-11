#!/usr/bin/env python3
"""
Static sanity checker for the Advent Calendar mod.

Because this sandbox has no JDK, this script substitutes for `javac -Xlint` as far as
possible using tree-sitter plus the real vanilla 1.20.1 (mojmaps) and Forge 1.20.1 sources as
ground truth:

  1. syntax errors in every .java file (tree-sitter)
  2. every imported / referenced class actually exists
  3. static field accesses (Type.CONSTANT) exist on that type or its supertypes
  4. constructor arities of `new Type(...)` match at least one declared constructor
  5. method calls on receivers whose type can be resolved: the method must exist (walking up
     the inheritance chain) and at least one overload must accept the given argument count

External classes are indexed from the vanilla dump and the Forge checkout; unknown receivers
are reported as "unresolved" (informational) rather than errors.
"""
import os
import re
import sys
import json
from collections import defaultdict

from tree_sitter import Language, Parser
import tree_sitter_java

MOD_SRC = "/home/user/adventcalendar/src/main/java"
VANILLA_SRC = "/tmp/mc1201"
FORGE_SRC = "/tmp/forge1201"

KEYWORDS = {"if", "for", "while", "switch", "return", "catch", "new", "else", "do", "try",
            "synchronized", "case", "assert", "throw", "super", "this", "instanceof"}


def strip_generics(text):
    out = []
    depth = 0
    for ch in text:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth = max(0, depth - 1)
        elif depth == 0:
            out.append(ch)
    return "".join(out)


def clean_type(text):
    if text is None:
        return None
    text = strip_generics(text).replace("?", "").strip()
    text = re.sub(r"\.\.\.", "[]", text)
    text = re.sub(r"\s+", " ", text)
    text = text.rstrip("[]").strip() if text.endswith("[]") else text
    while text.endswith("[]"):
        text = text[:-2]
    if text.startswith("final "):
        text = text[6:].strip()
    return text or None


class Unit:
    """One parsed .java file."""

    def __init__(self, path, source, tree, source_kind):
        self.path = path
        self.source = source
        self.tree = tree
        self.kind = source_kind
        self.package = None
        self.types = {}          # simple name -> TypeInfo
        self.imports = {}        # simple name -> fqn (explicit imports)
        self.star_imports = []   # list of packages
        self.has_errors = False

    def resolve(self, simple):
        """simple type name -> fqn or None"""
        if not simple:
            return None
        simple = clean_type(simple)
        if simple is None or not simple or simple in ("void", "boolean", "byte", "char", "short",
                                                      "int", "long", "float", "double"):
            return None
        if "." in simple:
            return simple
        if simple in self.imports:
            return self.imports[simple]
        if self.package and simple in self.types:
            return self.package + "." + simple
        for pkg in self.star_imports:
            candidate = pkg + "." + simple
            if candidate in CLASSES:
                return candidate
        # java.lang and the same package
        if "java.lang." + simple in CLASSES:
            return "java.lang." + simple
        if self.package and self.package + "." + simple in CLASSES:
            return self.package + "." + simple
        return None


class TypeInfo:
    def __init__(self, fqn):
        self.fqn = fqn
        self.superclass = None
        self.interfaces = []
        self.methods = defaultdict(set)   # name -> set of arities (-1 = varargs/unknown)
        self.returns = {}                 # name -> return type (best effort)
        self.fields = {}                  # name -> type (best effort)
        self.ctors = set()
        self.is_interface = False
        self.is_enum = False
        self.file = None
        self.unit = None
        self.kind = "unknown"
        self.super_names = []
        self.iface_names = []


CLASSES = {}          # fqn -> TypeInfo
SIMPLE_INDEX = defaultdict(set)   # simple name -> set of fqn


def add_method(info, name, arity):
    info.methods[name].add(arity)


def parse_file(path, kind):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            source = handle.read()
    except OSError:
        return None
    data = source.encode("utf-8", "replace")
    tree = PARSER.parse(data)
    unit = Unit(path, source, tree, kind)
    root = tree.root_node
    if root.has_error:
        unit.has_errors = True

    # package
    for node in root.children:
        if node.type == "package_declaration":
            unit.package = data[node.start_byte:node.end_byte].decode("utf-8", "replace") \
                .replace("package", "").replace(";", "").strip()
        elif node.type == "import_declaration":
            text = data[node.start_byte:node.end_byte].decode("utf-8", "replace")
            text = text.replace("import", "").replace("static", "").replace(";", "").strip()
            if text.endswith(".*"):
                unit.star_imports.append(text[:-2])
            else:
                unit.imports[text.split(".")[-1]] = text

    class_visitor = ClassVisitor(unit, data)
    class_visitor.visit(root)
    return unit


class ClassVisitor:
    """Collects type declarations (including nested) and their members."""

    def __init__(self, unit, data):
        self.unit = unit
        self.data = data

    def text(self, node):
        return self.data[node.start_byte:node.end_byte].decode("utf-8", "replace")

    def visit(self, node, owner=None):
        for child in node.children:
            if child.type in ("class_declaration", "interface_declaration", "enum_declaration",
                              "record_declaration", "annotation_type_declaration"):
                self.handle_type(child, owner)
            elif child.type in ("field_declaration", "method_declaration", "constructor_declaration",
                                "enum_constant"):
                if owner is not None:
                    self.handle_member(child, owner)
            elif child.type in ("block", "class_body", "interface_body", "enum_body",
                                "record_body", "declaration_list"):
                self.visit(child, owner)
            else:
                self.visit(child, owner)

    def handle_type(self, node, owner):
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        name = self.text(name_node)
        fqn = (owner.fqn + "$" + name) if owner else ((self.unit.package + "." + name)
                                                      if self.unit.package else name)
        info = TypeInfo(fqn)
        info.file = self.unit.path
        info.kind = self.unit.kind
        info.is_interface = node.type == "interface_declaration"
        info.is_enum = node.type == "enum_declaration"
        info.unit = self.unit
        superclass = node.child_by_field_name("superclass")
        if superclass is not None:
            info.super_names.append(self.text(superclass).replace("extends", "").strip())
        interface_lists = []
        interfaces = node.child_by_field_name("interfaces")
        if interfaces is not None:
            interface_lists.append(interfaces)
        for child in node.children:
            if child.type in ("extends_interfaces", "super_interfaces", "interfaces"):
                interface_lists.append(child)
        for interface_node in interface_lists:
            text = self.text(interface_node)
            text = re.sub(r"^(implements|extends)", "", text).strip()
            for part in re.split(r",", text):
                part = part.strip()
                if part:
                    info.iface_names.append(part)
        CLASSES.setdefault(fqn, info)
        self.unit.types[name] = info
        SIMPLE_INDEX[name].add(fqn)
        if owner is not None:
            owner.fields[name] = fqn

        body = node.child_by_field_name("body")
        if body is not None:
            for child in body.children:
                if child.type in ("field_declaration", "method_declaration",
                                  "constructor_declaration"):
                    self.handle_member(child, info, record=node.type == "record_declaration")
                elif child.type in ("class_declaration", "interface_declaration",
                                    "enum_declaration", "record_declaration",
                                    "annotation_type_declaration"):
                    self.handle_type(child, info)
                elif child.type == "enum_constant":
                    constant_name = self.text(child).split("(")[0].strip()
                    if constant_name:
                        info.fields[constant_name] = info.fqn
                elif child.type == "enum_body_declarations":
                    for grandchild in child.children:
                        if grandchild.type in ("field_declaration", "method_declaration",
                                               "constructor_declaration"):
                            self.handle_member(grandchild, info)
                        elif grandchild.type in ("class_declaration", "interface_declaration",
                                                 "enum_declaration", "record_declaration",
                                                 "annotation_type_declaration"):
                            self.handle_type(grandchild, info)

    def handle_member(self, node, info, record=False):
        if node.type == "field_declaration":
            type_node = node.child_by_field_name("type")
            declared = clean_type(self.text(type_node)) if type_node is not None else None
            for child in node.children:
                if child.type == "variable_declarator":
                    name_node = child.child_by_field_name("name")
                    if name_node is not None:
                        info.fields[self.text(name_node)] = declared
                elif child.type == "enum_constant":
                    constant = self.text(child).split("(")[0].strip()
                    if constant:
                        info.fields[constant] = info.fqn
            return
        if node.type == "method_declaration":
            name_node = node.child_by_field_name("name")
            if name_node is None:
                return
            params = node.child_by_field_name("parameters")
            arity, varargs = self.arity(params)
            method_name = self.text(name_node)
            add_method(info, method_name, arity if not varargs else -1)
            return_type = node.child_by_field_name("type")
            if return_type is not None:
                info.returns[method_name] = clean_type(self.text(return_type))
            return
        if node.type == "constructor_declaration":
            params = node.child_by_field_name("parameters")
            arity, varargs = self.arity(params)
            info.ctors.add(arity if not varargs else -1)

    def arity(self, params):
        if params is None:
            return 0, False
        count = 0
        varargs = False
        for child in params.children:
            if child.type in ("formal_parameter", "spread_parameter"):
                if child.type == "receiver_parameter":
                    continue
                count += 1
                if child.type == "spread_parameter":
                    varargs = True
        return count, varargs


SKIP_DIRS = {"test", "tests", "build", ".git", "gradle", "buildSrc", "patches"}


def index_tree(root_dir, kind, limit=None):
    count = 0
    for dirpath, dirnames, filenames in os.walk(root_dir):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for filename in filenames:
            if not filename.endswith(".java"):
                continue
            path = os.path.join(dirpath, filename)
            parse_file(path, kind)
            count += 1
            if limit and count >= limit:
                return count
    return count


def resolve_in(info, name):
    if info is not None and info.unit is not None:
        resolved = info.unit.resolve(name)
        if resolved:
            return resolved
    return resolve_global(name)


def ordered_supertypes(fqn):
    """Breadth first: the type itself first, then its parents (subclasses win)."""
    order = []
    queue = [fqn]
    seen = set()
    while queue:
        current = queue.pop(0)
        if current in seen or current not in CLASSES:
            continue
        seen.add(current)
        order.append(current)
        info = CLASSES[current]
        for parent in info.super_names + info.iface_names:
            resolved = resolve_in(info, parent)
            if resolved:
                queue.append(resolved)
    return order


def supertypes(fqn, seen=None):
    if seen is None:
        seen = set()
    if fqn in seen:
        return seen
    seen.add(fqn)
    info = CLASSES.get(fqn)
    if info is None:
        return seen
    for parent in info.super_names + info.iface_names:
        resolved = resolve_in(info, parent)
        if resolved:
            supertypes(resolved, seen)
    return seen


def resolve_global(name):
    name = clean_type(name)
    if not name:
        return None
    if name in CLASSES:
        return name
    candidates = SIMPLE_INDEX.get(name)
    if candidates:
        # prefer net.minecraft / net.minecraftforge / com.adventcalendar
        for prefix in ("net.minecraft.", "net.minecraftforge.", "com.adventcalendar."):
            for candidate in sorted(candidates):
                if candidate.startswith(prefix):
                    return candidate
        return sorted(candidates)[0]
    return None


def has_field(fqn, field):
    for candidate in ordered_supertypes(fqn):
        info = CLASSES.get(candidate)
        if info and field in info.fields:
            return True
    return False


JAVA_LANG_TYPES = {
    "System", "Math", "String", "Double", "Float", "Integer", "Long", "Short", "Byte",
    "Boolean", "Character", "Object", "Class", "Enum", "Record", "Void", "Thread",
    "Runnable", "Iterable", "Comparable", "Number", "Exception", "RuntimeException",
    "Error", "Throwable", "StringBuilder", "StringBuffer", "CharSequence", "StringJoiner",
    "IllegalArgumentException", "IllegalStateException", "UnsupportedOperationException",
    "NumberFormatException", "NullPointerException", "IndexOutOfBoundsException",
    "ArithmeticException", "ClassCastException", "ReflectiveOperationException",
    "Override", "Deprecated", "SuppressWarnings", "SafeVarargs", "FunctionalInterface",
    "ProcessHandle", "StackTraceElement", "ThreadLocal", "Runtime", "Package", "Module",
    "AutoCloseable", "Cloneable", "Appendable", "Readable", "VerifyError", "AssertionError",
    "InterruptedException", "InstantiationException", "NoSuchMethodException",
    "NoSuchFieldException", "OutOfMemoryError", "StackOverflowError", "SecurityException",
}

UNIVERSAL_METHODS = {"toString", "equals", "hashCode", "getClass", "clone", "notify",
                     "notifyAll", "wait", "finalize", "name", "ordinal", "values", "valueOf",
                     "compareTo", "getDeclaringClass", "describeConstable", "registerNatives"}

TRUSTED_PREFIXES = ("net.minecraft.", "net.minecraftforge.", "com.adventcalendar.")

EXTERNAL_PREFIXES = ("java.", "javax.", "sun.", "com.google.", "com.mojang.authlib.",
                     "org.apache.", "org.slf4j.", "it.unimi.", "com.electronwill.",
                     "org.jetbrains.", "org.objectweb.", "cpw.mods.", "com.mojang.brigadier.",
                     "javax.annotation.", "net.minecraftforge.eventbus.", "net.minecraftforge.api.distmarker.")


def has_method(fqn, method, arity):
    found_name = False
    for candidate in ordered_supertypes(fqn):
        info = CLASSES.get(candidate)
        if not info or method not in info.methods:
            continue
        found_name = True
        arities = info.methods[method]
        if -1 in arities or arity in arities:
            return True, True
    return found_name, False


def has_ctor(fqn, arity):
    info = CLASSES.get(fqn)
    if info is None:
        return None
    if not info.ctors:
        return None
    if -1 in info.ctors or arity in info.ctors:
        return True
    return False


class Checker:
    def __init__(self, unit, data):
        self.unit = unit
        self.data = data
        self.errors = []
        self.notes = []
        self.current_type = None

    def text(self, node):
        return self.data[node.start_byte:node.end_byte].decode("utf-8", "replace")

    def error(self, node, message):
        row = node.start_point[0] + 1
        self.errors.append(f"{self.unit.path}:{row}: {message}")

    def note(self, node, message):
        row = node.start_point[0] + 1
        self.notes.append(f"{self.unit.path}:{row}: {message}")

    def check(self):
        root = self.unit.tree.root_node
        if root.has_error:
            self.error(root, "syntax error in this file")
        self.walk(root, None)

    # ---- scope handling
    def collect_scope(self, node, scope):
        """Map identifier -> type name for declarations inside a block/method."""
        for child in node.children:
            if child.type == "local_variable_declaration":
                type_node = child.child_by_field_name("type")
                if type_node is not None:
                    type_name = clean_type(self.text(type_node))
                    for declarator in child.children:
                        if declarator.type == "variable_declarator":
                            name_node = declarator.child_by_field_name("name")
                            if name_node is not None:
                                scope[self.text(name_node)] = type_name
            elif child.type in ("class_declaration", "interface_declaration", "enum_declaration",
                                "record_declaration"):
                pass
            elif child.type in ("block", "for_statement", "enhanced_for_statement", "if_statement",
                                "while_statement", "switch_block", "try_statement",
                                "try_with_resources_statement", "lambda_expression"):
                self.collect_scope(child, scope)

    def walk(self, node, scope):
        if scope is None:
            scope = {}
        if node.type == "class_declaration":
            name_node = node.child_by_field_name("name")
            previous = self.current_type
            if name_node is not None:
                info = self.unit.types.get(self.text(name_node))
                if info is not None:
                    self.current_type = info
            for child in node.children:
                self.walk(child, scope)
            self.current_type = previous
            return

        if node.type in ("method_declaration", "constructor_declaration"):
            scope = dict(scope)
            if node.type == "method_declaration":
                self.check_override(node)
                self.check_accidental_override(node)
                self.check_method_captures(node)
            params = node.child_by_field_name("parameters")
            if params is not None:
                for child in params.children:
                    if child.type in ("formal_parameter", "spread_parameter"):
                        type_node = child.child_by_field_name("type")
                        name_node = child.child_by_field_name("name")
                        if type_node is not None and name_node is not None:
                            scope[self.text(name_node)] = clean_type(self.text(type_node))
            body = node.child_by_field_name("body")
            if body is not None:
                self.collect_scope(body, scope)
                for child in body.children:
                    self.walk(child, scope)
            return

        if node.type == "field_declaration":
            type_node = node.child_by_field_name("type")
            if type_node is not None:
                type_name = clean_type(self.text(type_node))
                for declarator in node.children:
                    if declarator.type == "variable_declarator":
                        name_node = declarator.child_by_field_name("name")
                        if name_node is not None:
                            scope[self.text(name_node)] = type_name
            return

        if node.type == "local_variable_declaration":
            type_node = node.child_by_field_name("type")
            if type_node is not None:
                type_name = clean_type(self.text(type_node))
                for declarator in node.children:
                    if declarator.type == "variable_declarator":
                        name_node = declarator.child_by_field_name("name")
                        if name_node is not None:
                            scope[self.text(name_node)] = type_name
            self.check_assignment_compatibility(node, scope)

        if node.type == "class_declaration":
            for child in node.children:
                if child.type == "class_body":
                    nested = dict(scope)
                    self.collect_scope(child, nested)
                    for grandchild in child.children:
                        self.walk(grandchild, nested)
            return

        if node.type == "method_invocation":
            self.check_invocation(node, scope)
            object_node = node.child_by_field_name("object")
            if object_node is not None and object_node.type == "identifier":
                self.check_type_visibility(object_node, scope)
        elif node.type == "object_creation_expression":
            self.check_creation(node, scope)
            type_node = node.child_by_field_name("type")
            if type_node is not None and type_node.type == "type_identifier":
                self.check_type_visibility(type_node, scope)
        elif node.type == "field_access":
            self.check_field_access(node, scope)
        elif node.type == "marker_annotation" or node.type == "annotation":
            pass

        for child in node.children:
            self.walk(child, scope)

    # ---- @Override verification
    # ---- "must be final or effectively final" (lambdas capturing reassigned locals)
    def check_method_captures(self, node):
        body = node.child_by_field_name("body")
        if body is None:
            return
        self.analyse_captures(body, node.child_by_field_name("parameters"), initial={})
        # lambdas are their own scope: analyse each of them separately as well
        for lambda_node in self.find_lambdas(body):
            parameters = lambda_node.child_by_field_name("parameters")
            lambda_body = lambda_node.child_by_field_name("body")
            if lambda_body is not None:
                self.analyse_captures(lambda_body, parameters, initial={}, owner=lambda_node)

    def analyse_captures(self, body, params, initial, owner=None):
        declarations = dict(initial)   # name -> [assignments, is_param]
        if params is not None:
            for child in params.children:
                if child.type in ("formal_parameter", "spread_parameter"):
                    name_node = child.child_by_field_name("name")
                    if name_node is not None:
                        declarations[self.text(name_node)] = [0, True]
                elif child.type == "identifier":       # single lambda parameter
                    declarations[self.text(child)] = [0, True]
        self.collect_assignments(body, declarations)
        for lambda_node in self.find_lambdas(body):
            inner = set()
            parameters = lambda_node.child_by_field_name("parameters")
            if parameters is not None:
                for child in parameters.children:
                    if child.type == "identifier":
                        inner.add(self.text(child))
                    elif child.type in ("formal_parameter", "spread_parameter"):
                        name_node = child.child_by_field_name("name")
                        if name_node is not None:
                            inner.add(self.text(name_node))
            self.collect_declared_names(lambda_node.child_by_field_name("body"), inner)
            used = set()
            self.collect_identifiers(lambda_node, used)
            for name in sorted(used - inner):
                entry = declarations.get(name)
                if entry is None:
                    continue
                assignments, is_param = entry
                limit = 0 if is_param else 1
                if assignments > limit:
                    self.error(lambda_node, f"`{name}` is reassigned and therefore cannot be "
                                            f"used inside a lambda (must be final or effectively final)")

    def collect_assignments(self, node, declarations):
        if node.type in ("class_declaration", "interface_declaration", "enum_declaration",
                         "record_declaration", "lambda_expression"):
            return
        if node.type == "local_variable_declaration":
            has_initializer = any(child.type == "variable_declarator"
                                  and child.child_by_field_name("value") is not None
                                  for child in node.children)
            for child in node.children:
                if child.type == "variable_declarator":
                    name_node = child.child_by_field_name("name")
                    if name_node is not None:
                        name = self.text(name_node)
                        current = declarations.get(name)
                        if current is None:
                            declarations[name] = [1 if has_initializer else 0, False]
                        else:
                            current[0] += 1 if has_initializer else 0
        elif node.type == "assignment_expression":
            left = node.child_by_field_name("left")
            if left is not None and left.type == "identifier":
                name = self.text(left)
                if name in declarations:
                    declarations[name][0] += 1
        elif node.type == "update_expression":
            # tree-sitter-java exposes no fields here: `++x`, `x++`, `x--`
            for child in node.children:
                if child.type == "identifier":
                    name = self.text(child)
                    if name in declarations:
                        declarations[name][0] += 1
                    break
        for child in node.children:
            self.collect_assignments(child, declarations)

    def find_lambdas(self, node, found=None):
        if found is None:
            found = []
        if node.type == "lambda_expression":
            found.append(node)
            return found
        if node.type in ("class_declaration", "interface_declaration", "enum_declaration",
                         "record_declaration"):
            return found
        for child in node.children:
            self.find_lambdas(child, found)
        return found

    def collect_declared_names(self, node, names):
        if node is None:
            return
        if node.type in ("class_declaration", "interface_declaration", "enum_declaration"):
            return
        if node.type == "local_variable_declaration":
            for child in node.children:
                if child.type == "variable_declarator":
                    name_node = child.child_by_field_name("name")
                    if name_node is not None:
                        names.add(self.text(name_node))
        if node.type == "lambda_expression":
            parameters = node.child_by_field_name("parameters")
            if parameters is not None:
                for child in parameters.children:
                    if child.type == "identifier":
                        names.add(self.text(child))
        for child in node.children:
            self.collect_declared_names(child, names)

    def collect_identifiers(self, node, names):
        if node.type == "identifier":
            names.add(self.text(node))
        for child in node.children:
            self.collect_identifiers(child, names)

    def check_override(self, node):
        if self.current_type is None:
            return
        annotations = [child for child in node.children
                       if child.type in ("marker_annotation", "annotation")]
        if not any(self.text(annotation).lstrip("@").split("(")[0].strip() == "Override"
                   for annotation in annotations):
            return
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        name = self.text(name_node)
        params = node.child_by_field_name("parameters")
        arity = count_args(params)
        for candidate in ordered_supertypes(self.current_type.fqn)[1:]:
            info = CLASSES.get(candidate)
            if info is None or name not in info.methods:
                continue
            arities = info.methods[name]
            if -1 not in arities and arity not in arities:
                continue
            parent_return = info.returns.get(name)
            declared_node = node.child_by_field_name("type")
            declared = clean_type(self.text(declared_node)) if declared_node is not None else None
            if parent_return is None or declared is None:
                return
            parent_resolved = resolve_in(info, parent_return) or parent_return
            declared_resolved = self.unit.resolve(declared) or declared
            if parent_resolved != declared_resolved:
                self.error(node, f"@Override `{name}` returns {declared_resolved} but "
                                 f"{candidate}.{name} returns {parent_resolved}")
            return

    def check_accidental_override(self, node):
        """A method with the name and parameters of an inherited one must override it."""
        if self.current_type is None or self.current_type.kind == "vanilla-lib":
            return
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        name = self.text(name_node)
        if name in ("equals", "hashCode", "toString"):
            return
        arity = count_args(node.child_by_field_name("parameters"))
        declared_node = node.child_by_field_name("type")
        declared = clean_type(self.text(declared_node)) if declared_node is not None else None
        for candidate in ordered_supertypes(self.current_type.fqn)[1:]:
            info = CLASSES.get(candidate)
            if info is None or name not in info.methods:
                continue
            arities = info.methods[name]
            if -1 not in arities and arity not in arities:
                continue
            parent_return = info.returns.get(name)
            if parent_return is None or declared is None:
                continue
            parent_resolved = resolve_in(info, parent_return) or parent_return
            declared_resolved = self.unit.resolve(declared) or declared
            if parent_resolved != declared_resolved:
                self.error(node, f"`{name}(...)` hides {candidate}.{name} which returns "
                                 f"{parent_resolved} (declared {declared_resolved}) - "
                                 f"rename it or override with the right return type")
                return

    def receiver_type(self, object_node, scope):
        if object_node is None:
            return None, "implicit"
        if object_node.type == "identifier":
            name = self.text(object_node)
            if name == "this":
                return self.current_self(scope), "this"
            if name == "super":
                return None, "super"
            if name in scope:
                return scope[name], "var"
            # static reference to a class?
            resolved = self.unit.resolve(name)
            if resolved:
                return resolved, "static"
            return None, "unknown-identifier"
        if object_node.type == "this":
            return self.current_self(scope), "this"
        if object_node.type == "method_invocation":
            # infer from method name heuristics
            inner = object_node.child_by_field_name("name")
            if inner is not None:
                method = self.text(inner)
                guesses = {
                    "getInstance": None,
                    "getServer": "net.minecraft.server.MinecraftServer",
                    "getPlayer": "net.minecraft.server.level.ServerPlayer",
                    "getSender": "net.minecraft.server.level.ServerPlayer",
                    "level": "net.minecraft.world.level.Level",
                    "serverLevel": "net.minecraft.server.level.ServerLevel",
                    "getLevel": "net.minecraft.world.level.Level",
                    "getEntity": "net.minecraft.world.entity.Entity",
                    "getCommandSourceStack": "net.minecraft.commands.CommandSourceStack",
                    "createCommandSourceStack": "net.minecraft.commands.CommandSourceStack",
                    "getInventory": "net.minecraft.world.entity.player.Inventory",
                    "getMainHandItem": "net.minecraft.world.item.ItemStack",
                    "getItemInHand": "net.minecraft.world.item.ItemStack",
                    "getItem": "net.minecraft.world.item.ItemStack",
                    "getHoverName": "net.minecraft.network.chat.Component",
                    "getDisplayName": "net.minecraft.network.chat.Component",
                    "getPlayers": "net.minecraft.server.players.PlayerList",
                    "getPlayerList": "net.minecraft.server.players.PlayerList",
                    "getSoundManager": "net.minecraft.client.sounds.SoundManager",
                    "getTextureManager": "net.minecraft.client.renderer.texture.TextureManager",
                    "getCommands": "net.minecraft.commands.Commands",
                    "getGameProfile": "com.mojang.authlib.GameProfile",
                    "getBoundingBox": "net.minecraft.world.phys.AABB",
                    "getPersistentData": "net.minecraft.nbt.CompoundTag",
                    "getOrCreateTag": "net.minecraft.nbt.CompoundTag",
                    "getTag": "net.minecraft.nbt.CompoundTag",
                    "getCustomName": "net.minecraft.network.chat.Component",
                    "getSkin": "java.lang.String",
                }
                if method in guesses and guesses[method]:
                    return guesses[method], "chain"
                inner_object = object_node.child_by_field_name("object")
                owner = None
                if inner_object is None:
                    owner = self.current_type.fqn if self.current_type is not None else None
                else:
                    owner = self.receiver_type(inner_object, scope)[0]
                if owner:
                    found = self.find_return_type(owner, method)
                    if found:
                        return found, "chain"
            return None, "chain"
        if object_node.type == "field_access":
            base = object_node.child_by_field_name("object")
            field = object_node.child_by_field_name("field")
            if base is not None and field is not None:
                base_type = self.receiver_type(base, scope)[0]
                if base_type:
                    found = self.find_field_type(base_type, self.text(field))
                    if found:
                        return found, "field-chain"
            return None, "field-chain"
        if object_node.type == "parenthesized_expression":
            for child in object_node.children:
                if child.type not in ("(", ")"):
                    return self.receiver_type(child, scope)
        return None, "unknown-expression"

    def current_self(self, scope):
        return scope.get("__self__")

    def find_return_type(self, fqn, method):
        for candidate in ordered_supertypes(fqn):
            info = CLASSES.get(candidate)
            if info and method in info.returns:
                return resolve_in(info, info.returns[method]) or info.returns[method]
        return None

    def find_field_type(self, fqn, field):
        for candidate in ordered_supertypes(fqn):
            info = CLASSES.get(candidate)
            if info and field in info.fields:
                declared = info.fields[field]
                return resolve_in(info, declared) or declared
        return None

    def check_invocation(self, node, scope):
        name_node = node.child_by_field_name("name")
        if name_node is None:
            return
        method = self.text(name_node)
        args = node.child_by_field_name("arguments")
        arity = count_args(args)
        object_node = node.child_by_field_name("object")
        if object_node is None:
            # implicit `this`: must exist in the enclosing type (or in java.lang/Object)
            if self.current_type is None or method in UNIVERSAL_METHODS:
                return
            declared = False
            for candidate in ordered_supertypes(self.current_type.fqn):
                info = CLASSES.get(candidate)
                if info and method in info.methods:
                    declared = True
                    arities = info.methods[method]
                    if -1 in arities or arity in arities:
                        return
            if declared:
                self.error(node, f"no overload of `{method}` takes {arity} argument(s) "
                                 f"in {self.current_type.fqn}")
            return
        type_name, kind = self.receiver_type(object_node, scope)
        if kind == "super":
            return
        if type_name is None:
            return
        fqn = self.unit.resolve(type_name) or resolve_global(type_name)
        if fqn is None or fqn.startswith(EXTERNAL_PREFIXES) or fqn not in CLASSES:
            return
        if not fqn.startswith(TRUSTED_PREFIXES):
            return
        if method in UNIVERSAL_METHODS:
            return
        exists, arity_ok = has_method(fqn, method, arity)
        if not exists:
            self.error(node, f"unknown method `{method}` on {fqn}")
        elif not arity_ok:
            self.error(node, f"no overload of `{method}` on {fqn} takes {arity} argument(s)")

    def check_creation(self, node, scope):
        type_node = node.child_by_field_name("type")
        args = node.child_by_field_name("arguments")
        if type_node is None:
            return
        type_name = clean_type(self.text(type_node))
        fqn = self.unit.resolve(type_name) or resolve_global(type_name)
        if fqn is None or fqn not in CLASSES:
            return
        arity = count_args(args)
        ok = has_ctor(fqn, arity)
        if ok is False:
            self.error(node, f"no constructor of {fqn} takes {arity} argument(s)")

    def infer_expression_type(self, expr, scope):
        """Best effort static type of an expression, or None when unsure."""
        if expr.type == "object_creation_expression":
            type_node = expr.child_by_field_name("type")
            return clean_type(self.text(type_node)) if type_node is not None else None
        if expr.type == "cast_expression":
            type_node = expr.child_by_field_name("type")
            return clean_type(self.text(type_node)) if type_node is not None else None
        if expr.type in ("method_invocation", "field_access", "identifier"):
            if expr.type == "identifier":
                return scope.get(self.text(expr))
            return self.receiver_type(expr, scope)[0]
        return None

    def check_assignment_compatibility(self, node, scope):
        if node.type != "local_variable_declaration":
            return
        type_node = node.child_by_field_name("type")
        declared = clean_type(self.text(type_node)) if type_node is not None else None
        if declared is None or declared in ("var",):
            return
        for declarator in node.children:
            if declarator.type != "variable_declarator":
                continue
            value = declarator.child_by_field_name("value")
            if value is None:
                continue
            inferred = self.infer_expression_type(value, scope)
            if not inferred:
                continue
            declared_fqn = self.unit.resolve(declared) or declared
            inferred_fqn = self.unit.resolve(inferred) or inferred
            if declared_fqn not in CLASSES or inferred_fqn not in CLASSES:
                continue
            if declared_fqn == inferred_fqn:
                continue
            if declared_fqn in ordered_supertypes(inferred_fqn):
                continue
            if inferred_fqn in ordered_supertypes(declared_fqn):
                continue   # downcast: fine without a cast in some cases? report instead
            self.error(declarator, f"cannot assign {inferred_fqn} to {declared_fqn}")

    def check_type_visibility(self, node, scope):
        """`Foo.bar` / `new Foo(...)` / `Foo.method(...)` where Foo is a type that exists
        somewhere in the index but is not reachable from this file (missing import)."""
        if node.type == "identifier":
            name = self.text(node)
            if name in scope or name in KEYWORDS:
                return
            if not name[:1].isupper():
                return
            if name in self.unit.types or name in self.unit.imports:
                return
            if self.unit.resolve(name) is not None:
                return
            # Only flag top level matches: a nested class (Outer$Inner) or a java.lang class
            # with the same simple name is a coincidence, not a missing import.
            candidates = [candidate for candidate in SIMPLE_INDEX[name] if "$" not in candidate]
            if not candidates or name in JAVA_LANG_TYPES:
                return
            self.error(node, f"type `{name}` is not visible here (missing import?). "
                             f"Known types with that name: {', '.join(sorted(candidates)[:3])}")

    def check_field_access(self, node, scope):
        object_node = node.child_by_field_name("object")
        field_node = node.child_by_field_name("field")
        if object_node is None or field_node is None:
            return
        if object_node.type != "identifier":
            return
        name = self.text(object_node)
        if name in scope:
            return
        fqn = self.unit.resolve(name)
        if fqn is None or fqn not in CLASSES:
            self.check_type_visibility(object_node, scope)
            return
        field = self.text(field_node)
        info = CLASSES[fqn]
        if has_field(fqn, field):
            return
        # enum constants / methods referenced without parens (method reference) are fine
        if field in info.methods:
            return
        self.error(node, f"unknown field/constant `{field}` on {fqn}")


def count_args(args_node):
    if args_node is None:
        return 0
    count = 0
    for child in args_node.children:
        if child.type in ("(", ")", ","):
            continue
        count += 1
    return count


def check_types_exist(unit):
    """Every type referenced in imports/declarations must resolve."""
    errors = []
    data = unit.source.encode("utf-8", "replace")
    for simple, fqn in unit.imports.items():
        if fqn.startswith(EXTERNAL_PREFIXES):
            continue
        if fqn not in CLASSES and resolve_global(fqn) is None:
            errors.append(f"{unit.path}: import {fqn} does not exist")
    return errors


def main():
    global PARSER
    language = Language(tree_sitter_java.language())
    PARSER = Parser(language)

    print("indexing vanilla sources ...")
    vanilla = index_tree(VANILLA_SRC, "vanilla")
    print(f"  {vanilla} files")
    print("indexing forge sources ...")
    forge = index_tree(FORGE_SRC, "forge")
    print(f"  {forge} files")
    print("indexing mod sources ...")
    mod_files = []
    for dirpath, _dirnames, filenames in os.walk(MOD_SRC):
        for filename in filenames:
            if filename.endswith(".java"):
                mod_files.append(os.path.join(dirpath, filename))
    units = []
    for path in sorted(mod_files):
        unit = parse_file(path, "mod")
        if unit is not None:
            units.append(unit)
    print(f"  {len(units)} files, {len(CLASSES)} types indexed")

    all_errors = []
    all_notes = []
    for unit in units:
        checker = Checker(unit, unit.source.encode("utf-8", "replace"))
        checker.check()
        all_errors.extend(checker.errors)
        all_notes.extend(checker.notes)
        all_errors.extend(check_types_exist(unit))

    print()
    if all_errors:
        print(f"=== {len(all_errors)} PROBLEM(S) ===")
        for item in all_errors:
            print(" -", item)
    else:
        print("=== no problems found ===")
    if all_notes:
        print(f"({len(all_notes)} informational notes)")
    return 1 if all_errors else 0


if __name__ == "__main__":
    sys.exit(main())
