package com.adventcalendar.network;

import com.adventcalendar.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open one of the mod's GUIs. */
public class OpenGuiPacket {
    public static final int GUI_CALENDAR = 0;
    public static final int GUI_ADMIN = 1;
    public static final int GUI_DEBUG = 2;
    public static final int GUI_NPC_CONFIG = 3;

    public final int gui;
    public final int entityId;

    public OpenGuiPacket(int gui, int entityId) {
        this.gui = gui;
        this.entityId = entityId;
    }

    public OpenGuiPacket(int gui) {
        this(gui, -1);
    }

    public static void encode(OpenGuiPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.gui);
        buf.writeVarInt(msg.entityId + 1);
    }

    public static OpenGuiPacket decode(FriendlyByteBuf buf) {
        int gui = buf.readVarInt();
        int entityId = buf.readVarInt() - 1;
        return new OpenGuiPacket(gui, entityId);
    }

    public static void handle(OpenGuiPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.openGui(msg.gui, msg.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
