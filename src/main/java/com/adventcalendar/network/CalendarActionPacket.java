package com.adventcalendar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the actions a player can trigger from the calendar GUI.
 * Everything is validated again on the server, the client is never trusted.
 */
public class CalendarActionPacket {
    public static final int REQUEST_SYNC = 0;
    public static final int CLAIM = 1;
    public static final int RETRIEVE_STOCKING = 2;
    public static final int OPEN_CALENDAR = 3;
    public static final int OPEN_ADMIN = 4;
    public static final int OPEN_DEBUG = 5;

    public final int action;
    public final int day;

    public CalendarActionPacket(int action, int day) {
        this.action = action;
        this.day = day;
    }

    public static void encode(CalendarActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.day);
    }

    public static CalendarActionPacket decode(FriendlyByteBuf buf) {
        return new CalendarActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(CalendarActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerPacketHandler.handleAction(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
