package com.adventcalendar.network;

import com.adventcalendar.AdventCalendar;
import com.adventcalendar.AdventConfig;
import com.adventcalendar.ModEntities;
import com.adventcalendar.data.AdventDataManager;
import com.adventcalendar.data.ClaimResult;
import com.adventcalendar.data.Reward;
import com.adventcalendar.entity.AdventNpcEntity;
import com.adventcalendar.util.DateUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.time.LocalDate;

/** Server side logic behind every client -> server packet of the mod. */
public final class ServerPacketHandler {
    private ServerPacketHandler() {
    }

    private static Component msg(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static void deny(ServerPlayer player) {
        player.sendSystemMessage(msg("adventcalendar.msg.op_required").withStyle(ChatFormatting.RED));
    }

    private static int clampDay(int day) {
        return Math.max(1, Math.min(day, DateUtil.daysInCalendar()));
    }

    // ------------------------------------------------------------------ player GUI

    public static void handleAction(ServerPlayer player, CalendarActionPacket packet) {
        AdventDataManager data = AdventDataManager.get();
        switch (packet.action) {
            case CalendarActionPacket.REQUEST_SYNC -> data.syncTo(player);
            case CalendarActionPacket.CLAIM -> {
                int day = clampDay(packet.day);
                Reward reward = data.rewards().get(day);
                ItemStack preview = reward == null ? ItemStack.EMPTY : reward.item().copy();
                ClaimResult result = data.tryClaim(player, day, false);
                data.sendClaimFeedback(player, day, result, preview);
                data.syncTo(player);
            }
            case CalendarActionPacket.RETRIEVE_STOCKING -> {
                int count = data.retrieveStocking(player);
                player.sendSystemMessage(count > 0
                        ? msg("adventcalendar.msg.pending_given", count).withStyle(ChatFormatting.GOLD)
                        : msg("adventcalendar.msg.pending_none").withStyle(ChatFormatting.GRAY));
                data.syncTo(player);
            }
            case CalendarActionPacket.OPEN_CALENDAR -> {
                data.syncTo(player);
                ModNetwork.sendTo(player, new OpenGuiPacket(OpenGuiPacket.GUI_CALENDAR));
            }
            case CalendarActionPacket.OPEN_ADMIN -> openAdminGui(player);
            case CalendarActionPacket.OPEN_DEBUG -> openDebugGui(player);
            default -> AdventCalendar.LOGGER.debug("Unknown calendar action {} from {}", packet.action, player.getName());
        }
    }

    public static void openAdminGui(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            deny(player);
            return;
        }
        AdventDataManager data = AdventDataManager.get();
        data.syncTo(player);
        ModNetwork.sendTo(player, new OpenGuiPacket(OpenGuiPacket.GUI_ADMIN));
    }

