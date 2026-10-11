#!/usr/bin/env python3
"""
Generates assets/adventcalendar/lang/en_us.json and es_es.json.

The placeholder count of every entry is validated against the arguments the code actually
passes (see the scan below), so a mismatch shows up here instead of at runtime.
"""
import json
import os
import re
import collections
import pathlib

RES = "/home/user/adventcalendar/src/main/resources/assets/adventcalendar/lang"

EN = {
    # ------------------------------------------------------------------ items
    "item.adventcalendar.control_wand": "NPC Control Wand",
    "item.adventcalendar.control_wand.tooltip1": "Right click an Advent NPC to select it",
    "item.adventcalendar.control_wand.tooltip2": "Sneak + right click to configure it",
    "item.adventcalendar.advent_calendar": "Advent Calendar",
    "item.adventcalendar.advent_calendar.tooltip1": "Right click to open the calendar",
    "item.adventcalendar.advent_calendar.tooltip2": "One gift every day of December",

    # ------------------------------------------------------------------ player GUI
    "adventcalendar.gui.title": "Advent Calendar",
    "adventcalendar.gui.subtitle.closed": "The calendar opens in December",
    "adventcalendar.gui.realdate": "Server date: %s",
    "adventcalendar.gui.overridden": "Debug date: %s",
    "adventcalendar.gui.stocking": "%s gift(s) waiting in your stocking",
    "adventcalendar.gui.button.close": "Close",
    "adventcalendar.gui.button.admin": "Admin",
    "adventcalendar.gui.button.debug": "Debug",
    "adventcalendar.gui.button.retrieve": "Collect %s gift(s)",
    "adventcalendar.gui.day.locked": "Day %s - locked until December %s",
    "adventcalendar.gui.day.available": "Day %s - click to claim your gift!",
    "adventcalendar.gui.day.claimed": "Day %s - already claimed",
    "adventcalendar.gui.day.empty": "Day %s - no reward configured yet",
    "adventcalendar.gui.day.today": "Today's gift",

    # ------------------------------------------------------------------ admin GUI
    "adventcalendar.admin.title": "Advent Calendar - Admin",
    "adventcalendar.admin.selected": "Editing day %s",
    "adventcalendar.admin.configured": "Configured doors: %s / %s",
    "adventcalendar.admin.label": "Label (optional)",
    "adventcalendar.admin.command": "Command reward (optional)",
    "adventcalendar.admin.command.hint": "Run as the claiming player, e.g. give @p diamond 3",
    "adventcalendar.admin.hidden": "Hidden until unlocked: %s",
    "adventcalendar.admin.button.save": "Save to disk",
    "adventcalendar.admin.button.reload": "Reload file",
    "adventcalendar.admin.button.sample": "Sample rewards",
    "adventcalendar.admin.button.clearall": "Clear all",
    "adventcalendar.admin.button.spawn": "Spawn NPC",
    "adventcalendar.admin.hint.drag": "Drag an item onto a door to assign it",
    "adventcalendar.admin.hint.clear": "Right click a door to clear it",
    "adventcalendar.admin.hint.grab": "Middle click a door to copy its reward",
    "adventcalendar.admin.day.set": "Day %s updated",
    "adventcalendar.admin.day.cleared": "Door %s cleared",

    # ------------------------------------------------------------------ debug GUI
    "adventcalendar.debug.title": "Advent Calendar - Debug",
    "adventcalendar.debug.date": "Real server date: %s",
    "adventcalendar.debug.override": "Date override: %s",
    "adventcalendar.debug.override.none": "No date override",
    "adventcalendar.debug.calendar": "Calendar year %s - unlocked through day %s",
    "adventcalendar.debug.input": "YYYY-MM-DD",
    "adventcalendar.debug.apply": "Apply",
    "adventcalendar.debug.hint": "Left click a door: force claim. Right click: unlock up to it.",
    "adventcalendar.debug.button.prev": "-1 day",
    "adventcalendar.debug.button.dec1": "1st of Dec",
    "adventcalendar.debug.button.clear": "Clear date",
    "adventcalendar.debug.preview": "Preview rewards: %s",
    "adventcalendar.debug.simulate": "Simulate claims: %s",
    "adventcalendar.debug.button.claim": "Force claim",
    "adventcalendar.debug.button.grant": "Grant all",
    "adventcalendar.debug.button.reset": "Reset mine",
    "adventcalendar.debug.button.resetall": "Reset all",

    # ------------------------------------------------------------------ NPC GUI
    "adventcalendar.npc.title": "Advent NPC configuration",
    "adventcalendar.npc.name": "Name",
    "adventcalendar.npc.color": "Name colour",
    "adventcalendar.npc.skin": "Skin (https://url.png, username or local:file.png)",
    "adventcalendar.npc.model": "Model: %s",
    "adventcalendar.npc.glow": "Glowing: %s",
    "adventcalendar.npc.particles": "Particles: %s",
    "adventcalendar.npc.invulnerable": "Invulnerable: %s",
    "adventcalendar.npc.look": "Looks at players: %s",
    "adventcalendar.npc.ambient": "Ambient sound: %s",
    "adventcalendar.npc.preview": "Reload skin",
    "adventcalendar.npc.save": "Save",
    "adventcalendar.npc.cancel": "Cancel",
    "adventcalendar.npc.skin.loading": "Downloading skin...",
    "adventcalendar.npc.skin.processing": "Registering skin...",
    "adventcalendar.npc.skin.ready": "Skin ready",
    "adventcalendar.npc.skin.failed": "Could not download that skin",
    "adventcalendar.npc.skin.offline": "Remote skins are disabled in the config",

    # ------------------------------------------------------------------ chat / feedback
    "adventcalendar.msg.claimed": "Day %s: you received %s!",
    "adventcalendar.msg.claimed_multiple": "Day %s: you received %s x%s!",
    "adventcalendar.msg.stocking": "%s was placed in your stocking",
    "adventcalendar.msg.pending_given": "Moved %s gift(s) from your stocking into your inventory",
    "adventcalendar.msg.pending_none": "Your stocking is empty",
    "adventcalendar.msg.already_claimed": "You already opened that door",
    "adventcalendar.msg.locked": "That door is still locked",
    "adventcalendar.msg.locked_december": "The calendar only opens in December",
    "adventcalendar.msg.no_reward": "That door has no reward configured",
    "adventcalendar.msg.invalid_day": "That day does not exist in this calendar",
    "adventcalendar.msg.op_required": "You need operator permissions for that",
    "adventcalendar.msg.unlocked": "Door %s is open! Come and get your gift",
    "adventcalendar.msg.announce": "%s opened door %s!",
    "adventcalendar.msg.current_date": "Server date %s (calendar year %s, unlocked through day %s)",
    "adventcalendar.msg.date_override": "Debug date set to %s",
    "adventcalendar.msg.date_override_cleared": "Debug date override cleared",
    "adventcalendar.msg.preview_on": "Reward preview enabled",
    "adventcalendar.msg.preview_off": "Reward preview disabled",
    "adventcalendar.msg.reloaded": "Advent Calendar data reloaded",
    "adventcalendar.msg.saved": "Advent Calendar data saved",
    "adventcalendar.msg.reset": "Removed %s claim(s)",
    "adventcalendar.msg.granted_all": "Every configured reward was granted",
    "adventcalendar.msg.reward_set": "Day %s reward set to %s",
    "adventcalendar.msg.reward_cleared": "Day %s cleared",
    "adventcalendar.msg.grabbed": "Copied %s from that door",
    "adventcalendar.msg.npc_spawned": "Advent NPC spawned",
    "adventcalendar.msg.npc_missing": "No Advent NPC nearby",
    "adventcalendar.msg.skin_set": "NPC skin set to %s",
    "adventcalendar.msg.wand_selected": "Advent NPC selected",
    "adventcalendar.msg.wand_moved": "Advent NPC moved here",
    "adventcalendar.msg.wand_no_selection": "Select an Advent NPC with a right click first",
    "adventcalendar.msg.claimed_days": "%s claimed: %s",
    "adventcalendar.msg.claimed_days_none": "%s has not claimed any door yet",

    # ------------------------------------------------------------------ subtitles
    "subtitles.adventcalendar.lock": "Door is locked",
    "subtitles.adventcalendar.unlock": "A door opened",
    "subtitles.adventcalendar.claim": "Gift received",
    "subtitles.adventcalendar.notify": "Advent Calendar notification",
}

