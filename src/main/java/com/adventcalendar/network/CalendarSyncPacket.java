package com.adventcalendar.network;

import com.adventcalendar.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: complete, player specific state of the calendar.
 *
 * <p>Sent when a player logs in, opens a GUI, claims something or when the calendar rolls
 * over to a new day. Locked rewards are simply not included unless the viewer is allowed to
 * see them, so a modified client cannot peek at future gifts.</p>
 */
public class CalendarSyncPacket {
    /** One configured door. */
    public static final class DayEntry {
        public final int day;
        public final ItemStack stack;
        public final String command;
        public final String label;
        public final boolean hidden;
        public final boolean commandOnly;

        public DayEntry(int day, ItemStack stack, String command, String label, boolean hidden, boolean commandOnly) {
            this.day = day;
            this.stack = stack;
            this.command = command;
            this.label = label;
            this.hidden = hidden;
            this.commandOnly = commandOnly;
        }
    }

    public final int days;
    public final int year;
    public final int month;
    public final int dayOfMonth;
    public final int unlockedThrough;
    public final int claimedMask;
    public final int configuredMask;
    public final int pendingCount;
    public final boolean admin;
    public final boolean dateOverridden;
    public final boolean decemberOnly;
    public final boolean preview;
    public final boolean hideLocked;
    public final List<DayEntry> entries;

    public CalendarSyncPacket(int days, int year, int month, int dayOfMonth, int unlockedThrough,
                              int claimedMask, int configuredMask, int pendingCount, boolean admin,
                              boolean dateOverridden, boolean decemberOnly, boolean preview,
                              boolean hideLocked, List<DayEntry> entries) {
        this.days = days;
        this.year = year;
        this.month = month;
        this.dayOfMonth = dayOfMonth;
        this.unlockedThrough = unlockedThrough;
        this.claimedMask = claimedMask;
        this.configuredMask = configuredMask;
        this.pendingCount = pendingCount;
        this.admin = admin;
        this.dateOverridden = dateOverridden;
        this.decemberOnly = decemberOnly;
        this.preview = preview;
        this.hideLocked = hideLocked;
        this.entries = entries;
    }

    public static void encode(CalendarSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.days);
        buf.writeVarInt(msg.year);
        buf.writeVarInt(msg.month);
        buf.writeVarInt(msg.dayOfMonth);
        buf.writeVarInt(msg.unlockedThrough);
        buf.writeInt(msg.claimedMask);
        buf.writeInt(msg.configuredMask);
        buf.writeVarInt(msg.pendingCount);
        buf.writeBoolean(msg.admin);
        buf.writeBoolean(msg.dateOverridden);
        buf.writeBoolean(msg.decemberOnly);
        buf.writeBoolean(msg.preview);
        buf.writeBoolean(msg.hideLocked);
        buf.writeVarInt(msg.entries.size());
        for (DayEntry entry : msg.entries) {
            buf.writeVarInt(entry.day);
            buf.writeItem(entry.stack);
            buf.writeUtf(entry.command, 512);
            buf.writeUtf(entry.label, 64);
            buf.writeBoolean(entry.hidden);
            buf.writeBoolean(entry.commandOnly);
        }
    }

    public static CalendarSyncPacket decode(FriendlyByteBuf buf) {
        int days = buf.readVarInt();
        int year = buf.readVarInt();
        int month = buf.readVarInt();
        int dayOfMonth = buf.readVarInt();
        int unlockedThrough = buf.readVarInt();
        int claimedMask = buf.readInt();
        int configuredMask = buf.readInt();
        int pendingCount = buf.readVarInt();
        boolean admin = buf.readBoolean();
        boolean dateOverridden = buf.readBoolean();
        boolean decemberOnly = buf.readBoolean();
        boolean preview = buf.readBoolean();
        boolean hideLocked = buf.readBoolean();
        int size = buf.readVarInt();
        List<DayEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int day = buf.readVarInt();
            ItemStack stack = buf.readItem();
            String command = buf.readUtf(512);
            String label = buf.readUtf(64);
            boolean hidden = buf.readBoolean();
            boolean commandOnly = buf.readBoolean();
            entries.add(new DayEntry(day, stack, command, label, hidden, commandOnly));
        }
        return new CalendarSyncPacket(days, year, month, dayOfMonth, unlockedThrough, claimedMask,
                configuredMask, pendingCount, admin, dateOverridden, decemberOnly, preview, hideLocked, entries);
    }

    public static void handle(CalendarSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.applySync(msg)));
        ctx.get().setPacketHandled(true);
    }
}