    public static void openDebugGui(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            deny(player);
            return;
        }
        AdventDataManager data = AdventDataManager.get();
        data.syncTo(player);
        ModNetwork.sendTo(player, new OpenGuiPacket(OpenGuiPacket.GUI_DEBUG));
    }

    // ------------------------------------------------------------------ admin GUI

    public static void handleAdminEdit(ServerPlayer player, AdminEditPacket packet) {
        if (!player.hasPermissions(2)) {
            deny(player);
            return;
        }
        AdventDataManager data = AdventDataManager.get();
        int day = clampDay(packet.day);
        switch (packet.action) {
            case AdminEditPacket.SET_DAY -> {
                Reward reward = new Reward();
                reward.setItem(packet.stack);
                reward.setCommand(packet.command.trim());
                reward.setLabel(packet.label.trim());
                reward.setHidden(packet.hidden);
                if (reward.isEmpty()) {
                    data.clearReward(day);
                    player.sendSystemMessage(msg("adventcalendar.admin.day.cleared", day)
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    data.setReward(day, reward);
                    player.sendSystemMessage(msg("adventcalendar.admin.day.set", day)
                            .withStyle(ChatFormatting.GREEN));
                }
                data.saveAsync();
                data.syncAll();
            }
            case AdminEditPacket.CLEAR_DAY -> {
                data.clearReward(day);
                player.sendSystemMessage(msg("adventcalendar.admin.day.cleared", day).withStyle(ChatFormatting.GRAY));
                data.saveAsync();
                data.syncAll();
            }
            case AdminEditPacket.CLEAR_ALL -> {
                data.clearAllRewards();
                data.saveAsync();
                data.syncAll();
                player.sendSystemMessage(msg("adventcalendar.admin.button.clearall").withStyle(ChatFormatting.YELLOW));
            }
            case AdminEditPacket.FILL_SAMPLE -> {
                int created = data.fillSampleRewards();
                data.saveAsync();
                data.syncAll();
                player.sendSystemMessage(msg("adventcalendar.admin.button.sample")
                        .append(" (" + created + ")").withStyle(ChatFormatting.GREEN));
            }
            case AdminEditPacket.SAVE -> {
                data.saveNow("admin GUI");
                player.sendSystemMessage(msg("adventcalendar.msg.saved").withStyle(ChatFormatting.GREEN));
            }
            case AdminEditPacket.RELOAD -> {
                data.reload();
                player.sendSystemMessage(msg("adventcalendar.msg.reloaded").withStyle(ChatFormatting.GREEN));
            }
            case AdminEditPacket.SPAWN_NPC -> spawnNpc(player);
            case AdminEditPacket.TAKE_DAY -> {
                Reward reward = data.rewards().get(day);
                if (reward == null || reward.item().isEmpty()) {
                    return;
                }
                data.deliver(player, reward.item().copy());
                player.sendSystemMessage(msg("adventcalendar.msg.grabbed", day).withStyle(ChatFormatting.GOLD));
            }
            default -> AdventCalendar.LOGGER.debug("Unknown admin action {} from {}", packet.action, player.getName());
        }
    }

    private static void spawnNpc(ServerPlayer player) {
        AdventNpcEntity npc = ModEntities.ADVENT_NPC.get().create(player.serverLevel());
        if (npc == null) {
            return;
        }
        npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
        npc.snapRotation(player.getYRot() + 180.0F, 0.0F);
        npc.setSkin(player.getGameProfile().getName());
        npc.setCustomName(Component.literal("Advent Calendar").withStyle(ChatFormatting.GOLD));
        npc.setCustomNameVisible(true);
        player.serverLevel().addFreshEntity(npc);
        player.sendSystemMessage(msg("adventcalendar.msg.npc_spawned").withStyle(ChatFormatting.GREEN));
    }

    // ------------------------------------------------------------------ debug GUI

    public static void handleDebugAction(ServerPlayer player, DebugActionPacket packet) {
        if (!player.hasPermissions(2)) {
            deny(player);
            return;
        }
        AdventDataManager data = AdventDataManager.get();
        switch (packet.action) {
            case DebugActionPacket.SET_DATE_TEXT -> {
                if (!AdventConfig.ALLOW_DATE_OVERRIDE.get()) {
                    return;
                }
                LocalDate date = DateUtil.parse(packet.text);
                if (date == null) {
                    player.sendSystemMessage(Component.literal("Could not read the date '" + packet.text + "'")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
                data.setDateOverride(date);
                player.sendSystemMessage(msg("adventcalendar.msg.date_override", DateUtil.format(date))
                        .withStyle(ChatFormatting.AQUA));
            }
            case DebugActionPacket.SHIFT_DAYS -> {
                if (!AdventConfig.ALLOW_DATE_OVERRIDE.get()) {
                    return;
                }
                LocalDate date = DateUtil.now().plusDays(packet.value);
                data.setDateOverride(date);
                player.sendSystemMessage(msg("adventcalendar.msg.date_override", DateUtil.format(date))
                        .withStyle(ChatFormatting.AQUA));
            }
            case DebugActionPacket.SET_UNLOCK_DAY -> {
                if (!AdventConfig.ALLOW_DATE_OVERRIDE.get()) {
                    return;
                }
                int day = clampDay(packet.value);
                LocalDate date = LocalDate.of(DateUtil.now().getYear(), 12, day);
                data.setDateOverride(date);
                player.sendSystemMessage(msg("adventcalendar.msg.date_override", DateUtil.format(date))
                        .withStyle(ChatFormatting.AQUA));
            }
            case DebugActionPacket.CLEAR_DATE -> {
                data.setDateOverride(null);
                player.sendSystemMessage(msg("adventcalendar.msg.date_override_cleared")
                        .withStyle(ChatFormatting.AQUA));
            }
            case DebugActionPacket.SET_PREVIEW -> {
                data.setPreview(player.getUUID(), packet.value != 0);
                data.syncTo(player);
                player.sendSystemMessage(msg(packet.value != 0
                        ? "adventcalendar.msg.preview_on" : "adventcalendar.msg.preview_off")
                        .withStyle(ChatFormatting.AQUA));
            }
            case DebugActionPacket.RESET_MY_CLAIMS -> {
                data.resetClaims(player.getUUID());
                data.saveAsync();
                data.syncTo(player);
                player.sendSystemMessage(msg("adventcalendar.msg.reset", 1).withStyle(ChatFormatting.YELLOW));
            }
            case DebugActionPacket.RESET_ALL_CLAIMS -> {
                int removed = data.resetAllClaims();
                data.saveAsync();
                data.syncAll();
                player.sendSystemMessage(msg("adventcalendar.msg.reset", removed).withStyle(ChatFormatting.YELLOW));
            }
            case DebugActionPacket.FORCE_CLAIM -> {
                int day = clampDay(packet.value);
                Reward reward = data.rewards().get(day);
                ItemStack preview = reward == null ? ItemStack.EMPTY : reward.item().copy();
                ClaimResult result = data.tryClaim(player, day, true);
                data.sendClaimFeedback(player, day, result, preview);
                data.syncTo(player);
            }
            case DebugActionPacket.GRANT_ALL -> {
                int granted = 0;
                for (int day = 1; day <= DateUtil.daysInCalendar(); day++) {
                    Reward reward = data.rewards().get(day);
                    if (reward == null || reward.isEmpty()) {
                        continue;
                    }
                    data.grant(player, reward);
                    granted++;
                }
                data.syncTo(player);
                player.sendSystemMessage(msg("adventcalendar.msg.granted_all")
                        .append(" (" + granted + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            case DebugActionPacket.REQUEST_SYNC -> data.syncTo(player);
            default -> AdventCalendar.LOGGER.debug("Unknown debug action {} from {}", packet.action, player.getName());
        }
    }

    @Nullable
    public static AdventNpcEntity findNpc(ServerPlayer player, int entityId) {
        if (player.serverLevel().getEntity(entityId) instanceof AdventNpcEntity npc) {
            return npc;
        }
        return null;
    }
}
