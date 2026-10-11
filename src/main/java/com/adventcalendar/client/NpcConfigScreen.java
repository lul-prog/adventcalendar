package com.adventcalendar.client;

import com.adventcalendar.entity.AdventNpcEntity;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.network.NpcConfigPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Interfaz de configuración del NPC (mando de control, shift + clic derecho).
 * Nombre, color del nombre, brillo, partículas ambientales, inmortal y skin.
 * Los cambios se envían al servidor con "Guardar".
 */
public class NpcConfigScreen extends Screen {
    private static final ChatFormatting[] COLORS = {
            ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN,
            ChatFormatting.DARK_AQUA, ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.DARK_GRAY,
            ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
            ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW,
            ChatFormatting.WHITE
    };

    private final int entityId;
    private final String initialName;
    private final String initialSkin;
    private ChatFormatting color;
    private boolean glow;
    private boolean particles;
    private boolean invulnerable;

    private EditBox nameBox;
    private EditBox skinBox;
    private Button glowBtn;
    private Button particlesBtn;
    private Button invulnBtn;
    private final List<ColorButton> colorButtons = new ArrayList<>();

    public NpcConfigScreen(AdventNpcEntity npc) {
        super(Component.literal("Configurar NPC"));
        this.entityId = npc.getId();
        Component cn = npc.getCustomName();
        this.initialName = cn == null ? "" : cn.getString();
        this.initialSkin = npc.getSkin();
        this.color = parseColor(npc.getNameColor());
        this.glow = npc.getGlow();
        this.particles = npc.getAmbientParticles();
        this.invulnerable = npc.getInvulnerableSync();
    }

    private static ChatFormatting parseColor(String name) {
        if (name == null || name.isEmpty()) {
            return ChatFormatting.WHITE;
        }
        try {
            ChatFormatting f = ChatFormatting.valueOf(name);
            return f.isColor() ? f : ChatFormatting.WHITE;
        } catch (IllegalArgumentException e) {
            return ChatFormatting.WHITE;
        }
    }

    private static Component toggleText(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "Sí" : "No"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // Nombre
        this.nameBox = new EditBox(this.font, cx - 100, 40, 200, 20, Component.literal("Nombre"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(initialName);
        this.addRenderableWidget(this.nameBox);

        // Color del nombre: 2 filas de 8
        colorButtons.clear();
        for (int i = 0; i < COLORS.length; i++) {
            int bx = cx - 100 + (i % 8) * 25;
            int by = 76 + (i / 8) * 22;
            ColorButton b = new ColorButton(bx, by, COLORS[i]);
            colorButtons.add(b);
            this.addRenderableWidget(b);
        }

        // Toggles
        this.glowBtn = this.addRenderableWidget(Button.builder(toggleText("Brillo", glow), b -> {
            glow = !glow;
            b.setMessage(toggleText("Brillo", glow));
        }).bounds(cx - 150, 130, 96, 20).build());
        this.particlesBtn = this.addRenderableWidget(Button.builder(toggleText("Partículas", particles), b -> {
            particles = !particles;
            b.setMessage(toggleText("Partículas", particles));
        }).bounds(cx - 48, 130, 96, 20).build());
        this.invulnBtn = this.addRenderableWidget(Button.builder(toggleText("Inmortal", invulnerable), b -> {
            invulnerable = !invulnerable;
            b.setMessage(toggleText("Inmortal", invulnerable));
        }).bounds(cx + 54, 130, 96, 20).build());

        // Skin
        this.skinBox = new EditBox(this.font, cx - 100, 166, 200, 20,
                Component.literal("Skin (URL o usuario)"));
        this.skinBox.setMaxLength(256);
        this.skinBox.setValue(initialSkin);
        this.addRenderableWidget(this.skinBox);

        // Guardar / Cancelar
        this.addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> saveAndClose())
                .bounds(cx - 104, 198, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> this.onClose())
                .bounds(cx + 4, 198, 100, 20).build());
    }

    private void saveAndClose() {
        ModNetwork.CHANNEL.sendToServer(new NpcConfigPacket(
                entityId,
                nameBox.getValue().trim(),
                color.name(),
                glow, particles, invulnerable,
                skinBox.getValue().trim()));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 18, 0xFFFFFF);
        g.drawString(this.font, "Nombre:", cx - 100, 30, 0xA0A0A0);
        g.drawString(this.font, "Color del nombre:", cx - 100, 66, 0xA0A0A0);
        g.drawString(this.font, "Skin (URL o usuario):", cx - 100, 156, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Botón de color: muestra el color y un borde blanco si está seleccionado. */
    private class ColorButton extends Button {
        private final ChatFormatting cf;

        ColorButton(int x, int y, ChatFormatting cf) {
            super(x, y, 20, 20, Component.empty(), b -> color = cf, Button.DEFAULT_NARRATION);
            this.cf = cf;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int rgb = cf.getColor() == null ? 0xFFFFFF : cf.getColor();
            int x = this.getX();
            int y = this.getY();
            g.fill(x, y, x + this.width, y + this.height, 0xFF000000 | rgb);
            int border = (color == cf) ? 0xFFFFFFFF : 0xFF707070;
            g.renderOutline(x, y, this.width, this.height, border);
        }
    }
}
