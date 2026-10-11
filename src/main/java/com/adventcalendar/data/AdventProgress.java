package com.adventcalendar.data;

import com.adventcalendar.AdventCalendar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.time.LocalDate;

/**
 * Días reclamados por jugador: bitmask de 32 bits (se usan los bits 0-30)
 * guardado en el NBT persistente del jugador bajo la clave "adventcalendar".
 */
public final class AdventProgress {
    private AdventProgress() {
    }

    public static int getMask(Player player) {
        return player.getPersistentData().getInt(AdventCalendar.MOD_ID);
    }

    public static boolean isClaimed(Player player, int day) {
        return (getMask(player) & (1 << (day - 1))) != 0;
    }

    public static void setClaimed(Player player, int day) {
        CompoundTag tag = player.getPersistentData();
        tag.putInt(AdventCalendar.MOD_ID, getMask(player) | (1 << (day - 1)));
    }

    /** Fecha real del servidor (zona horaria de la máquina). */
    public static boolean isDecember() {
        return LocalDate.now().getMonthValue() == 12;
    }

    public static int today() {
        return LocalDate.now().getDayOfMonth();
    }
}
