package com.adventcalendar.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: actions of the debug GUI (date override, preview, forced claims,
 * resetting progress). OP level 2 required; meant for testing the mod.
 */
public class DebugActionPacket {
    public static final int SET_DATE_TEXT = 0;
    public static final int SHIFT_DAYS = 1;
    public static final int CLEAR_DATE = 2;
    public static final int SET_PREVIEW = 3;
    public static final int RESET_MY_CLAIMS = 4;
    public static final int RESET_ALL_CLAIMS = 5;
    public static final int FORCE_CLAIM = 6;
    public static final int GRANT_ALL = 7;
    public static final int SET_UNLOCK_DAY = 8;
    public static final int REQUEST_SYNC = 9;

    public final int action;
    public final int value;
    public final String text;

    public DebugActionPacket(int action, int value, String text) {
        this.action = action;
        this.value = value;
        this.text = text == null ? "" : text;
    }

    public static DebugActionPacket of(int action, int value) {
        return new DebugActionPacket(action, value, "");
    }

    public static DebugActionPacket text(int action, String text) {
        return new DebugActionPacket(action, 0, text);
    }

    public static void encode(DebugActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.value);
        buf.writeUtf(msg.text, 64);
    }

    public static DebugActionPacket decode(FriendlyByteBuf buf) {
        return new DebugActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(DebugActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerPacketHandler.handleDebugAction(player, msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
