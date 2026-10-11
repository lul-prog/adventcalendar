package com.adventcalendar.item;

import com.adventcalendar.entity.AdventNpcEntity;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.network.OpenGuiPacket;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The operator tool of the mod.
 *
 * <ul>
 *     <li>right click an Advent NPC - select it (its UUID is stored in the wand)</li>
 *     <li>shift + right click - open the NPC configuration GUI</li>
 *     <li>right click a block - move the selected NPC on top of that block</li>
 * </ul>
 */
public class ControlWandItem extends Item {
    private static final String TAG_SELECTED = "SelectedNpc";
    private static final double SEARCH_RADIUS = 12.0D;

    public ControlWandItem() {
        super(new Item.Properties().stacksTo(1));
    }

    private static boolean hasOp(Player player) {
        return player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2);
    }

    private static void denyOp(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("adventcalendar.msg.op_required")
                .withStyle(ChatFormatting.RED));
    }

    /** The selected NPC, or the closest one within {@link #SEARCH_RADIUS}. */
    @Nullable
    private static AdventNpcEntity findTarget(ServerPlayer player, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_SELECTED)) {
            Entity entity = level.getEntity(tag.getUUID(TAG_SELECTED));
            if (entity instanceof AdventNpcEntity npc) {
                return npc;
            }
            tag.remove(TAG_SELECTED);
        }
        List<AdventNpcEntity> nearby = level.getEntitiesOfClass(AdventNpcEntity.class,
                player.getBoundingBox().inflate(SEARCH_RADIUS));
        AdventNpcEntity closest = null;
        double best = Double.MAX_VALUE;
        for (AdventNpcEntity npc : nearby) {
            double distance = npc.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                closest = npc;
            }
        }
        return closest;
    }

    private static InteractionResult openConfig(ServerPlayer player, ItemStack stack) {
        AdventNpcEntity npc = findTarget(player, stack);
        if (npc == null) {
            player.sendSystemMessage(Component.translatable("adventcalendar.msg.npc_missing")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ModNetwork.sendTo(player, new OpenGuiPacket(OpenGuiPacket.GUI_NPC_CONFIG, npc.getId()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, net.minecraft.world.entity.LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof AdventNpcEntity npc) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (!hasOp(player)) {
            denyOp(serverPlayer);
            return InteractionResult.FAIL;
        }
        if (player.isCrouching()) {
            ModNetwork.sendTo(serverPlayer, new OpenGuiPacket(OpenGuiPacket.GUI_NPC_CONFIG, npc.getId()));
        } else {
            stack.getOrCreateTag().putUUID(TAG_SELECTED, npc.getUUID());
            serverPlayer.sendSystemMessage(Component.translatable("adventcalendar.msg.wand_selected")
                    .withStyle(ChatFormatting.GREEN));
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (!hasOp(player)) {
            denyOp(serverPlayer);
            return InteractionResult.FAIL;
        }
        if (player.isCrouching()) {
            return openConfig(serverPlayer, context.getItemInHand());
        }
        AdventNpcEntity npc = findTarget(serverPlayer, context.getItemInHand());
        if (npc == null) {
            serverPlayer.sendSystemMessage(Component.translatable("adventcalendar.msg.wand_no_selection")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        net.minecraft.core.BlockPos pos = context.getClickedPos().above();
        npc.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, npc.getYRot(), npc.getXRot());
        npc.snapRotation(npc.getYRot(), 0.0F);
        context.getLevel().playSound(null, npc.getX(), npc.getY(), npc.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        serverPlayer.sendSystemMessage(Component.translatable("adventcalendar.msg.wand_moved")
                .withStyle(ChatFormatting.GREEN));
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isCrouching()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        ServerPlayer serverPlayer = (ServerPlayer) player;
        if (!hasOp(player)) {
            denyOp(serverPlayer);
            return InteractionResultHolder.fail(stack);
        }
        InteractionResult result = openConfig(serverPlayer, stack);
        return result == InteractionResult.SUCCESS
                ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.adventcalendar.control_wand.tooltip1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.adventcalendar.control_wand.tooltip2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
