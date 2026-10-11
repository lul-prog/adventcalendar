package com.adventcalendar.client;

import com.adventcalendar.ModSounds;
import com.adventcalendar.network.CalendarActionPacket;
import com.adventcalendar.network.DebugActionPacket;
import com.adventcalendar.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Developer screen (OP level 2). Lets a server owner test the whole mod without waiting for
 * December: bypass the real date, preview every reward, force claims, reset progress, grant
 * everything at once and play each custom sound.
 */
public class AdventDebugScreen extends AbstractCalendarScreen {
    private EditBox dateBox;

    public AdventDebugScreen() {
        super(Component.translatable("adventcalendar.debug.title"), 138);
    }

    @Override
    protected void buildWidgets() {
        int centerX = this.width / 2;
        int baseY = this.height - 132;

        this.dateBox = new EditBox(this.font, centerX - 130, baseY, 110, 18,
                Component.translatable("adventcalendar.debug.input"));
        this.dateBox.setMaxLength(10);
        this.dateBox.setValue(state.dateString());
        this.addRenderableWidget(this.dateBox);

        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.debug.apply"), button -> {
            send(DebugActionPacket.text(DebugActionPacket.SET_DATE_TEXT, dateBox.getValue()));
        }).bounds(centerX - 16, baseY, 60, 18).build());

        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.debug.button.prev"),
                        button -> send(DebugActionPacket.of(DebugActionPacket.SHIFT_DAYS, -1)))
                .bounds(centerX + 48, baseY, 82, 18).build());

        int rowTwo = baseY + 22;
        buttonRow(rowTwo, centerX, new String[]{
                "adventcalendar.debug.button.dec1",
                "adventcalendar.debug.button.clear",
                "adventcalendar.debug.preview",
                "adventcalendar.debug.simulate"}, new Runnable[]{
                () -> send(DebugActionPacket.of(DebugActionPacket.SET_UNLOCK_DAY, 1)),
                () -> send(DebugActionPacket.of(DebugActionPacket.CLEAR_DATE, 0)),
                () -> send(DebugActionPacket.of(DebugActionPacket.SET_PREVIEW, state.isPreview() ? 0 : 1)),
                () -> state.setSimulating(!state.isSimulating())});

        int rowThree = rowTwo + 22;
        buttonRow(rowThree, centerX, new String[]{
                "adventcalendar.debug.button.claim",
                "adventcalendar.debug.button.grant",
                "adventcalendar.debug.button.reset",
                "adventcalendar.debug.button.resetall"}, new Runnable[]{
                () -> send(DebugActionPacket.of(DebugActionPacket.FORCE_CLAIM, selectedDay)),
                () -> send(DebugActionPacket.of(DebugActionPacket.GRANT_ALL, 0)),
                () -> send(DebugActionPacket.of(DebugActionPacket.RESET_MY_CLAIMS, 0)),
                () -> send(DebugActionPacket.of(DebugActionPacket.RESET_ALL_CLAIMS, 0))});

        int rowFour = rowThree + 22;
        buttonRow(rowFour, centerX, new String[]{"♪ lock", "♪ unlock", "♪ claim", "♪ notify"}, new Runnable[]{
                () -> playUiSound(ModSounds.LOCK.get(), 1.0F),
                () -> playUiSound(ModSounds.UNLOCK.get(), 1.0F),
                () -> playUiSound(ModSounds.CLAIM.get(), 1.0F),
                () -> playUiSound(ModSounds.NOTIFY.get(), 1.0F)});

        int rowFive = rowFour + 22;
        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.gui.button.admin"), button ->
                        sendAction(CalendarActionPacket.OPEN_ADMIN))
                .bounds(centerX - 130, rowFive, 84, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.gui.title"), button ->
                        sendAction(CalendarActionPacket.OPEN_CALENDAR))
                .bounds(centerX - 42, rowFive, 84, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("adventcalendar.gui.button.close"),
                        button -> this.onClose())
                .bounds(centerX + 46, rowFive, 84, 20).build());
    }

    private void buttonRow(int y, int centerX, String[] keys, Runnable[] actions) {
        int width = 62;
        int gap = 4;
        int total = width * keys.length + gap * (keys.length - 1);
        int x = centerX - total / 2;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            Runnable action = actions[i];
            String label = key.startsWith("♪") ? key : Component.translatable(key).getString();
            this.addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
                    .bounds(x + i * (width + gap), y, width, 20).build());
        }
    }

    private void send(DebugActionPacket packet) {
        ModNetwork.CHANNEL.sendToServer(packet);
    }

    private void sendAction(int action) {
        ModNetwork.CHANNEL.sendToServer(new CalendarActionPacket(action, 0));
    }

    @Override
    protected void drawHeader(GuiGraphics graphics) {
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, layout.gridY - 40, GuiTextures.GOLD);
        graphics.drawCenteredString(this.font,
                Component.translatable("adventcalendar.debug.date", state.dateString()),
                centerX, layout.gridY - 28, GuiTextures.TEXT);
        String override = state.isDateOverridden()
                ? Component.translatable("adventcalendar.debug.override", state.dateString()).getString()
                : Component.translatable("adventcalendar.debug.override.none").getString();
        graphics.drawCenteredString(this.font, Component.literal(override), centerX, layout.gridY - 16,
                state.isDateOverridden() ? GuiTextures.RED : GuiTextures.TEXT_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int y = this.height - 8;
        graphics.drawString(this.font, Component.translatable("adventcalendar.debug.calendar",
                state.year(), state.unlockedThrough()), centerX - 130, y - 6, GuiTextures.TEXT_DIM, false);
        Component hint = Component.translatable("adventcalendar.debug.hint");
        graphics.drawString(this.font, hint, centerX - 130, y - 16, GuiTextures.TEXT_DIM, false);
        // live status of the toggles
        graphics.drawString(this.font, Component.translatable("adventcalendar.debug.preview",
                state.isPreview() ? "ON" : "OFF"), centerX + 40, y - 16, GuiTextures.GOLD, false);
        graphics.drawString(this.font, Component.translatable("adventcalendar.debug.simulate",
                state.isSimulating() ? "ON" : "OFF"), centerX + 40, y - 6, GuiTextures.GOLD, false);
    }

    @Override
    protected void onDayClicked(int day, int button) {
        if (button == 0) {
            ModNetwork.CHANNEL.sendToServer(DebugActionPacket.of(DebugActionPacket.FORCE_CLAIM, day));
        } else if (button == 1) {
            ModNetwork.CHANNEL.sendToServer(DebugActionPacket.of(DebugActionPacket.SET_UNLOCK_DAY, day));
        }
    }

    @Override
    protected void drawFooter(GuiGraphics graphics) {
        // The hint lines are drawn in render(), the footer stays empty here.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
