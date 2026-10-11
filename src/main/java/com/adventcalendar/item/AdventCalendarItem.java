package com.adventcalendar.item;

import com.adventcalendar.network.CalendarActionPacket;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.network.ServerPacketHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Hand held version of the calendar: right click anywhere to open the GUI, so players do not
 * have to walk to the NPC (they still can, of course).
 */
public class AdventCalendarItem extends Item {
    public AdventCalendarItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            ServerPacketHandler.handleAction(serverPlayer,
                    new CalendarActionPacket(CalendarActionPacket.OPEN_CALENDAR, 0));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.adventcalendar.advent_calendar.tooltip1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.adventcalendar.advent_calendar.tooltip2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
