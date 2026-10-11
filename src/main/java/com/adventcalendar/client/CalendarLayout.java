package com.adventcalendar.client;

/**
 * Geometry of the 24 door grid (6 columns, 4 rows by default). The cell size adapts to the
 * screen so the GUIs also work on small windows or with a high GUI scale.
 */
public final class CalendarLayout {
    public final int days;
    public final int cols;
    public final int rows;
    public final int cell;
    public final int gap;
    public final int gridX;
    public final int gridY;
    public final int gridWidth;
    public final int gridHeight;

    private CalendarLayout(int days, int cols, int rows, int cell, int gap,
                           int gridX, int gridY, int gridWidth, int gridHeight) {
        this.days = days;
        this.cols = cols;
        this.rows = rows;
        this.cell = cell;
        this.gap = gap;
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
    }

    public static CalendarLayout fit(int days, int screenWidth, int screenHeight, int top, int bottom) {
        int safeDays = Math.max(1, days);
        int cols = safeDays <= 6 ? safeDays : 6;
        int rows = (int) Math.ceil(safeDays / (double) cols);
        int gap = 3;
        int availableWidth = Math.max(40, screenWidth - 24);
        int availableHeight = Math.max(60, screenHeight - top - bottom);
        int byWidth = (availableWidth - (cols - 1) * gap) / cols;
        int byHeight = (availableHeight - (rows - 1) * gap) / rows;
        int cell = Math.max(12, Math.min(26, Math.min(byWidth, byHeight)));
        int gridWidth = cols * cell + (cols - 1) * gap;
        int gridHeight = rows * cell + (rows - 1) * gap;
        int gridX = Math.max(6, (screenWidth - gridWidth) / 2);
        int gridY = top;
        return new CalendarLayout(safeDays, cols, rows, cell, gap, gridX, gridY, gridWidth, gridHeight);
    }

    public int cellX(int day) {
        int index = day - 1;
        return gridX + (index % cols) * (cell + gap);
    }

    public int cellY(int day) {
        int index = day - 1;
        return gridY + (index / cols) * (cell + gap);
    }

    /** Day under the mouse cursor, or {@code 0} when the cursor is outside of the grid. */
    public int dayAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseY < gridY) {
            return 0;
        }
        int col = (int) ((mouseX - gridX) / (cell + gap));
        int row = (int) ((mouseY - gridY) / (cell + gap));
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return 0;
        }
        // reject the gap between the doors
        double localX = (mouseX - gridX) - col * (cell + gap);
        double localY = (mouseY - gridY) - row * (cell + gap);
        if (localX > cell || localY > cell) {
            return 0;
        }
        int day = row * cols + col + 1;
        return day <= days ? day : 0;
    }

    public boolean contains(int day, double mouseX, double mouseY) {
        if (day < 1 || day > days) {
            return false;
        }
        int x = cellX(day);
        int y = cellY(day);
        return mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell;
    }
}
