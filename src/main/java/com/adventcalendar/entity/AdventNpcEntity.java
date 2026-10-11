package com.adventcalendar.entity;

import com.adventcalendar.AdventConfig;
import com.adventcalendar.ModSounds;
import com.adventcalendar.data.AdventDataManager;
import com.adventcalendar.item.ControlWandItem;
import com.adventcalendar.network.CalendarActionPacket;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.util.DateUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Advent Calendar NPC: a stationary mob rendered with the player model, so it can wear
 * any Minecraft skin (downloaded from a URL or resolved from a username) including slim ones.
 *
 * <p>Everything the renderer and the configuration GUI need is mirrored into synced entity
 * data, which means client and server always agree. The entity is saved with the world and
 * remembers its own configuration.</p>
 */
public class AdventNpcEntity extends Mob {
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN_MODEL =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NAME_COLOR =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> GLOW =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AMBIENT_PARTICLES =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> INVULNERABLE_SYNC =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LOOK_AT_PLAYERS =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AMBIENT_SOUND =
            SynchedEntityData.defineId(AdventNpcEntity.class, EntityDataSerializers.BOOLEAN);

    /** Server side only: throttle for the "welcome" action bar line. */
    private final Map<UUID, Long> greeted = new HashMap<>();

    public AdventNpcEntity(EntityType<AdventNpcEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SKIN, "");
        this.entityData.define(SKIN_MODEL, SkinModel.AUTO.name());
        this.entityData.define(NAME_COLOR, "GOLD");
        this.entityData.define(GLOW, false);
        this.entityData.define(AMBIENT_PARTICLES, true);
        this.entityData.define(INVULNERABLE_SYNC, true);
        this.entityData.define(LOOK_AT_PLAYERS, AdventConfig.NPC_LOOK_AT_PLAYERS.get());
        this.entityData.define(AMBIENT_SOUND, AdventConfig.NPC_AMBIENT_SOUND.get());
    }

    // ------------------------------------------------------------------ synced data

    public String getSkin() {
        return this.entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        this.entityData.set(SKIN, skin == null ? "" : skin.trim());
    }

    public SkinModel getSkinModel() {
        return SkinModel.byName(this.entityData.get(SKIN_MODEL));
    }

    public void setSkinModel(SkinModel model) {
        this.entityData.set(SKIN_MODEL, (model == null ? SkinModel.AUTO : model).name());
    }

    /** True when the NPC should be rendered with the slim (3 pixel arms) player model. */
    public boolean isSlim() {
        return getSkinModel() == SkinModel.SLIM;
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

    public boolean getLookAtPlayers() {
        return this.entityData.get(LOOK_AT_PLAYERS);
    }

    /**
     * Named {@code isAmbientSoundEnabled} on purpose: {@code Mob#getAmbientSound()} already
     * exists in vanilla and returns a {@code SoundEvent}.
     */
    public boolean isAmbientSoundEnabled() {
        return this.entityData.get(AMBIENT_SOUND);
    }

    // ------------------------------------------------------------------ configuration

    /** Applies the values sent by the NPC configuration GUI (already permission checked). */
    public void applyConfig(String name, String colorName, boolean glow, boolean particles,
                            boolean invulnerable, String skin, String skinModel,
                            boolean lookAtPlayers, boolean ambientSound) {
        ChatFormatting color = parseColor(colorName);
        if (name == null || name.isBlank()) {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        } else {
            this.setCustomName(Component.literal(name).withStyle(color));
            this.setCustomNameVisible(true);
        }
        this.entityData.set(NAME_COLOR, color == null ? "WHITE" : color.name());
        this.setGlowingTag(glow);
        this.entityData.set(GLOW, glow);
        this.entityData.set(AMBIENT_PARTICLES, particles);
        this.setInvulnerable(invulnerable);
        this.entityData.set(INVULNERABLE_SYNC, invulnerable);
        this.entityData.set(LOOK_AT_PLAYERS, lookAtPlayers);
        this.entityData.set(AMBIENT_SOUND, ambientSound);
        setSkin(skin);
        setSkinModel(SkinModel.byName(skinModel));
    }

    @Nullable
    public static ChatFormatting parseColor(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return ChatFormatting.WHITE;
        }
        try {
            ChatFormatting formatting = ChatFormatting.valueOf(name.toUpperCase());
            return formatting.isFormat() ? ChatFormatting.WHITE : formatting;
        } catch (IllegalArgumentException e) {
            return ChatFormatting.WHITE;
        }
    }

    /** Makes the NPC face exactly the given direction; used by the spawn command/GUI. */
    public void snapRotation(float yaw, float pitch) {
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.setXRot(pitch);
        this.xRotO = pitch;
        this.setYHeadRot(yaw);
        this.yHeadRotO = yaw;
        this.yBodyRot = yaw;
        this.yBodyRotO = yaw;
    }

    // ------------------------------------------------------------------ behaviour

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (this.getAmbientParticles() && this.tickCount % 8 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    this.getX() + (this.random.nextDouble() - 0.5D) * 0.8D,
                    this.getY() + 0.4D + this.random.nextDouble() * 1.6D,
                    this.getZ() + (this.random.nextDouble() - 0.5D) * 0.8D,
                    1, 0.0D, 0.02D, 0.0D, 0.01D);
        }
        if (this.isAmbientSoundEnabled() && this.tickCount % 240 == 0) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.NOTIFY.get(), SoundSource.NEUTRAL, 0.35F, 1.6F);
        }
        if (this.tickCount % 10 == 0 && (this.getLookAtPlayers() || AdventConfig.NOTIFY_NEW_DOORS.get())) {
            lookAtNearestPlayer(level);
        }
    }

    private void lookAtNearestPlayer(ServerLevel level) {
        Player nearest = level.getNearestPlayer(this, 10.0D);
        if (nearest == null || !nearest.isAlive()) {
            return;
        }
        if (this.getLookAtPlayers()) {
            Vec3 target = nearest.getEyePosition();
            double dx = target.x - this.getX();
            double dz = target.z - this.getZ();
            double dy = target.y - (this.getY() + 1.6D);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            this.setYRot(yaw);
            this.setYHeadRot(yaw);
            this.yBodyRot = yaw;
            this.setXRot(pitch);
        }
        if (nearest instanceof ServerPlayer player && greet(player)) {
            int unlocked = Math.min(DateUtil.unlockedThrough(), DateUtil.daysInCalendar());
            if (unlocked > 0) {
                player.displayClientMessage(Component.translatable("adventcalendar.msg.unlocked", unlocked)
                        .withStyle(ChatFormatting.GOLD), true);
            }
            player.playNotifySound(ModSounds.NOTIFY.get(), SoundSource.NEUTRAL, 0.4F, 1.5F);
        }
    }

    /** Returns true at most once every ten minutes per player. */
    private boolean greet(ServerPlayer player) {
        long now = this.level().getGameTime();
        Long last = greeted.get(player.getUUID());
        if (last != null && now - last < 12000L) {
            return false;
        }
        greeted.put(player.getUUID(), now);
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        return this.getInvulnerableSync() || super.isInvulnerableTo(source);
    }

    /** Right-click opens the calendar (the control wand keeps its own interaction). */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof ControlWandItem) {
            return InteractionResult.PASS; // handled by the wand
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
            // Reuse the exact same path as the GUI button: server sends a sync + open packet.
            com.adventcalendar.network.ServerPacketHandler.handleAction(serverPlayer,
                    new CalendarActionPacket(CalendarActionPacket.OPEN_CALENDAR, 0));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Skin", getSkin());
        tag.putString("SkinModel", getSkinModel().name());
        tag.putString("NameColor", getNameColor());
        tag.putBoolean("AmbientParticles", getAmbientParticles());
        tag.putBoolean("LookAtPlayers", getLookAtPlayers());
        tag.putBoolean("AmbientSound", isAmbientSoundEnabled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkin(tag.getString("Skin"));
        setSkinModel(SkinModel.byName(tag.getString("SkinModel")));
        this.entityData.set(NAME_COLOR, tag.contains("NameColor") ? tag.getString("NameColor") : "GOLD");
        this.entityData.set(AMBIENT_PARTICLES,
                !tag.contains("AmbientParticles") || tag.getBoolean("AmbientParticles"));
        this.entityData.set(LOOK_AT_PLAYERS,
                !tag.contains("LookAtPlayers") || tag.getBoolean("LookAtPlayers"));
        this.entityData.set(AMBIENT_SOUND, tag.getBoolean("AmbientSound"));
        this.entityData.set(GLOW, this.isCurrentlyGlowing());
        this.entityData.set(INVULNERABLE_SYNC, this.isInvulnerable());
    }

    /** Available models for the skin. AUTO keeps the classic model (slim skins look a bit wide). */
    public enum SkinModel {
        AUTO,
        CLASSIC,
        SLIM;

        public static SkinModel byName(@Nullable String name) {
            if (name == null) {
                return AUTO;
            }
            for (SkinModel model : values()) {
                if (model.name().equalsIgnoreCase(name.trim())) {
                    return model;
                }
            }
            return AUTO;
        }

        public SkinModel next() {
            SkinModel[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
