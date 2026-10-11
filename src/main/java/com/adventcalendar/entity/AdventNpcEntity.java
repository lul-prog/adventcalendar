package com.adventcalendar.entity;

import com.adventcalendar.data.AdventProgress;
import com.adventcalendar.data.AdventRewardsData;
import com.adventcalendar.item.ControlWandItem;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.network.OpenCalendarPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/**
 * NPC del calendario de adviento: inmóvil, sin IA y con skin configurable.
 * Se invoca con: /summon adventcalendar:advent_npc
 * Se configura con el mando de control (shift + clic derecho).
 */
public class AdventNpcEntity extends Mob {
    /** Skin configurada: URL http(s) o nombre de usuario de Minecraft. Se sincroniza a los clientes. */
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.STRING);
    /** Color del nombre (nombre del enum ChatFormatting, ej. "GOLD"). */
    private static final EntityDataAccessor<String> NAME_COLOR =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.STRING);
    /** Espejo del flag vanilla de brillo, para que la GUI del mando lo lea en el cliente. */
    private static final EntityDataAccessor<Boolean> GLOW =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** Partículas ambientales alrededor del NPC. */
    private static final EntityDataAccessor<Boolean> AMBIENT_PARTICLES =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** Espejo del flag vanilla de invulnerabilidad, para que la GUI del mando lo lea en el cliente. */
    private static final EntityDataAccessor<Boolean> INVULNERABLE_SYNC =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);

    public AdventNpcEntity(EntityType<AdventNpcEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SKIN, "");
        this.entityData.define(NAME_COLOR, "WHITE");
        this.entityData.define(GLOW, false);
        this.entityData.define(AMBIENT_PARTICLES, false);
        this.entityData.define(INVULNERABLE_SYNC, false);
    }

    public String getSkin() {
        return this.entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        this.entityData.set(SKIN, skin == null ? "" : skin);
    }

    public String getNameColor() {
        return this.entityData.get(NAME_COLOR);
    }

    public boolean getGlow() {
        return this.entityData.get(GLOW);
    }

    public boolean getAmbientParticles() {
        return this.entityData.get(AMBIENT_PARTICLES);
    }

    public boolean getInvulnerableSync() {
        return this.entityData.get(INVULNERABLE_SYNC);
    }

    /**
     * Aplica la configuración enviada desde la GUI del mando (ya validada en el servidor).
     */
    public void applyConfig(String name, String colorName, boolean glow, boolean particles,
                            boolean invulnerable, String skin) {
        // Nombre + color (vacío = sin nombre visible)
        if (name == null || name.isBlank()) {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        } else {
            ChatFormatting color;
            try {
                color = ChatFormatting.valueOf(colorName);
            } catch (IllegalArgumentException ex) {
                color = ChatFormatting.WHITE;
            }
            this.setCustomName(Component.literal(name).withStyle(color));
            this.setCustomNameVisible(true);
        }
        this.entityData.set(NAME_COLOR, colorName == null ? "WHITE" : colorName);
        // Brillo (flag vanilla, persiste en NBT solo)
        this.setGlowingTag(glow);
        this.entityData.set(GLOW, glow);
        // Partículas ambientales
        this.entityData.set(AMBIENT_PARTICLES, particles);
        // Inmortal (flag vanilla, persiste en NBT solo)
        this.setInvulnerable(invulnerable);
        this.entityData.set(INVULNERABLE_SYNC, invulnerable);
        // Skin (usa el sistema existente)
        this.setSkin(skin);
    }

    @Override
    public void tick() {
        super.tick();
        // Partículas ambientales, solo en el servidor y cada 10 ticks
        if (!this.level().isClientSide && this.entityData.get(AMBIENT_PARTICLES) && this.tickCount % 10 == 0) {
            ServerLevel sl = (ServerLevel) this.level();
            sl.sendParticles(ParticleTypes.END_ROD,
                    this.getX(), this.getY() + 1.0, this.getZ(),
                    4, 0.5, 0.9, 0.5, 0.02);
        }
    }

    /** Los datos del mando deben sobrevivir reinicios del mundo: se guardan en el NBT de la entidad. */
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Skin", getSkin());
        tag.putString("NameColor", getNameColor());
        tag.putBoolean("AmbientParticles", getAmbientParticles());
        // "Glowing" e "Invulnerable" los guarda el propio vanilla.
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkin(tag.getString("Skin"));
        this.entityData.set(NAME_COLOR, tag.getString("NameColor"));
        this.entityData.set(AMBIENT_PARTICLES, tag.getBoolean("AmbientParticles"));
        // El vanilla ya cargó "Glowing" e "Invulnerable": espejarlos para la GUI.
        this.entityData.set(GLOW, this.isCurrentlyGlowing());
        this.entityData.set(INVULNERABLE_SYNC, this.isInvulnerable());
    }

    /** El NPC no debe desaparecer cuando el jugador se aleja. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    /**
     * Clic derecho: con el mando en la mano, el mando decide (seleccionar/configurar)
     * y no se abre el calendario. Sin el mando, el servidor pide al cliente que abra la GUI.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof ControlWandItem) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp) {
            int claimed = AdventProgress.getMask(player);
            int rewards = AdventRewardsData.get(sp.getServer().overworld()).getRewardsMask();
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new OpenCalendarPacket(claimed, rewards));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }
}