ES = {
    "item.adventcalendar.control_wand": "Varita de control del NPC",
    "item.adventcalendar.control_wand.tooltip1": "Clic derecho sobre un NPC navideno para seleccionarlo",
    "item.adventcalendar.control_wand.tooltip2": "Agachado + clic derecho para configurarlo",
    "item.adventcalendar.advent_calendar": "Calendario de Adviento",
    "item.adventcalendar.advent_calendar.tooltip1": "Clic derecho para abrir el calendario",
    "item.adventcalendar.advent_calendar.tooltip2": "Un regalo cada dia de diciembre",

    "adventcalendar.gui.title": "Calendario de Adviento",
    "adventcalendar.gui.subtitle.closed": "El calendario se abre en diciembre",
    "adventcalendar.gui.realdate": "Fecha del servidor: %s",
    "adventcalendar.gui.overridden": "Fecha de depuracion: %s",
    "adventcalendar.gui.stocking": "%s regalo(s) esperando en tu calcetin",
    "adventcalendar.gui.button.close": "Cerrar",
    "adventcalendar.gui.button.admin": "Admin",
    "adventcalendar.gui.button.debug": "Debug",
    "adventcalendar.gui.button.retrieve": "Recoger %s regalo(s)",
    "adventcalendar.gui.day.locked": "Dia %s - bloqueado hasta el %s de diciembre",
    "adventcalendar.gui.day.available": "Dia %s - clic para reclamar tu regalo!",
    "adventcalendar.gui.day.claimed": "Dia %s - ya reclamado",
    "adventcalendar.gui.day.empty": "Dia %s - sin recompensa configurada",
    "adventcalendar.gui.day.today": "El regalo de hoy",

    "adventcalendar.admin.title": "Calendario de Adviento - Admin",
    "adventcalendar.admin.selected": "Editando el dia %s",
    "adventcalendar.admin.configured": "Puertas configuradas: %s / %s",
    "adventcalendar.admin.label": "Etiqueta (opcional)",
    "adventcalendar.admin.command": "Recompensa por comando (opcional)",
    "adventcalendar.admin.command.hint": "Se ejecuta como el jugador, ej: give @p diamond 3",
    "adventcalendar.admin.hidden": "Oculto hasta desbloquearse: %s",
    "adventcalendar.admin.button.save": "Guardar en disco",
    "adventcalendar.admin.button.reload": "Recargar archivo",
    "adventcalendar.admin.button.sample": "Recompensas de ejemplo",
    "adventcalendar.admin.button.clearall": "Borrar todo",
    "adventcalendar.admin.button.spawn": "Invocar NPC",
    "adventcalendar.admin.hint.drag": "Arrastra un objeto a una puerta para asignarlo",
    "adventcalendar.admin.hint.clear": "Clic derecho en una puerta para borrarla",
    "adventcalendar.admin.hint.grab": "Clic central en una puerta para copiar su recompensa",
    "adventcalendar.admin.day.set": "Dia %s actualizado",
    "adventcalendar.admin.day.cleared": "Puerta %s borrada",

    "adventcalendar.debug.title": "Calendario de Adviento - Debug",
    "adventcalendar.debug.date": "Fecha real del servidor: %s",
    "adventcalendar.debug.override": "Fecha forzada: %s",
    "adventcalendar.debug.override.none": "Sin fecha forzada",
    "adventcalendar.debug.calendar": "Ano %s - desbloqueado hasta el dia %s",
    "adventcalendar.debug.input": "AAAA-MM-DD",
    "adventcalendar.debug.apply": "Aplicar",
    "adventcalendar.debug.hint": "Clic izquierdo: forzar reclamo. Clic derecho: desbloquear hasta ahi.",
    "adventcalendar.debug.button.prev": "-1 dia",
    "adventcalendar.debug.button.dec1": "1 de dic",
    "adventcalendar.debug.button.clear": "Quitar fecha",
    "adventcalendar.debug.preview": "Previsualizar: %s",
    "adventcalendar.debug.simulate": "Simular reclamos: %s",
    "adventcalendar.debug.button.claim": "Forzar reclamo",
    "adventcalendar.debug.button.grant": "Dar todo",
    "adventcalendar.debug.button.reset": "Reiniciar mio",
    "adventcalendar.debug.button.resetall": "Reiniciar todo",

    "adventcalendar.npc.title": "Configuracion del NPC",
    "adventcalendar.npc.name": "Nombre",
    "adventcalendar.npc.color": "Color del nombre",
    "adventcalendar.npc.skin": "Skin (https://url.png, usuario o local:archivo.png)",
    "adventcalendar.npc.model": "Modelo: %s",
    "adventcalendar.npc.glow": "Brillo: %s",
    "adventcalendar.npc.particles": "Particulas: %s",
    "adventcalendar.npc.invulnerable": "Invulnerable: %s",
    "adventcalendar.npc.look": "Mira a los jugadores: %s",
    "adventcalendar.npc.ambient": "Sonido ambiente: %s",
    "adventcalendar.npc.preview": "Recargar skin",
    "adventcalendar.npc.save": "Guardar",
    "adventcalendar.npc.cancel": "Cancelar",
    "adventcalendar.npc.skin.loading": "Descargando skin...",
    "adventcalendar.npc.skin.processing": "Registrando skin...",
    "adventcalendar.npc.skin.ready": "Skin lista",
    "adventcalendar.npc.skin.failed": "No se pudo descargar la skin",
    "adventcalendar.npc.skin.offline": "Las skins remotas estan desactivadas en la config",

    "adventcalendar.msg.claimed": "Dia %s: recibiste %s!",
    "adventcalendar.msg.claimed_multiple": "Dia %s: recibiste %s x%s!",
    "adventcalendar.msg.stocking": "%s se guardo en tu calcetin",
    "adventcalendar.msg.pending_given": "Se movieron %s regalo(s) del calcetin al inventario",
    "adventcalendar.msg.pending_none": "Tu calcetin esta vacio",
    "adventcalendar.msg.already_claimed": "Ya abriste esa puerta",
    "adventcalendar.msg.locked": "Esa puerta sigue bloqueada",
    "adventcalendar.msg.locked_december": "El calendario solo se abre en diciembre",
    "adventcalendar.msg.no_reward": "Esa puerta no tiene recompensa",
    "adventcalendar.msg.invalid_day": "Ese dia no existe en este calendario",
    "adventcalendar.msg.op_required": "Necesitas permisos de operador",
    "adventcalendar.msg.unlocked": "La puerta %s esta abierta! Ven por tu regalo",
    "adventcalendar.msg.announce": "%s abrio la puerta %s!",
    "adventcalendar.msg.current_date": "Fecha del servidor %s (ano %s, desbloqueado hasta el dia %s)",
    "adventcalendar.msg.date_override": "Fecha de depuracion: %s",
    "adventcalendar.msg.date_override_cleared": "Fecha de depuracion borrada",
    "adventcalendar.msg.preview_on": "Previsualizacion activada",
    "adventcalendar.msg.preview_off": "Previsualizacion desactivada",
    "adventcalendar.msg.reloaded": "Datos del calendario recargados",
    "adventcalendar.msg.saved": "Datos del calendario guardados",
    "adventcalendar.msg.reset": "Se borraron %s reclamo(s)",
    "adventcalendar.msg.granted_all": "Se otorgaron todas las recompensas configuradas",
    "adventcalendar.msg.reward_set": "Recompensa del dia %s: %s",
    "adventcalendar.msg.reward_cleared": "Dia %s borrado",
    "adventcalendar.msg.grabbed": "Se copio %s de esa puerta",
    "adventcalendar.msg.npc_spawned": "NPC del calendario invocado",
    "adventcalendar.msg.npc_missing": "No hay ningun NPC cerca",
    "adventcalendar.msg.skin_set": "Skin del NPC: %s",
    "adventcalendar.msg.wand_selected": "NPC seleccionado",
    "adventcalendar.msg.wand_moved": "NPC movido aqui",
    "adventcalendar.msg.wand_no_selection": "Selecciona un NPC con clic derecho primero",
    "adventcalendar.msg.claimed_days": "%s reclamado: %s",
    "adventcalendar.msg.claimed_days_none": "%s todavia no reclamado ninguna puerta",

    "subtitles.adventcalendar.lock": "Puerta bloqueada",
    "subtitles.adventcalendar.unlock": "Una puerta se abrio",
    "subtitles.adventcalendar.claim": "Regalo recibido",
    "subtitles.adventcalendar.notify": "Aviso del calendario",
}


