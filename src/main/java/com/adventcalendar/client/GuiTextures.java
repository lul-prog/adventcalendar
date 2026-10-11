package com.adventcalendar.client;

import com.adventcalendar.AdventCalendar;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture atlas of the mod GUIs.
 *
 * <p>{@code textures/gui/calendar.png} holds the door backgrounds (locked / available /
 * claimed), the hover and "today" overlays and the small badge icons. Everything is drawn
 * scaled from a fixed 24x24 (or 16x16) source region, so the GUIs keep their shape on every
 * GUI scale.</p>
 */
public final class GuiTextures {
    public static final ResourceLocation CALENDAR =
            new ResourceLocation(AdventCalendar.MOD_ID, "textures/gui/calendar.png");

    public static final int CELL_SIZE = 24;
    public static final int ICON_SIZE = 16;

    public static final int CELL_LOCKED_U = 0;
    public static final int CELL_AVAILABLE_U = 24;
    public static final int CELL_CLAIMED_U = 48;
    public static final int CELL_HOVER_U = 72;
    public static final int CELL_TODAY_U = 96;
    public static final int CELL_ROW_V = 0;

    public static final int ICON_ROW_V = 24;
    public static final int ICON_LOCK_U = 0;
    public static final int ICON_GIFT_U = 16;
    public static final int ICON_CHECK_U = 32;
    public static final int ICON_SNOW_U = 48;
    public static final int ICON_STAR_U = 64;

    // A few colours shared by the screens.
    public static final int PANEL_BG = 0xE610161F;
    public static final int PANEL_BORDER = 0xFF3B4A5A;
    public static final int TEXT = 0xFFE8EEF4;
    public static final int TEXT_DIM = 0xFF9AA7B4;
    public static final int GOLD = 0xFFF0C24B;
    public static final int GREEN = 0xFF6FD08C;
    public static final int RED = 0xFFE06C6C;
    public static final int SLOT_BG = 0xFF1B2430;
    public static final int SLOT_BORDER = 0xFF44566B;

    private GuiTextures() {
    }

    /** Blits a square region of the atlas, scaled to {@code size}. */
    public static void drawSquare(GuiGraphics graphics, int x, int y, int size, int u, int v, int source) {
        float scale = size / (float) source;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.blit(CALENDAR, 0, 0, u, v, source, source);
        pose.popPose();
    }

    public static void drawCell(GuiGraphics graphics, int x, int y, int size, int u) {
        drawSquare(graphics, x, y, size, u, CELL_ROW_V, CELL_SIZE);
    }

    public static void drawIcon(GuiGraphics graphics, int x, int y, int size, int u) {
        drawSquare(graphics, x, y, size, u, ICON_ROW_V, ICON_SIZE);
    }

    /** A simple raised panel, used as background of the GUIs. */
    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        graphics.fill(x, y, x + width, y + 1, PANEL_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + height, PANEL_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);
    }

    /** Vanilla-ish inventory slot frame. */
    public static void drawSlot(GuiGraphics graphics, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, SLOT_BG);
        graphics.fill(x, y, x + size, y + 1, SLOT_BORDER);
        graphics.fill(x, y + size - 1, x + size, y + size, SLOT_BORDER);
        graphics.fill(x, y, x + 1, y + size, SLOT_BORDER);
        graphics.fill(x + size - 1, y, x + size, y + size, SLOT_BORDER);
    }

    public static void drawProgressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF101820);
        graphics.fill(x, y, x + (int) (width * Math.max(0.0F, Math.min(1.0F, progress))), y + height, color);
        graphics.fill(x, y, x + width, y + 1, PANEL_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
    }
}
