package com.adventcalendar.network;

import com.adventcalendar.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: abre la interfaz de configuración del NPC indicado.
 * La GUI lee los datos directamente de la entidad (ya están sincronizados).
 */
public class OpenNpcConfigPacket {
    private final int entityId;

    public OpenNpcConfigPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenNpcConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static OpenNpcConfigPacket decode(FriendlyByteBuf buf) {
        return new OpenNpcConfigPacket(buf.readInt());
    }

    public static void handle(OpenNpcConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientHooks.openNpcConfig(msg.entityId)));
        ctx.get().setPacketHandled(true);
    }
}
