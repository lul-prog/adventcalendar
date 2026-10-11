package com.adventcalendar.client;

import com.adventcalendar.data.AdventProgress;
import com.adventcalendar.network.ClaimDayPacket;
import com.adventcalendar.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI del calendario de adviento: rejilla de 31 días.
 * - Gris: día futuro / bloqueado (o sin recompensa configurada)
 * - Dorado: disponible para reclamar
 * - Verde: ya reclamado
 * Si no es diciembre, muestra "Vuelve en diciembre".
 */
public class AdventCalendarScreen extends Screen {
    private static final int COLS = 7;
    private static final int BTN = 30;
    private static final int GAP = 4;

    private int claimedMask;
    private final int rewardsMask;
    private final List<DayButton> dayButtons = new ArrayList<>();

    public AdventCalendarScreen(int claimedMask, int rewardsMask) {
        super(Component.literal("Calendario de Adviento"));
        this.claimedMask = claimedMask;
        this.rewardsMask = rewardsMask;
    }

    private enum DayState {
        LOCKED, OPEN, CLAIMED
    }

    @Override
    protected void init() {
        dayButtons.clear();
        boolean december = AdventProgress.isDecember();
        int today = AdventProgress.today();
        int gridW = COLS * BTN + (COLS - 1) * GAP;
        int x0 = (this.width - gridW) / 2;
        int y0 = 52;
        for (int day = 1; day <= 31; day++) {
            int x = x0 + ((day - 1) % COLS) * (BTN + GAP);
            int y = y0 + ((day - 1) / COLS) * (BTN + GAP);
            DayButton b = new DayButton(x, y, day, stateOf(day, december, today));
            dayButtons.add(b);
            this.addRenderableWidget(b);
        }
        this.addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> this.onClose())
                .bounds((this.width - 100) / 2, this.height - 36, 100, 20)
                .build());
    }

    private DayState stateOf(int day, boolean december, int today) {
        if ((claimedMask & (1 << (day - 1))) != 0) {
            return DayState.CLAIMED;
        }
        if (!december || day > today) {
            return DayState.LOCKED;
        }
        if ((rewardsMask & (1 << (day - 1))) == 0) {
            return DayState.LOCKED; // el admin aún no configuró premio para este día
        }
        return DayState.OPEN;
    }

    /** Clic en un día disponible: pide al servidor el reclamo y actualiza el botón. */
    private void onDayClick(int day) {
        ModNetwork.CHANNEL.sendToServer(new ClaimDayPacket(day));
        // Optimista: el servidor aplica exactamente las mismas validaciones.
        claimedMask |= (1 << (day - 1));
        boolean december = AdventProgress.isDecember();
        int today = AdventProgress.today();
        for (DayButton b : dayButtons) {
            b.refresh(stateOf(b.day, december, today));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        if (!AdventProgress.isDecember()) {
            g.drawCenteredString(this.font, "Vuelve en diciembre",
                    this.width / 2, this.height / 2 - 70, 0xFFE0A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Botón de un día, pintado con el color de su estado. */
    private class DayButton extends Button {
        private final int day;
        private DayState state;

        DayButton(int x, int y, int day, DayState state) {
            super(x, y, BTN, BTN, Component.literal(String.valueOf(day)),
                    b -> onDayClick(day), Button.DEFAULT_NARRATION);
            this.day = day;
            refresh(state);
        }

        void refresh(DayState state) {
            this.state = state;
            this.active = state == DayState.OPEN;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int color = switch (state) {
                case LOCKED -> 0xFF5A5A5A; // gris: bloqueado
                case CLAIMED -> 0xFF2E9E4F; // verde: ya reclamado
                case OPEN -> this.isHoveredOrFocused() ? 0xFFFFD54A : 0xFFC9962E; // dorado: disponible
            };
            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);
            g.drawCenteredString(AdventCalendarScreen.this.font, String.valueOf(day),
                    this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, 0xFFFFFFFF);
        }
    }
}
