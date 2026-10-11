package com.adventcalendar.client;

import com.adventcalendar.ModSounds;
import com.adventcalendar.network.AdminEditPacket;
import com.adventcalendar.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * OP only admin screen: pick any item of your inventory and drag it onto a day to assign it
 * as that day's reward.
 *
 * <ul>
 *     <li>left click a day with an item "in the brush" - assign it</li>
 *     <li>left click a day without a brush - open the day editor (label / command / hidden)</li>
 *     <li>right click a day - clear it</li>
 *     <li>ctrl + left click a day - take a copy of its reward</li>
 *     <li>shift + left click a day - assign the item you hold in your main hand</li>
 * </ul>
 */
public class AdventAdminScreen extends AbstractCalendarScreen {
    private static final int SLOT = 18;
    private static final int PALETTE_COLS = 9;
    private static final int PALETTE_ROWS = 4;

    private final List<ItemStack> palette = new ArrayList<>();
    private ItemStack brush = ItemStack.EMPTY;
    private boolean draggingBrush;
    private int paletteX;
    private int paletteY;

    public AdventAdminScreen() {
        super(Component.translatable("adventcalendar.admin.title"), 132);
    }

    @Override
    protected void buildWidgets() {
        this.paletteX = (this.width - PALETTE_COLS * SLOT) / 2;
        this.paletteY = this.height - 26 - PALETTE_ROWS * SLOT - 8;

        int rowY = this.height - 24;
        int buttonWidth = 82;
        int gap = 4;
        int total = buttonWidth * 6 + gap * 5;
        int startX = (this.width - total) / 2;

        this.addRenderableWidget(Button.builder(text("adventcalendar.admin.button.save"), button -> {
            send(AdminEditPacket.simple(AdminEditPacket.SAVE, 0));
        }).bounds(startX, rowY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(text("adventcalendar.admin.button.reload"), button -> {
            send(AdminEditPacket.simple(AdminEditPacket.RELOAD, 0));
        }).bounds(startX + (buttonWidth + gap), rowY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(text("adventcalendar.admin.button.sample"), button -> {
            send(AdminEditPacket.simple(AdminEditPacket.FILL_SAMPLE, 0));
        }).bounds(startX + (buttonWidth + gap) * 2, rowY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(text("adventcalendar.admin.button.clearall"), button -> {
            send(AdminEditPacket.simple(AdminEditPacket.CLEAR_ALL, 0));
        }).bounds(startX + (buttonWidth + gap) * 3, rowY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(text("adventcalendar.admin.button.spawn"), button -> {
            send(AdminEditPacket.simple(AdminEditPacket.SPAWN_NPC, 0));
        }).bounds(startX + (buttonWidth + gap) * 4, rowY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(text("adventcalendar.gui.button.close"), button -> this.onClose())
                .bounds(startX + (buttonWidth + gap) * 5, rowY, buttonWidth, 20).build());
    }

    private void send(AdminEditPacket packet) {
        ModNetwork.CHANNEL.sendToServer(packet);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = paletteSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            ItemStack stack = slotOf(slot);
            this.brush = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            this.draggingBrush = !this.brush.isEmpty();
            if (!this.brush.isEmpty()) {
                playUiSound(ModSounds.NOTIFY.get(), 1.6F);
            }
            return true;
        }
        int day = layout.dayAt(mouseX, mouseY);
        if (day > 0) {
            this.selectedDay = day;
            handleDayClick(day, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleDayClick(int day, int button) {
        if (button == 1) {
            send(AdminEditPacket.simple(AdminEditPacket.CLEAR_DAY, day));
            playUiSound(ModSounds.LOCK.get(), 1.2F);
            return;
        }
        if (button == 2) {
            ItemStack stack = state.stackFor(day);
            this.brush = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            return;
        }
        if (hasControlDown() && button == 0) {
            send(AdminEditPacket.simple(AdminEditPacket.TAKE_DAY, day));
            return;
        }
        if (hasShiftDown() && this.minecraft != null && this.minecraft.player != null) {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (!held.isEmpty()) {
                assign(day, held.copy());
                return;
            }
        }
        if (!brush.isEmpty()) {
            assign(day, brush.copy());
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DayEditorScreen(this, day));
        }
    }

    private void assign(int day, ItemStack stack) {
        // Keep the label and command that were configured before, only replace the item.
        String label = state.labelFor(day);
        String command = state.commandFor(day);
        boolean hidden = state.isHidden(day);
        send(AdminEditPacket.setDay(day, stack, label == null ? "" : label,
                command == null ? "" : command, hidden));
        playUiSound(ModSounds.CLAIM.get(), 1.6F);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingBrush) {
            this.draggingBrush = false;
            int day = layout.dayAt(mouseX, mouseY);
            if (day > 0 && !brush.isEmpty()) {
                assign(day, brush.copy());
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingBrush) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawFrame(GuiGraphics graphics) {
        // Extra panel for the inventory palette.
        GuiTextures.drawPanel(graphics, paletteX - 6, paletteY - 20,
                PALETTE_COLS * SLOT + 12, PALETTE_ROWS * SLOT + 26);
        super.drawFrame(graphics);
    }

    @Override
    protected void drawHeader(GuiGraphics graphics) {
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, layout.gridY - 36, GuiTextures.GOLD);
        graphics.drawCenteredString(this.font,
                text("adventcalendar.admin.configured", configuredDays(), state.days()),
                centerX, layout.gridY - 24, GuiTextures.GREEN);
        graphics.drawCenteredString(this.font, text("adventcalendar.gui.realdate", state.dateString()),
                centerX, layout.gridY - 12,
                state.isDateOverridden() ? GuiTextures.RED : GuiTextures.TEXT_DIM);
    }

    private int configuredDays() {
        int count = 0;
        for (int day = 1; day <= state.days(); day++) {
            if (state.isConfigured(day)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawPalette(graphics, mouseX, mouseY);
        if (!brush.isEmpty()) {
            drawScaledItem(graphics, brush, mouseX - 8, mouseY - 8, 17);
            graphics.drawString(this.font, text("adventcalendar.admin.hint.drag"),
                    paletteX - 6, paletteY - 32, GuiTextures.TEXT_DIM, false);
        }
    }

    private void drawPalette(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.literal("Inventory"), paletteX - 6, paletteY - 16,
                GuiTextures.GOLD, false);
        for (int slot = 0; slot < PALETTE_COLS * PALETTE_ROWS; slot++) {
            int x = paletteX + (slot % PALETTE_COLS) * SLOT;
            int y = paletteY + (slot / PALETTE_COLS) * SLOT;
            GuiTextures.drawSlot(graphics, x, y, SLOT);
            ItemStack stack = slotOf(slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
            }
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                graphics.renderOutline(x, y, SLOT, SLOT, 0xFFFFFFFF);
            }
        }
    }

    @Override
    protected void drawFooter(GuiGraphics graphics) {
        graphics.drawString(this.font, text("adventcalendar.admin.hint.clear"),
                8, this.height - 46, GuiTextures.TEXT_DIM, false);
        graphics.drawString(this.font, text("adventcalendar.admin.hint.grab"),
                8, this.height - 36, GuiTextures.TEXT_DIM, false);
    }

    @Override
    protected void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int slot = paletteSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            ItemStack stack = slotOf(slot);
            if (!stack.isEmpty()) {
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            }
            return;
        }
        super.drawTooltip(graphics, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ palette helpers

    private int paletteSlotAt(double mouseX, double mouseY) {
        if (mouseX < paletteX || mouseY < paletteY) {
            return -1;
        }
        int col = (int) ((mouseX - paletteX) / SLOT);
        int row = (int) ((mouseY - paletteY) / SLOT);
        if (col < 0 || col >= PALETTE_COLS || row < 0 || row >= PALETTE_ROWS) {
            return -1;
        }
        return row * PALETTE_COLS + col;
    }

    /** Palette slot 0-26 are the inventory main rows, 27-35 the hotbar. */
    private ItemStack slotOf(int paletteSlot) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        int index = paletteSlot < 27 ? paletteSlot + 9 : paletteSlot - 27;
        if (index < 0 || index >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(index);
    }
}