def scan_arg_counts():
    """key -> number of arguments the code passes (must match the placeholders)."""
    counts = collections.defaultdict(set)
    for path in pathlib.Path("/home/user/adventcalendar/src/main/java").rglob("*.java"):
        src = path.read_text()
        for match in re.finditer(r'\b(?:translatable|text|msg|onOff)\(\s*"([^"]+)"', src):
            key = match.group(1)
            index = match.end()
            depth = 1
            while index < len(src) and depth > 0:
                char = src[index]
                if char in "([{":
                    depth += 1
                elif char in ")]}":
                    depth -= 1
                elif char == '"':
                    index += 1
                    while index < len(src) and src[index] != '"':
                        index += 2 if src[index] == "\\" else 1
                index += 1
            args = src[match.end():index - 1].strip()
            number = 0
            if args:
                depth = 0
                current = ""
                parts = []
                for char in args:
                    if char in "([{":
                        depth += 1
                    elif char in ")]}":
                        depth -= 1
                    if char == "," and depth == 0:
                        parts.append(current)
                        current = ""
                    else:
                        current += char
                parts.append(current)
                number = len([part for part in parts if part.strip()])
            counts[key].add(number)
        for match in re.finditer(r'return "(adventcalendar\.[a-z0-9_.]+)"', src):
            counts[match.group(1)].add(0)
    return counts


