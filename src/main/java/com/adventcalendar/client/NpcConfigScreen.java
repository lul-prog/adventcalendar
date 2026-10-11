package com.adventcalendar.client;

import com.adventcalendar.entity.AdventNpcEntity;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.network.NpcConfigPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration GUI of one Advent NPC (opened with the control wand, the debug GUI or
 * {@code /advent npc}). Everything - name, colour, skin (URL, username or
 * {@code local:file.png}), skin model, glow, particles, invulnerability, looking at players
 * and the ambient sound - is sent to the server on save.
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
    private ChatFormatting color;
    private boolean glow;
    private boolean particles;
    private boolean invulnerable;
    private boolean lookAtPlayers;
    private boolean ambientSound;
    private AdventNpcEntity.SkinModel skinModel;

    private EditBox nameBox;
    private EditBox skinBox;
    private Button modelButton;
    private Button glowButton;
    private Button particlesButton;
    private Button invulnerableButton;
    private Button lookButton;
    private Button ambientButton;
    private final List<ColorButton> colorButtons = new ArrayList<>();

    private int panelLeft;
    private int panelTop;

    public NpcConfigScreen(AdventNpcEntity npc) {
        super(Component.translatable("adventcalendar.npc.title"));
        this.entityId = npc.getId();
        Component customName = npc.getCustomName();
        this.initialName = customName == null ? "" : customName.getString();
        this.color = AdventNpcEntity.parseColor(npc.getNameColor());
        this.glow = npc.getGlow();
        this.particles = npc.getAmbientParticles();
        this.invulnerable = npc.getInvulnerableSync();
        this.lookAtPlayers = npc.getLookAtPlayers();
        this.ambientSound = npc.isAmbientSoundEnabled();
        this.skinModel = npc.getSkinModel();
    }

    private Component onOff(String key, boolean value) {
        return Component.translatable(key, value ? "ON" : "OFF");
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        this.panelLeft = centerX - 130;
        this.panelTop = Math.max(2, this.height / 2 - 112);

        this.nameBox = new EditBox(this.font, centerX - 95, panelTop + 32, 190, 18,
                Component.translatable("adventcalendar.npc.name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(initialName);
        this.addRenderableWidget(this.nameBox);

        colorButtons.clear();
        for (int i = 0; i < COLORS.length; i++) {
            ColorButton button = new ColorButton(centerX - 112 + i * 14, panelTop + 66, COLORS[i]);
            colorButtons.add(button);
            this.addRenderableWidget(button);
        }

        this.modelButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("adventcalendar.npc.model", skinModel.name()), button -> {
                            skinModel = skinModel.next();
                            button.setMessage(Component.translatable("adventcalendar.npc.model", skinModel.name()));
                        })
                .bounds(centerX - 124, panelTop + 86, 122, 18).build());

        this.glowButton = this.addRenderableWidget(Button.builder(onOff("adventcalendar.npc.glow", glow), button -> {
            glow = !glow;
            button.setMessage(onOff("adventcalendar.npc.glow", glow));
        }).bounds(centerX + 2, panelTop + 86, 122, 18).build());

        this.particlesButton = this.addRenderableWidget(
                Button.builder(onOff("adventcalendar.npc.particles", particles), button -> {
                    particles = !particles;
                    button.setMessage(onOff("adventcalendar.npc.particles", particles));
                }).bounds(centerX - 124, panelTop + 106, 122, 18).build());

        this.invulnerableButton = this.addRenderableWidget(
                Button.builder(onOff("adventcalendar.npc.invulnerable", invulnerable), button -> {
                    invulnerable = !invulnerable;
                    button.setMessage(onOff("adventcalendar.npc.invulnerable", invulnerable));
                }).bounds(centerX + 2, panelTop + 106, 122, 18).build());

        this.lookButton = this.addRenderableWidget(Button.builder(onOff("adventcalendar.npc.look", lookAtPlayers),
                button -> {
                    lookAtPlayers = !lookAtPlayers;
                    button.setMessage(onOff("adventcalendar.npc.look", lookAtPlayers));
                }).bounds(centerX - 124, panelTop + 126, 122, 18).build());

        this.ambientButton = this.addRenderableWidget(
                Button.builder(onOff("adventcalendar.npc.ambient", ambientSound), button -> {
                    ambientSound = !ambientSound;
                    button.setMessage(onOff("adventcalendar.npc.ambient", ambientSound));
                }).bounds(centerX + 2, panelTop + 126, 122, 18).build());

        this.skinBox = new EditBox(this.font, centerX - 95, panelTop + 158, 190, 18,
                Component.translatable("adventcalendar.npc.skin"));
        this.skinBox.setMaxLength(256);
        this.addRenderableWidget(this.skinBox);

        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.npc.preview"),
                        button -> SkinManager.invalidate(skinBox.getValue().trim()))
                .bounds(centerX - 124, panelTop + 180, 122, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.npc.save"),
                        button -> saveAndClose())
                .bounds(centerX - 124, panelTop + 204, 122, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.npc.cancel"),
                        button -> this.onClose())
                .bounds(centerX + 2, panelTop + 204, 122, 20).build());
    }

    private void saveAndClose() {
        ModNetwork.CHANNEL.sendToServer(new NpcConfigPacket(
                entityId,
                nameBox.getValue().trim(),
                color.name(),
                glow, particles, invulnerable,
                skinBox.getValue().trim(),
                skinModel.name(),
                lookAtPlayers, ambientSound));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;

        GuiTextures.drawPanel(graphics, panelLeft, panelTop, 260, 230);
        graphics.drawCenteredString(this.font, this.title, centerX - 20, panelTop + 10, GuiTextures.GOLD);
        graphics.drawString(this.font, Component.translatable("adventcalendar.npc.name"),
                centerX - 95, panelTop + 22, GuiTextures.TEXT_DIM, false);
        graphics.drawString(this.font, Component.translatable("adventcalendar.npc.color"),
                centerX - 112, panelTop + 56, GuiTextures.TEXT_DIM, false);
        graphics.drawString(this.font, Component.translatable("adventcalendar.npc.skin"),
                centerX - 95, panelTop + 148, GuiTextures.TEXT_DIM, false);
        graphics.drawString(this.font, skinStateText(), centerX - 124, panelTop + 182,
                stateColour(), false);

        drawSkinPreview(graphics, centerX + 108, panelTop + 12);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component skinStateText() {
        String skin = skinBox == null ? "" : skinBox.getValue().trim();
        return switch (SkinManager.stateOf(skin)) {
            case EMPTY -> Component.empty();
            case DISABLED -> Component.translatable("adventcalendar.npc.skin.offline");
            case FAILED -> Component.translatable("adventcalendar.npc.skin.failed");
            case READY -> Component.translatable("adventcalendar.npc.skin.ready");
            case LOADING -> Component.translatable("adventcalendar.npc.skin.processing");
            default -> Component.translatable("adventcalendar.npc.skin.loading");
        };
    }

    private int stateColour() {
        return switch (SkinManager.stateOf(skinBox == null ? "" : skinBox.getValue().trim())) {
            case READY -> GuiTextures.GREEN;
            case FAILED, DISABLED -> GuiTextures.RED;
            default -> GuiTextures.TEXT_DIM;
        };
    }

    /** 4x scaled face (and hat layer) of the configured skin. */
    private void drawSkinPreview(GuiGraphics graphics, int x, int y) {
        ResourceLocation texture = SkinManager.textureFor(skinBox == null ? "" : skinBox.getValue().trim());
        GuiTextures.drawSlot(graphics, x - 2, y - 2, 36);
        if (texture == null) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(4.0F, 4.0F, 1.0F);
        graphics.blit(texture, 0, 0, 8, 8, 8, 8);
        graphics.blit(texture, 0, 0, 40, 8, 8, 8);
        pose.popPose();
    }


    /** Keeps the layout in sync when the window is resized (the vanilla hook for that). */
    @Override
    protected void repositionElements() {
        this.clearWidgets();
        this.init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Colour swatch; a white outline marks the active one. */
    private class ColorButton extends Button {
        private final ChatFormatting formatting;

        ColorButton(int x, int y, ChatFormatting formatting) {
            super(x, y, 12, 12, Component.empty(), button -> color = formatting, Button.DEFAULT_NARRATION);
            this.formatting = formatting;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Integer rgb = formatting.getColor();
            int value = rgb == null ? 0xFFFFFF : rgb;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height,
                    0xFF000000 | value);
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height,
                    color == formatting ? 0xFFFFFFFF : 0xFF404850);
        }
    }
}
