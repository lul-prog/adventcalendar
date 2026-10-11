package com.adventcalendar.network;

import com.adventcalendar.data.AdventProgress;
import com.adventcalendar.data.AdventRewardsData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor: el jugador reclama la recompensa de un día.
 * El servidor valida todo de nuevo (fecha, día, no reclamado, recompensa configurada).
 */
public class ClaimDayPacket {
    private final int day;

    public ClaimDayPacket(int day) {
        this.day = day;
    }

    public static void encode(ClaimDayPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.day);
    }

    public static ClaimDayPacket decode(FriendlyByteBuf buf) {
        return new ClaimDayPacket(buf.readInt());
    }

    public static void handle(ClaimDayPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            int day = msg.day;
            // Validaciones (las mismas que muestra la GUI)
            if (!AdventProgress.isDecember()) {
                return;
            }
            if (day < 1 || day > 31 || day > AdventProgress.today()) {
                return;
            }
            if (AdventProgress.isClaimed(player, day)) {
                return;
            }
            ItemStack reward = AdventRewardsData.get(player.getServer().overworld()).getReward(day);
            if (reward.isEmpty()) {
                return;
            }
            // Entrega una copia (al inventario, o al suelo si está lleno)
            ItemStack copy = reward.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
            AdventProgress.setClaimed(player, day);
            // VFX + sonido en el jugador
            ServerLevel level = player.serverLevel();
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    player.getX(), player.getY() + 1.2, player.getZ(),
                    30, 0.6, 0.6, 0.6, 0.25);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        });
        ctx.get().setPacketHandled(true);
    }
}