def validate(table, counts, language):
    problems = []
    for key, text in table.items():
        expected = max(counts.get(key, {0})) if counts.get(key) else 0
        provided = text.count("%s")
        if provided != expected:
            problems.append(f"{language}: {key} has {provided} placeholder(s), code passes {expected}")
    for key, values in counts.items():
        if key.startswith("adventcalendar") and key not in table:
            problems.append(f"{language}: {key} is used by the code but missing from the lang file")
    return problems


def main():
    counts = scan_arg_counts()
    problems = validate(EN, counts, "en_us") + validate(ES, counts, "es_es")
    if problems:
        print("PLACEHOLDER PROBLEMS:")
        for problem in problems:
            print(" -", problem)
        raise SystemExit(1)
    os.makedirs(RES, exist_ok=True)
    with open(f"{RES}/en_us.json", "w", encoding="utf-8") as handle:
        json.dump(EN, handle, indent=2, ensure_ascii=False, sort_keys=True)
        handle.write("\n")
    with open(f"{RES}/es_es.json", "w", encoding="utf-8") as handle:
        json.dump(ES, handle, indent=2, ensure_ascii=False, sort_keys=True)
        handle.write("\n")
    print(f"wrote {len(EN)} English and {len(ES)} Spanish entries")


if __name__ == "__main__":
    main()
