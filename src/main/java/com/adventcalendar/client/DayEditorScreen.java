package com.adventcalendar.client;

import com.adventcalendar.network.AdminEditPacket;
import com.adventcalendar.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Small modal editor opened from the admin GUI: fine tunes one day (custom label, reward
 * command and the "hidden until unlocked" flag) on top of the item assigned by drag & drop.
 */
public class DayEditorScreen extends Screen {
    private final Screen parent;
    private final int day;
    private final ItemStack stack;

    private EditBox labelBox;
    private EditBox commandBox;
    private boolean hidden;
    private Button hiddenButton;

    public DayEditorScreen(Screen parent, int day) {
        super(Component.translatable("adventcalendar.admin.selected", day));
        this.parent = parent;
        this.day = day;
        ClientCalendarState state = ClientCalendarState.get();
        this.stack = state.stackFor(day).copy();
        this.hidden = state.isHidden(day);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 60;

        ClientCalendarState state = ClientCalendarState.get();
        String label = state.labelFor(day);
        String command = state.commandFor(day);

        this.labelBox = new EditBox(this.font, centerX - 100, top + 10, 200, 18,
                Component.translatable("adventcalendar.admin.label"));
        this.labelBox.setMaxLength(48);
        this.labelBox.setValue(label == null ? "" : label);
        this.addRenderableWidget(this.labelBox);

        this.commandBox = new EditBox(this.font, centerX - 100, top + 46, 200, 18,
                Component.translatable("adventcalendar.admin.command"));
        this.commandBox.setMaxLength(256);
        this.commandBox.setValue(command == null ? "" : command);
        this.addRenderableWidget(this.commandBox);

        this.hiddenButton = this.addRenderableWidget(Button.builder(hiddenLabel(), button -> {
            this.hidden = !this.hidden;
            button.setMessage(hiddenLabel());
        }).bounds(centerX - 100, top + 74, 200, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.npc.save"),
                        button -> save())
                .bounds(centerX - 100, top + 100, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.admin.button.clearall"),
                        button -> {
                            ModNetwork.CHANNEL.sendToServer(
                                    AdminEditPacket.simple(AdminEditPacket.CLEAR_DAY, day));
                            back();
                        })
                .bounds(centerX + 4, top + 100, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.gui.button.close"),
                        button -> back())
                .bounds(centerX - 50, top + 124, 100, 20).build());
    }

    private Component hiddenLabel() {
        return Component.translatable("adventcalendar.admin.hidden", hidden ? "ON" : "OFF");
    }

    private void save() {
        ModNetwork.CHANNEL.sendToServer(AdminEditPacket.setDay(day, stack,
                this.labelBox.getValue().trim(), this.commandBox.getValue().trim(), hidden));
        back();
    }

    private void back() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int centerX = this.width / 2;
        int top = this.height / 2 - 60;

        GuiTextures.drawPanel(graphics, centerX - 110, top - 22, 220, 178);
        graphics.drawCenteredString(this.font, this.title, centerX, top - 16, GuiTextures.GOLD);
        graphics.drawString(this.font, Component.translatable("adventcalendar.admin.label"),
                centerX - 100, top, GuiTextures.TEXT_DIM, false);
        graphics.drawString(this.font, Component.translatable("adventcalendar.admin.command"),
                centerX - 100, top + 36, GuiTextures.TEXT_DIM, false);
        graphics.drawString(this.font, Component.translatable("adventcalendar.admin.command.hint"),
                centerX - 100, top + 66, GuiTextures.TEXT_DIM, false);

        // Reward preview
        ItemStack display = stack.isEmpty() && ClientCalendarState.get().isCommandOnly(day)
                ? ClientCalendarState.get().displayStackFor(day)
                : stack;
        if (!display.isEmpty()) {
            ItemStack copy = display.copy();
            copy.setCount(Math.min(copy.getCount(), 64));
            graphics.pose().pushPose();
            graphics.pose().translate(centerX + 78, top + 6, 0);
            graphics.pose().scale(1.6F, 1.6F, 1.0F);
            graphics.renderItem(copy, 0, 0);
            graphics.pose().popPose();
        }

        super.render(graphics, mouseX, mouseY, partialTick);
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

    @Override
    public void onClose() {
        back();
    }
}
