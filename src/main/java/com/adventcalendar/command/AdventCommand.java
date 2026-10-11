package com.adventcalendar.command;

import com.adventcalendar.AdventConfig;
import com.adventcalendar.ModEntities;
import com.adventcalendar.data.AdventDataManager;
import com.adventcalendar.data.ClaimResult;
import com.adventcalendar.data.Reward;
import com.adventcalendar.entity.AdventNpcEntity;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.network.OpenGuiPacket;
import com.adventcalendar.network.ServerPacketHandler;
import com.adventcalendar.util.DateUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Every command of the mod, available as {@code /advent} and {@code /adventcalendar}.
 *
 * <p>Read only helpers ({@code gui}, {@code list}, {@code date}, {@code info}) are open to
 * everyone, everything that changes data requires permission level 2.</p>
 */
public final class AdventCommand {
    private AdventCommand() {
    }

    public static void register(net.minecraftforge.event.RegisterCommandsEvent event) {
        context = event.getBuildContext();
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(build("advent"));
        dispatcher.register(build("adventcalendar"));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .then(Commands.literal("help").executes(AdventCommand::help))
                .then(Commands.literal("gui").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerPacketHandler.handleAction(player,
                            new com.adventcalendar.network.CalendarActionPacket(
                                    com.adventcalendar.network.CalendarActionPacket.OPEN_CALENDAR, 0));
                    return 1;
                }))
                .then(Commands.literal("admin").requires(op()).executes(ctx -> {
                    ServerPacketHandler.openAdminGui(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("rewards").requires(op()).executes(ctx -> {
                    ServerPacketHandler.openAdminGui(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("debug").requires(op()).executes(ctx -> {
                    ServerPacketHandler.openDebugGui(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("info").executes(AdventCommand::info))
                .then(Commands.literal("date").executes(AdventCommand::showDate)
                        .then(Commands.literal("set").requires(op())
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(ctx -> setDate(ctx,
                                                StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("day").requires(op())
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 31))
                                        .executes(ctx -> setDay(ctx,
                                                IntegerArgumentType.getInteger(ctx, "day")))))
                        .then(Commands.literal("clear").requires(op()).executes(AdventCommand::clearDate)))
                .then(Commands.literal("preview").requires(op())
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setPreview(ctx, BoolArgumentType.getBool(ctx, "value")))))
                .then(Commands.literal("spawn").requires(op())
                        .executes(ctx -> spawn(ctx, "", "", "AUTO"))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"), "", "AUTO"))
                                .then(Commands.argument("skin", StringArgumentType.string())
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"),
                                                StringArgumentType.getString(ctx, "skin"), "AUTO"))
                                        .then(Commands.argument("model", StringArgumentType.word())
                                                .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "skin"),
                                                        StringArgumentType.getString(ctx, "model")))))))
                .then(Commands.literal("skin").requires(op())
                        .then(Commands.argument("value", StringArgumentType.string())
                                .executes(ctx -> setSkin(ctx, StringArgumentType.getString(ctx, "value"), null))
                                .then(Commands.argument("model", StringArgumentType.word())
                                        .executes(ctx -> setSkin(ctx, StringArgumentType.getString(ctx, "value"),
                                                StringArgumentType.getString(ctx, "model"))))))
                .then(Commands.literal("npc").requires(op())
                        .then(Commands.literal("config").executes(AdventCommand::openNpcConfig)))
                .then(Commands.literal("setreward").requires(op())
                        .then(dayArgument()
                                .executes(ctx -> setRewardFromHand(ctx, IntegerArgumentType.getInteger(ctx, "day")))
                                .then(Commands.literal("command")
                                        .then(Commands.argument("command", StringArgumentType.greedyString())
                                                .executes(ctx -> setRewardCommand(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "day"),
                                                        StringArgumentType.getString(ctx, "command")))))
                                .then(Commands.argument("item", ItemArgument.item(ctxOfBuilder()))
                                        .executes(ctx -> setRewardFromItem(ctx,
                                                IntegerArgumentType.getInteger(ctx, "day"),
                                                ItemArgument.getItem(ctx, "item"))))
                                .then(Commands.argument("item", ItemArgument.item(ctxOfBuilder()))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> setRewardFromItemCount(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "day"),
                                                        ItemArgument.getItem(ctx, "item"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("clearreward").requires(op())
                        .then(dayArgument().executes(ctx -> clearReward(ctx, IntegerArgumentType.getInteger(ctx, "day")))))
                .then(Commands.literal("clearall").requires(op()).executes(AdventCommand::clearAll))
                .then(Commands.literal("sample").requires(op()).executes(AdventCommand::sample))
                .then(Commands.literal("reload").requires(op()).executes(ctx -> {
                    AdventDataManager.get().reload();
                    ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.reloaded"), true);
                    return 1;
                }))
                .then(Commands.literal("save").requires(op()).executes(ctx -> {
                    AdventDataManager.get().saveNow("command");
                    ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.saved"), true);
                    return 1;
                }))
                .then(Commands.literal("claim").requires(op())
                        .then(dayArgument().executes(ctx -> forceClaim(ctx,
                                IntegerArgumentType.getInteger(ctx, "day"), null))
                                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> forceClaim(ctx,
                                                IntegerArgumentType.getInteger(ctx, "day"),
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("grantall").requires(op()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    AdventDataManager data = AdventDataManager.get();
                    int granted = 0;
                    for (int day = 1; day <= DateUtil.daysInCalendar(); day++) {
                        Reward reward = data.rewards().get(day);
                        if (reward != null && !reward.isEmpty()) {
                            data.grant(player, reward);
                            granted++;
                        }
                    }
                    final int total = granted;
                    ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.granted_all")
                            .append(" (" + total + ")"), true);
                    return 1;
                }))
                .then(Commands.literal("reset").requires(op())
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(AdventCommand::resetPlayer))
                        .then(Commands.literal("all").executes(AdventCommand::resetAll)))
                .then(Commands.literal("list")
                        .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .requires(op())
                                .executes(ctx -> list(ctx,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player")))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> dayArgument() {
        return Commands.argument("day", IntegerArgumentType.integer(1, 31));
    }

    /** The build context of the running server, needed to parse item arguments. */
    private static net.minecraft.commands.CommandBuildContext context;

    private static net.minecraft.commands.CommandBuildContext ctxOfBuilder() {
        if (context == null) {
            throw new IllegalStateException("Advent Calendar commands are registered before the build context exists");
        }
        return context;
    }

    private static java.util.function.Predicate<CommandSourceStack> op() {
        return source -> source.hasPermission(2);
    }

    // ------------------------------------------------------------------ handlers

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("/advent gui").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("/advent spawn [name] [skin] [auto|classic|slim]")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/advent admin | /advent debug | /advent rewards")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/advent setreward <day> [<item> [count]|command <cmd>]")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/advent skin <url|username|local:file.png> [model]")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/advent date [set <date>|day <n>|clear]")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/advent claim <day> [player] | /advent reset <player>|all")
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("/advent reload | save | sample | clearall | list [player]")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        AdventDataManager data = AdventDataManager.get();
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Advent Calendar data: " + data.directory().toAbsolutePath())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Configured doors: " + data.rewards().countConfigured()
                + " | players tracked: " + data.claims().playerCount()), false);
        source.sendSuccess(() -> Component.literal("Storage mode: "
                + AdventConfig.DATA_LOCATION.get().name() + " (config/adventcalendar-common.toml)"), false);
        return 1;
    }

    private static int showDate(CommandContext<CommandSourceStack> ctx) {
        Component message = Component.translatable("adventcalendar.msg.current_date",
                DateUtil.format(), DateUtil.year(), DateUtil.unlockedThrough());
        ctx.getSource().sendSuccess(() -> message, false);
        return 1;
    }

    private static int setDate(CommandContext<CommandSourceStack> ctx, String value) {
        LocalDate date = DateUtil.parse(value);
        if (date == null) {
            ctx.getSource().sendFailure(Component.literal("Could not read the date: " + value));
            return 0;
        }
        AdventDataManager.get().setDateOverride(date);
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.date_override",
                DateUtil.format(date)), true);
        return 1;
    }

    private static int setDay(CommandContext<CommandSourceStack> ctx, int day) {
        LocalDate date = LocalDate.of(DateUtil.now().getYear(), 12, Math.min(day, 31));
        AdventDataManager.get().setDateOverride(date);
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.date_override",
                DateUtil.format(date)), true);
        return 1;
    }

    private static int clearDate(CommandContext<CommandSourceStack> ctx) {
        AdventDataManager.get().setDateOverride(null);
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.date_override_cleared"), true);
        return 1;
    }

    private static int setPreview(CommandContext<CommandSourceStack> ctx, boolean value)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AdventDataManager.get().setPreview(player.getUUID(), value);
        AdventDataManager.get().syncTo(player);
        ctx.getSource().sendSuccess(() -> Component.translatable(value
                ? "adventcalendar.msg.preview_on" : "adventcalendar.msg.preview_off"), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String name, String skin, String model)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AdventNpcEntity npc = ModEntities.ADVENT_NPC.get().create(player.serverLevel());
        if (npc == null) {
            ctx.getSource().sendFailure(Component.literal("Could not create the NPC"));
            return 0;
        }
        npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot() + 180.0F, 0.0F);
        npc.snapRotation(player.getYRot() + 180.0F, 0.0F);
        npc.applyConfig(name.isEmpty() ? "Advent Calendar" : name, "GOLD", false, true, true,
                skin, model, AdventConfig.NPC_LOOK_AT_PLAYERS.get(), AdventConfig.NPC_AMBIENT_SOUND.get());
        player.serverLevel().addFreshEntity(npc);
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.npc_spawned"), true);
        return 1;
    }

    private static int setSkin(CommandContext<CommandSourceStack> ctx, String value, @Nullable String model)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AdventNpcEntity npc = nearestNpc(player);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.translatable("adventcalendar.msg.npc_missing"));
            return 0;
        }
        npc.setSkin(value);
        if (model != null) {
            npc.setSkinModel(AdventNpcEntity.SkinModel.byName(model));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.skin_set", value), true);
        return 1;
    }

    private static int openNpcConfig(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        AdventNpcEntity npc = nearestNpc(player);
        if (npc == null) {
            ctx.getSource().sendFailure(Component.translatable("adventcalendar.msg.npc_missing"));
            return 0;
        }
        ModNetwork.sendTo(player, new OpenGuiPacket(OpenGuiPacket.GUI_NPC_CONFIG, npc.getId()));
        return 1;
    }

    @Nullable
    private static AdventNpcEntity nearestNpc(ServerPlayer player) {
        List<AdventNpcEntity> nearby = player.serverLevel().getEntitiesOfClass(AdventNpcEntity.class,
                player.getBoundingBox().inflate(16.0D));
        AdventNpcEntity best = null;
        double distance = Double.MAX_VALUE;
        for (AdventNpcEntity npc : nearby) {
            if (npc.distanceToSqr(player) < distance) {
                distance = npc.distanceToSqr(player);
                best = npc;
            }
        }
        return best;
    }

    private static int setRewardFromHand(CommandContext<CommandSourceStack> ctx, int day)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold the reward item in your main hand first"));
            return 0;
        }
        return applyReward(ctx, day, held.copy(), "adventcalendar.msg.reward_set");
    }

    private static int setRewardFromItem(CommandContext<CommandSourceStack> ctx, int day, ItemInput input)
            throws CommandSyntaxException {
        return applyReward(ctx, day, input.createItemStack(1, false), "adventcalendar.msg.reward_set");
    }

    private static int setRewardFromItemCount(CommandContext<CommandSourceStack> ctx, int day, ItemInput input,
                                              int count) throws CommandSyntaxException {
        return applyReward(ctx, day, input.createItemStack(count, false), "adventcalendar.msg.reward_set");
    }

    private static int setRewardCommand(CommandContext<CommandSourceStack> ctx, int day, String command) {
        Reward reward = new Reward();
        reward.setCommand(command);
        AdventDataManager.get().setReward(day, reward);
        AdventDataManager.get().saveAsync();
        AdventDataManager.get().syncAll();
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.reward_set", day,
                "/" + command), true);
        return 1;
    }

    private static int applyReward(CommandContext<CommandSourceStack> ctx, int day, ItemStack stack, String key) {
        AdventDataManager data = AdventDataManager.get();
        Reward reward = data.rewards().get(day);
        if (reward == null) {
            reward = Reward.of(stack);
        } else {
            reward.setItem(stack);
        }
        data.setReward(day, reward);
        data.saveAsync();
        data.syncAll();
        Component name = stack.getHoverName();
        ctx.getSource().sendSuccess(() -> Component.translatable(key, day, name), true);
        return 1;
    }

    private static int clearReward(CommandContext<CommandSourceStack> ctx, int day) {
        AdventDataManager data = AdventDataManager.get();
        data.clearReward(day);
        data.saveAsync();
        data.syncAll();
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.reward_cleared", day), true);
        return 1;
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        AdventDataManager data = AdventDataManager.get();
        data.clearAllRewards();
        data.saveAsync();
        data.syncAll();
        ctx.getSource().sendSuccess(() -> Component.literal("All doors cleared"), true);
        return 1;
    }

    private static int sample(CommandContext<CommandSourceStack> ctx) {
        AdventDataManager data = AdventDataManager.get();
        int created = data.fillSampleRewards();
        data.saveAsync();
        data.syncAll();
        ctx.getSource().sendSuccess(() -> Component.literal(created + " sample rewards created"), true);
        return 1;
    }

    private static int forceClaim(CommandContext<CommandSourceStack> ctx, int day, @Nullable ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer player = target != null ? target : ctx.getSource().getPlayerOrException();
        AdventDataManager data = AdventDataManager.get();
        Reward reward = data.rewards().get(day);
        ItemStack preview = reward == null ? ItemStack.EMPTY : reward.item().copy();
        ClaimResult result = data.tryClaim(player, day, true);
        data.sendClaimFeedback(player, day, result, preview);
        data.syncTo(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Claim of day " + day + " for "
                + player.getGameProfile().getName() + ": " + result.name()).withStyle(ChatFormatting.GRAY), false);
        return result.isSuccess() ? 1 : 0;
    }

    private static int resetPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
        int removed = AdventDataManager.get().resetClaims(target.getUUID());
        AdventDataManager.get().saveAsync();
        AdventDataManager.get().syncTo(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("adventcalendar.msg.reset", removed), true);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> ctx) {
        int removed = AdventDataManager.get().resetAllClaims();
        AdventDataManager.get().saveAsync();
        AdventDataManager.get().syncAll();
        ctx.getSource().sendSuccess(() -> Component.literal("Removed " + removed + " claims"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        AdventDataManager data = AdventDataManager.get();
        int year = DateUtil.year();
        Map<Integer, String> claimed = data.claims().claimedDays(target.getUUID(), year);
        CommandSourceStack source = ctx.getSource();
        if (claimed.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("adventcalendar.msg.claimed_days_none",
                    target.getGameProfile().getName()), false);
            return 0;
        }
        List<String> days = new ArrayList<>();
        claimed.keySet().forEach(day -> days.add(String.valueOf(day)));
        source.sendSuccess(() -> Component.translatable("adventcalendar.msg.claimed_days",
                target.getGameProfile().getName(), String.join(", ", days)), false);
        return claimed.size();
    }
}
