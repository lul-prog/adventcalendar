package com.adventcalendar.network;

import com.adventcalendar.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: abre la GUI del calendario.
 * Lleva los bitmask de días reclamados y de recompensas configuradas,
 * porque el NBT persistente del jugador no se sincroniza solo al cliente.
 */
public class OpenCalendarPacket {
    private final int claimedMask;
    private final int rewardsMask;

    public OpenCalendarPacket(int claimedMask, int rewardsMask) {
        this.claimedMask = claimedMask;
        this.rewardsMask = rewardsMask;
    }

    public static void encode(OpenCalendarPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.claimedMask);
        buf.writeInt(msg.rewardsMask);
    }

    public static OpenCalendarPacket decode(FriendlyByteBuf buf) {
        return new OpenCalendarPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(OpenCalendarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.openCalendar(msg.claimedMask, msg.rewardsMask)));
        ctx.get().setPacketHandled(true);
    }
}
