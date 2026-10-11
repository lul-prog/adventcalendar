package com.adventcalendar.client;

import com.adventcalendar.AdventCalendar;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared base of the calendar GUIs: draws the framed panel, the 24 doors with their dynamic
 * icons (locked / available / claimed), the header and the footer, and forwards clicks on a
 * door to {@link #onDayClicked(int, int)}.
 */
public abstract class AbstractCalendarScreen extends Screen {
    protected final ClientCalendarState state = ClientCalendarState.get();
    protected final int bottomReserve;
    protected CalendarLayout layout;
    protected int hoveredDay;
    protected int selectedDay = 1;

    protected AbstractCalendarScreen(Component title, int bottomReserve) {
        super(title);
        this.bottomReserve = bottomReserve;
        this.layout = CalendarLayout.fit(state.days(), 320, 240, topReserve(), bottomReserve);
    }

    /** Vertical space kept free above the grid. */
    protected int topReserve() {
        return 46;
    }

    @Override
    protected void init() {
        super.init();
        this.layout = CalendarLayout.fit(state.days(), this.width, this.height, topReserve(), bottomReserve);
        this.selectedDay = Math.max(1, Math.min(state.days(), Math.max(1, this.selectedDay)));
        buildWidgets();
    }

    /** Subclasses add their buttons and text fields here. */
    protected abstract void buildWidgets();

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        this.hoveredDay = layout.dayAt(mouseX, mouseY);
        drawFrame(graphics);
        for (int day = 1; day <= state.days(); day++) {
            drawDoor(graphics, day, mouseX, mouseY);
        }
        drawHeader(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawFooter(graphics);
        drawTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int day = layout.dayAt(mouseX, mouseY);
        if (day > 0) {
            this.selectedDay = day;
            onDayClicked(day, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Called when a door is clicked; subclasses decide what happens. */
    protected void onDayClicked(int day, int button) {
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

    // ------------------------------------------------------------------ drawing

    protected void drawFrame(GuiGraphics graphics) {
        int padding = 8;
        int top = layout.gridY - topReserve() + 4;
        int bottom = Math.min(this.height - 2, layout.gridY + layout.gridHeight + bottomReserve + 4);
        GuiTextures.drawPanel(graphics, Math.max(2, layout.gridX - padding), Math.max(2, top),
                layout.gridWidth + padding * 2, bottom - top);
    }

    protected void drawHeader(GuiGraphics graphics) {
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, layout.gridY - 34, GuiTextures.GOLD);
        Component date = Component.translatable("adventcalendar.gui.realdate", state.dateString());
        graphics.drawCenteredString(this.font, date, centerX, layout.gridY - 22,
                state.isDateOverridden() ? GuiTextures.RED : GuiTextures.TEXT_DIM);
        if (!state.isDecemberNow() && state.isDecemberOnly()) {
            graphics.drawCenteredString(this.font, Component.translatable("adventcalendar.gui.subtitle.closed"),
                    centerX, layout.gridY - 10, GuiTextures.GOLD);
        } else if (state.isDateOverridden()) {
            graphics.drawCenteredString(this.font, Component.translatable("adventcalendar.gui.overridden", state.dateString()),
                    centerX, layout.gridY - 10, GuiTextures.RED);
        }
    }

    protected void drawFooter(GuiGraphics graphics) {
        int y = this.height - 12;
        int centerX = this.width / 2;
        if (state.pendingCount() > 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("adventcalendar.gui.stocking", state.pendingCount()),
                    centerX, y, GuiTextures.GOLD);
        }
    }

    protected void drawDoor(GuiGraphics graphics, int day, int mouseX, int mouseY) {
        int x = layout.cellX(day);
        int y = layout.cellY(day);
        int size = layout.cell;
        DayState dayState = state.stateOf(day);

        int background = switch (dayState) {
            case LOCKED -> GuiTextures.CELL_LOCKED_U;
            case AVAILABLE -> GuiTextures.CELL_AVAILABLE_U;
            case CLAIMED -> GuiTextures.CELL_CLAIMED_U;
        };
        GuiTextures.drawCell(graphics, x, y, size, background);

        ItemStack stack = state.displayStackFor(day);
        boolean showItem = !stack.isEmpty() && dayState != DayState.LOCKED;
        int iconSize = Math.max(8, size * 2 / 3);
        if (showItem) {
            drawScaledItem(graphics, stack, x, y, size);
            if (dayState == DayState.CLAIMED) {
                graphics.fill(x, y, x + size, y + size, 0x66101820);
            }
        } else {
            int iconU = dayState == DayState.CLAIMED ? GuiTextures.ICON_CHECK_U
                    : dayState == DayState.AVAILABLE ? GuiTextures.ICON_GIFT_U : GuiTextures.ICON_LOCK_U;
            GuiTextures.drawIcon(graphics, x + (size - iconSize) / 2, y + (size - iconSize) / 2 + 2, iconSize, iconU);
        }

        int numberColor = dayState == DayState.LOCKED ? GuiTextures.TEXT_DIM : 0xFFFFFFFF;
        graphics.drawString(this.font, String.valueOf(day), x + 2, y + 2, numberColor, true);

        if (dayState == DayState.CLAIMED && showItem) {
            GuiTextures.drawIcon(graphics, x + size - 9, y + size - 9, 9, GuiTextures.ICON_CHECK_U);
        }
        if (state.isToday(day)) {
            GuiTextures.drawIcon(graphics, x + size - 9, y + 1, 9, GuiTextures.ICON_SNOW_U);
        }

        float progress = state.animationProgress(day);
        if (progress >= 0.0F) {
            drawClaimAnimation(graphics, x, y, size, progress);
        }
        if (day == selectedDay) {
            graphics.renderOutline(x - 1, y - 1, size + 2, size + 2, GuiTextures.GOLD);
        }
        if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
            graphics.renderOutline(x, y, size, size, 0xFFFFFFFF);
        }
    }

    protected void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        float scale = (size - 2) / 16.0F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 1.0F, y + 1.0F, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    /** Expanding golden ring + white flash when a gift is claimed. */
    private void drawClaimAnimation(GuiGraphics graphics, int x, int y, int size, float progress) {
        float radius = size / 2.0F + progress * size;
        int alpha = (int) (0.55F * (1.0F - progress) * 255.0F);
        if (alpha <= 0) {
            return;
        }
        int color = (alpha << 24) | 0x00F0C24B;
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        graphics.fill(centerX - (int) radius, centerY - 1, centerX + (int) radius, centerY + 1, color);
        graphics.fill(centerX - 1, centerY - (int) radius, centerX + 1, centerY + (int) radius, color);
        int flash = (int) (0.35F * (1.0F - progress) * 255.0F);
        graphics.fill(x, y, x + size, y + size, (flash << 24) | 0x00FFFFFF);
    }

    protected void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredDay <= 0) {
            return;
        }
        int day = hoveredDay;
        DayState dayState = state.stateOf(day);
        List<Component> lines = new ArrayList<>();
        if (!state.isConfigured(day)) {
            lines.add(Component.translatable("adventcalendar.gui.day.empty", day));
        } else {
            switch (dayState) {
                case CLAIMED -> lines.add(Component.translatable("adventcalendar.gui.day.claimed", day));
                case LOCKED -> lines.add(Component.translatable("adventcalendar.gui.day.locked", day, day));
                default -> lines.add(Component.translatable("adventcalendar.gui.day.available", day));
            }
        }
        ItemStack stack = state.displayStackFor(day);
        if (!stack.isEmpty()) {
            lines.add(stack.getHoverName().copy().withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        String label = state.labelFor(day);
        if (label != null && !label.isEmpty()) {
            lines.add(Component.literal(label).withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
        graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    // ------------------------------------------------------------------ helpers

    protected void playUiSound(SoundEvent event, float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
        }
    }

    protected static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }

    protected static net.minecraft.resources.ResourceLocation icon(String name) {
        return new net.minecraft.resources.ResourceLocation(AdventCalendar.MOD_ID, "textures/gui/" + name + ".png");
    }
}
