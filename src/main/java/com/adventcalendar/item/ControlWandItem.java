package com.adventcalendar.item;

import com.adventcalendar.entity.AdventNpcEntity;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.network.OpenNpcConfigPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Mando de control del NPC de adviento (requiere operador).
 * - Clic derecho a un NPC: lo selecciona (guarda su UUID en el NBT del ítem).
 * - Clic derecho a un bloque: teletransporta el NPC seleccionado encima del bloque.
 * - Shift + clic derecho (al NPC o al aire): abre la interfaz de configuración.
 */
public class ControlWandItem extends Item {
    private static final String TAG_SELECTED = "SelectedNpc";

    public ControlWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Mando de control");
    }

    /** Permiso de operador (nivel 2), verificado en el servidor. */
    private static boolean hasOp(Player player) {
        return player instanceof ServerPlayer sp && sp.hasPermissions(2);
    }

    private static void denyOp(ServerPlayer sp) {
        sp.displayClientMessage(Component.literal("Necesitas ser operador para usar el mando."), true);
    }

    /** NPC objetivo: el seleccionado en el mando, o el más cercano en 5 bloques si no hay. */
    private static AdventNpcEntity findTarget(ServerPlayer sp, ItemStack stack) {
        ServerLevel level = sp.serverLevel();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_SELECTED)) {
            UUID uuid = tag.getUUID(TAG_SELECTED);
            Entity e = level.getEntity(uuid);
            if (e instanceof AdventNpcEntity npc) {
                return npc;
            }
        }
        AdventNpcEntity best = null;
        double bestDist = 5.0;
        for (AdventNpcEntity npc : level.getEntitiesOfClass(AdventNpcEntity.class,
                sp.getBoundingBox().inflate(5.0), n -> true)) {
            double d = npc.distanceTo(sp);
            if (d < bestDist) {
                bestDist = d;
                best = npc;
            }
        }
        return best;
    }

    private static InteractionResult openConfig(ServerPlayer sp, ItemStack stack) {
        AdventNpcEntity npc = findTarget(sp, stack);
        if (npc == null) {
            sp.displayClientMessage(Component.literal("No hay NPC: selecciona uno con clic derecho o acércate a 5 bloques."), true);
            return InteractionResult.FAIL;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                new OpenNpcConfigPacket(npc.getId()));
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * Clic derecho sobre el NPC: con shift abre su configuración, sin shift lo selecciona.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof AdventNpcEntity npc) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        ServerPlayer sp = (ServerPlayer) player;
        if (!hasOp(player)) {
            denyOp(sp);
            return InteractionResult.FAIL;
        }
        if (player.isCrouching()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new OpenNpcConfigPacket(npc.getId()));
        } else {
            stack.getOrCreateTag().putUUID(TAG_SELECTED, npc.getUUID());
            sp.displayClientMessage(Component.literal("NPC seleccionado."), true);
        }
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * Clic derecho a un bloque: con shift abre la configuración;
     * sin shift teletransporta el NPC seleccionado encima del bloque.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || ctx.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        ServerPlayer sp = (ServerPlayer) player;
        if (!hasOp(player)) {
            denyOp(sp);
            return InteractionResult.FAIL;
        }
        if (player.isCrouching()) {
            return openConfig(sp, ctx.getItemInHand());
        }
        ItemStack stack = ctx.getItemInHand();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_SELECTED)) {
            sp.displayClientMessage(Component.literal("Primero selecciona un NPC con clic derecho."), true);
            return InteractionResult.FAIL;
        }
        ServerLevel level = sp.serverLevel();
        Entity e = level.getEntity(tag.getUUID(TAG_SELECTED));
        if (!(e instanceof AdventNpcEntity npc)) {
            tag.remove(TAG_SELECTED);
            sp.displayClientMessage(Component.literal("El NPC seleccionado ya no existe."), true);
            return InteractionResult.FAIL;
        }
        BlockPos pos = ctx.getClickedPos();
        npc.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, npc.getYRot(), npc.getXRot());
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * Shift + clic derecho al aire: abre la configuración del NPC seleccionado
     * (o del más cercano en 5 bloques).
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isCrouching()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        ServerPlayer sp = (ServerPlayer) player;
        if (!hasOp(player)) {
            denyOp(sp);
            return InteractionResultHolder.fail(stack);
        }
        InteractionResult r = openConfig(sp, stack);
        return r.consumesAction()
                ? InteractionResultHolder.sidedSuccess(stack, false)
                : InteractionResultHolder.fail(stack);
    }
}
