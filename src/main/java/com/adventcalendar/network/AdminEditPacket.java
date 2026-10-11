package com.adventcalendar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: every edit made in the admin GUI (drag & drop reward assignment, label
 * and command editing, clearing days, saving/reloading the JSON, spawning the NPC).
 * The server checks {@code hasPermissions(2)} before touching anything.
 */
public class AdminEditPacket {
    public static final int SET_DAY = 0;
    public static final int CLEAR_DAY = 1;
    public static final int CLEAR_ALL = 2;
    public static final int FILL_SAMPLE = 3;
    public static final int SAVE = 4;
    public static final int RELOAD = 5;
    public static final int SPAWN_NPC = 6;
    public static final int TAKE_DAY = 7;

    public final int action;
    public final int day;
    public final boolean hidden;
    public final String label;
    public final String command;
    public final ItemStack stack;

    public AdminEditPacket(int action, int day, boolean hidden, String label, String command, ItemStack stack) {
        this.action = action;
        this.day = day;
        this.hidden = hidden;
        this.label = label == null ? "" : label;
        this.command = command == null ? "" : command;
        this.stack = stack == null ? ItemStack.EMPTY : stack;
    }

    public static AdminEditPacket setDay(int day, ItemStack stack, String label, String command, boolean hidden) {
        return new AdminEditPacket(SET_DAY, day, hidden, label, command, stack);
    }

    public static AdminEditPacket simple(int action, int day) {
        return new AdminEditPacket(action, day, false, "", "", ItemStack.EMPTY);
    }

    public static void encode(AdminEditPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.day);
        buf.writeBoolean(msg.hidden);
        buf.writeUtf(msg.label, 128);
        buf.writeUtf(msg.command, 1024);
        buf.writeItem(msg.stack);
    }

    public static AdminEditPacket decode(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        int day = buf.readVarInt();
        boolean hidden = buf.readBoolean();
        String label = buf.readUtf(128);
        String command = buf.readUtf(1024);
        ItemStack stack = buf.readItem();
        return new AdminEditPacket(action, day, hidden, label, command, stack);
    }

    public static void handle(AdminEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerPacketHandler.handleAdminEdit(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
