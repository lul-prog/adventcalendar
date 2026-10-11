package com.adventcalendar.network;

import com.adventcalendar.entity.AdventNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor: aplica la configuración del NPC desde la GUI del mando.
 * El servidor valida el permiso de operador y que la entidad exista.
 */
public class NpcConfigPacket {
    private final int entityId;
    private final String name;
    private final String color;
    private final boolean glow;
    private final boolean particles;
    private final boolean invulnerable;
    private final String skin;

    public NpcConfigPacket(int entityId, String name, String color, boolean glow,
                           boolean particles, boolean invulnerable, String skin) {
        this.entityId = entityId;
        this.name = name;
        this.color = color;
        this.glow = glow;
        this.particles = particles;
        this.invulnerable = invulnerable;
        this.skin = skin;
    }

    public static void encode(NpcConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeUtf(msg.name);
        buf.writeUtf(msg.color);
        buf.writeBoolean(msg.glow);
        buf.writeBoolean(msg.particles);
        buf.writeBoolean(msg.invulnerable);
        buf.writeUtf(msg.skin);
    }

    public static NpcConfigPacket decode(FriendlyByteBuf buf) {
        return new NpcConfigPacket(buf.readInt(), buf.readUtf(), buf.readUtf(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUtf());
    }

    public static void handle(NpcConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            // Solo operadores (igual que los comandos /advent)
            if (!player.hasPermissions(2)) {
                return;
            }
            Entity e = player.serverLevel().getEntity(msg.entityId);
            if (!(e instanceof AdventNpcEntity npc)) {
                return;
            }
            npc.applyConfig(msg.name, msg.color, msg.glow, msg.particles, msg.invulnerable, msg.skin);
        });
        ctx.get().setPacketHandled(true);
    }
}
