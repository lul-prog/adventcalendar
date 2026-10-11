package com.adventcalendar.network;

import com.adventcalendar.entity.AdventNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: applies the configuration of one Advent NPC (name, colour, skin,
 * skin model, glow, particles, invulnerability, look-at-players, ambient sound).
 * OP level 2 is required and the entity has to exist on the server.
 */
public class NpcConfigPacket {
    public final int entityId;
    public final String name;
    public final String color;
    public final boolean glow;
    public final boolean particles;
    public final boolean invulnerable;
    public final String skin;
    public final String skinModel;
    public final boolean lookAtPlayers;
    public final boolean ambientSound;

    public NpcConfigPacket(int entityId, String name, String color, boolean glow, boolean particles,
                           boolean invulnerable, String skin, String skinModel, boolean lookAtPlayers,
                           boolean ambientSound) {
        this.entityId = entityId;
        this.name = name == null ? "" : name;
        this.color = color == null ? "WHITE" : color;
        this.glow = glow;
        this.particles = particles;
        this.invulnerable = invulnerable;
        this.skin = skin == null ? "" : skin;
        this.skinModel = skinModel == null ? "AUTO" : skinModel;
        this.lookAtPlayers = lookAtPlayers;
        this.ambientSound = ambientSound;
    }

    public static void encode(NpcConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.name, 64);
        buf.writeUtf(msg.color, 24);
        buf.writeBoolean(msg.glow);
        buf.writeBoolean(msg.particles);
        buf.writeBoolean(msg.invulnerable);
        buf.writeUtf(msg.skin, 512);
        buf.writeUtf(msg.skinModel, 16);
        buf.writeBoolean(msg.lookAtPlayers);
        buf.writeBoolean(msg.ambientSound);
    }

    public static NpcConfigPacket decode(FriendlyByteBuf buf) {
        return new NpcConfigPacket(buf.readVarInt(), buf.readUtf(64), buf.readUtf(24), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readUtf(512), buf.readUtf(16),
                buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(NpcConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            Entity entity = player.serverLevel().getEntity(msg.entityId);
            if (entity instanceof AdventNpcEntity npc) {
                npc.applyConfig(msg.name, msg.color, msg.glow, msg.particles, msg.invulnerable,
                        msg.skin, msg.skinModel, msg.lookAtPlayers, msg.ambientSound);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
