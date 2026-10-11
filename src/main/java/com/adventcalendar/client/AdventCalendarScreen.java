package com.adventcalendar.client;

import com.adventcalendar.ModSounds;
import com.adventcalendar.network.CalendarActionPacket;
import com.adventcalendar.network.ModNetwork;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * The player facing calendar: 24 doors, each one showing its own state (locked, available,
 * claimed) and, once unlocked, the reward icon the server decided to reveal.
 */
public class AdventCalendarScreen extends AbstractCalendarScreen {
    public AdventCalendarScreen() {
        super(Component.translatable("adventcalendar.gui.title"), 46);
    }

    @Override
    protected void buildWidgets() {
        int centerX = this.width / 2;
        int buttonY = this.height - 26;
        int offset = 0;

        this.addRenderableWidget(Button.builder(text("adventcalendar.gui.button.close"), button -> this.onClose())
                .bounds(centerX - 50, buttonY, 100, 20)
                .build());
        offset += 104;

        if (state.pendingCount() > 0) {
            Component label = text("adventcalendar.gui.button.retrieve", state.pendingCount());
            this.addRenderableWidget(Button.builder(label, button ->
                            ModNetwork.CHANNEL.sendToServer(new CalendarActionPacket(
                                    CalendarActionPacket.RETRIEVE_STOCKING, 0)))
                    .bounds(centerX + offset - 104 - 60, buttonY, 120, 20)
                    .build());
        }
        if (state.isAdmin()) {
            this.addRenderableWidget(Button.builder(text("adventcalendar.gui.button.admin"), button ->
                            ModNetwork.CHANNEL.sendToServer(new CalendarActionPacket(
                                    CalendarActionPacket.OPEN_ADMIN, 0)))
                    .bounds(8, 8, 70, 18)
                    .build());
            this.addRenderableWidget(Button.builder(text("adventcalendar.gui.button.debug"), button ->
                            ModNetwork.CHANNEL.sendToServer(new CalendarActionPacket(
                                    CalendarActionPacket.OPEN_DEBUG, 0)))
                    .bounds(82, 8, 70, 18)
                    .build());
        }
    }

    @Override
    protected void onDayClicked(int day, int button) {
        if (button != 0) {
            return;
        }
        DayState dayState = state.stateOf(day);
        switch (dayState) {
            case LOCKED -> {
                // The error sound the spec asks for: instant local feedback on a locked door.
                playUiSound(ModSounds.LOCK.get(), 0.8F);
                if (!state.isConfigured(day)) {
                    playUiSound(ModSounds.NOTIFY.get(), 0.8F);
                }
            }
            case CLAIMED -> playUiSound(ModSounds.LOCK.get(), 1.4F);
            case AVAILABLE -> {
                playUiSound(ModSounds.UNLOCK.get(), 1.3F);
                ModNetwork.CHANNEL.sendToServer(new CalendarActionPacket(CalendarActionPacket.CLAIM, day));
            }
            default -> {
            }
        }
    }

    @Override
    protected void drawFooter(net.minecraft.client.gui.GuiGraphics graphics) {
        super.drawFooter(graphics);
        if (state.isSimulating()) {
            graphics.drawCenteredString(this.font, text("adventcalendar.debug.simulate", "ON"),
                    this.width / 2, this.height - 38, GuiTextures.RED);
        }
    }
}
