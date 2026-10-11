package com.adventcalendar.data;

import com.adventcalendar.AdventCalendar;
import com.adventcalendar.AdventConfig;
import com.adventcalendar.ModSounds;
import com.adventcalendar.network.CalendarSyncPacket;
import com.adventcalendar.network.ClaimFeedbackPacket;
import com.adventcalendar.network.ModNetwork;
import com.adventcalendar.util.DateUtil;
import com.adventcalendar.util.JsonIO;
import com.adventcalendar.util.SkinProfiles;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Server side heart of the mod.
 *
 * <p>Owns the two JSON files (rewards + claims), performs every claim with full validation,
 * keeps the clients in sync, resolves custom player head skins in the background and
 * watches the files for external edits so admins can tweak them while the server runs.</p>
 *
 * <p>Data lives in {@code <world>/adventcalendar/} by default, or in
 * {@code config/adventcalendar/} when {@code storage.data_location = CONFIG}.</p>
 */
public final class AdventDataManager {
    private static final AdventDataManager INSTANCE = new AdventDataManager();

    private static final String REWARDS_FILE = "rewards.json";
    private static final String CLAIMS_FILE = "claims.json";
    private static final String FORMAT_FILE = "FORMAT.md";

    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AdventCalendar-IO");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<UUID, Boolean> previewEnabled = new ConcurrentHashMap<>();
    private final Deque<PendingGrant> pendingGrants = new ArrayDeque<>();
    /** Skins currently being resolved on the IO thread, so we never resolve one twice. */
    private final java.util.Set<String> skinsInFlight = ConcurrentHashMap.newKeySet();

    @Nullable
    private MinecraftServer server;
    private Path directory = Path.of("adventcalendar");
    private RewardsFile rewards = new RewardsFile();
    private ClaimsFile claims = new ClaimsFile();

    private boolean dirty;
    private int ticks;
    private int lastUnlockedThrough = -1;
    private int lastCalendarYear = -1;
    private long rewardsStamp;
    private long claimsStamp;

    private AdventDataManager() {
    }

    public static AdventDataManager get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ lifecycle

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        this.directory = resolveDirectory(server);
        loadFromDisk(true);
        lastUnlockedThrough = DateUtil.unlockedThrough();
        lastCalendarYear = DateUtil.year();
        AdventCalendar.LOGGER.info("Advent Calendar data directory: {}", directory.toAbsolutePath());
    }

    public void onServerStopping() {
        saveNow("server shutdown");
        server = null;
    }

    private Path resolveDirectory(MinecraftServer server) {
        String folder = AdventConfig.DATA_FOLDER.get();
        if (folder == null || folder.isBlank()) {
            folder = "adventcalendar";
        }
        if (AdventConfig.DATA_LOCATION.get() == AdventConfig.DataLocation.CONFIG) {
            return FMLPaths.CONFIGDIR.get().resolve(folder);
        }
        return server.getWorldPath(LevelResource.ROOT).resolve(folder);
    }

    public Path directory() {
        return directory;
    }

    public RewardsFile rewards() {
        return rewards;
    }

    public ClaimsFile claims() {
        return claims;
    }

    @Nullable
    public MinecraftServer server() {
        return server;
    }

    // ------------------------------------------------------------------ persistence

    public void loadFromDisk(boolean initial) {
        JsonObject rewardsJson = JsonIO.read(directory.resolve(REWARDS_FILE));
        rewards = RewardsFile.fromJson(rewardsJson);
        JsonObject claimsJson = JsonIO.read(directory.resolve(CLAIMS_FILE));
        claims = ClaimsFile.fromJson(claimsJson);

        // The debug date override is persisted next to the rewards so testing setups survive restarts.
        String override = rewards.dateOverride();
        if (!override.isEmpty()) {
            java.time.LocalDate parsed = DateUtil.parse(override);
            if (parsed != null) {
                DateUtil.setOverride(parsed);
            }
        } else if (DateUtil.hasOverride()) {
            DateUtil.clearOverride();
        }

        if (rewardsJson == null) {
            saveNow("create default files");
            writeFormatFile();
        }
        rewardsStamp = stamp(directory.resolve(REWARDS_FILE));
        claimsStamp = stamp(directory.resolve(CLAIMS_FILE));
        dirty = false;
        if (!initial) {
            AdventCalendar.LOGGER.info("Advent Calendar data reloaded ({} configured doors, {} players).",
                    rewards.countConfigured(), claims.playerCount());
        }
    }

    /** Force a reload (used by {@code /advent reload} and by the admin GUI button). */
    public void reload() {
        loadFromDisk(false);
        syncAll();
    }

    public void markDirty() {
        dirty = true;
    }

    /** Writes both files on the calling thread. */
    public void saveNow(String reason) {
        RewardsFile rewardsSnapshot = rewards;
        ClaimsFile claimsSnapshot = claims;
        JsonObject rewardsJson = rewardsSnapshot.toJson();
        JsonObject claimsJson = claimsSnapshot.toJson();
        try {
            JsonIO.write(directory.resolve(REWARDS_FILE), rewardsJson);
            JsonIO.write(directory.resolve(CLAIMS_FILE), claimsJson);
            writeFormatFile();
            rewardsStamp = stamp(directory.resolve(REWARDS_FILE));
            claimsStamp = stamp(directory.resolve(CLAIMS_FILE));
            dirty = false;
            AdventCalendar.LOGGER.debug("Advent Calendar saved ({})", reason);
        } catch (RuntimeException e) {
            AdventCalendar.LOGGER.error("Could not save Advent Calendar data: {}", e.toString());
        }
    }

    /** Snapshots the data on the server thread and writes it in the background. */
    public void saveAsync() {
        if (!dirty) {
            return;
        }
        dirty = false;
        JsonObject rewardsJson = rewards.toJson();
        JsonObject claimsJson = claims.toJson();
        Path rewardsFile = directory.resolve(REWARDS_FILE);
        Path claimsFile = directory.resolve(CLAIMS_FILE);
        io.execute(() -> {
            JsonIO.write(rewardsFile, rewardsJson);
            JsonIO.write(claimsFile, claimsJson);
            rewardsStamp = stamp(rewardsFile);
            claimsStamp = stamp(claimsFile);
        });
    }

    private static long stamp(Path file) {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private void writeFormatFile() {
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(FORMAT_FILE);
            if (!Files.exists(target)) {
                Files.writeString(target, FileFormatDoc.text());
            }
        } catch (IOException e) {
            AdventCalendar.LOGGER.debug("Could not write the format documentation: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ ticking

    public void tick(MinecraftServer server) {
        ticks++;
        this.server = server;

        if (AdventConfig.AUTOSAVE_SECONDS.get() > 0
                && ticks % (AdventConfig.AUTOSAVE_SECONDS.get() * 20) == 0) {
            saveAsync();
        }
        if (ticks % 40 == 0 && AdventConfig.LOG_EXTERNAL_EDITS.get()) {
            checkForExternalEdits();
        }
        if (ticks % 100 == 0) {
            notifyNewDoors();
        }
        if (ticks % 5 == 0) {
            processPendingGrants();
        }
    }

    /** Hot reload: an admin edited the JSON files while the server was running. */
    private void checkForExternalEdits() {
        saveAsync();
        long rewardsNow = stamp(directory.resolve(REWARDS_FILE));
        long claimsNow = stamp(directory.resolve(CLAIMS_FILE));
        if ((rewardsNow != 0 && rewardsNow != rewardsStamp) || (claimsNow != 0 && claimsNow != claimsStamp)) {
            AdventCalendar.LOGGER.info("Advent Calendar JSON changed on disk, reloading it.");
            loadFromDisk(false);
            syncAll();
        }
    }

    /** Tells players about freshly unlocked doors (once per door). */
    private void notifyNewDoors() {
        MinecraftServer current = server;
        if (current == null || !AdventConfig.NOTIFY_NEW_DOORS.get()) {
            return;
        }
        int unlocked = Math.min(DateUtil.unlockedThrough(), DateUtil.daysInCalendar());
        int year = DateUtil.year();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            int last = claims.lastNotified(player.getUUID(), year);
            if (unlocked > last) {
                claims.setLastNotified(player.getUUID(), year, unlocked);
                markDirty();
                player.sendSystemMessage(Component.translatable("adventcalendar.msg.unlocked", unlocked)
                        .withStyle(ChatFormatting.GOLD));
                player.playNotifySound(ModSounds.UNLOCK.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            }
        }
        if (unlocked != lastUnlockedThrough || year != lastCalendarYear) {
            lastUnlockedThrough = unlocked;
            lastCalendarYear = year;
            syncAll();
        }
    }

    // ------------------------------------------------------------------ claims

    public int claimedMask(UUID uuid) {
        return claims.claimedMask(uuid, DateUtil.year());
    }

    public boolean isClaimed(UUID uuid, int day) {
        return claims.isClaimed(uuid, DateUtil.year(), day);
    }

    public int pendingCount(UUID uuid) {
        return claims.stockingCount(uuid);
    }

    /**
     * Full claim pipeline with server side validation:
     * day range, unlock state, configured reward and "already claimed" are all re-checked here.
     *
     * @param ignoreDate when true the date check is skipped (debug/testing only, OP gated)
     */
    public ClaimResult tryClaim(ServerPlayer player, int day, boolean ignoreDate) {
        int days = DateUtil.daysInCalendar();
        if (day < 1 || day > days) {
            return ClaimResult.INVALID_DAY;
        }
        if (!ignoreDate && !DateUtil.isUnlocked(day)) {
            return ClaimResult.LOCKED;
        }
        Reward reward = rewards.get(day);
        if (reward == null || reward.isEmpty()) {
            return ClaimResult.NO_REWARD;
        }
        UUID uuid = player.getUUID();
        int year = DateUtil.year();
        if (claims.isClaimed(uuid, year, day)) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        claims.markClaimed(uuid, player.getGameProfile().getName(), year, day);
        markDirty();
        grant(player, reward);
        boolean stocked = AdventConfig.GRANT_MODE.get() == AdventConfig.GrantMode.STOCKING
                && !reward.item().isEmpty();
        return stocked ? ClaimResult.SUCCESS_STOCKED : ClaimResult.SUCCESS;
    }

    /** Hands a reward to a player: runs the command and/or delivers the item. */
    public void grant(ServerPlayer player, Reward reward) {
        MinecraftServer current = server != null ? server : player.getServer();
        if (reward.isCommand() && current != null) {
            String command = reward.command().trim();
            while (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (!command.isEmpty()) {
                try {
                    current.getCommands().performPrefixedCommand(
                            player.createCommandSourceStack().withPermission(2), command);
                } catch (Exception e) {
                    AdventCalendar.LOGGER.warn("Reward command failed for {}: {}", command, e.toString());
                }
            }
        }
        ItemStack stack = reward.item();
        if (stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        if (needsSkinLookup(copy, reward)) {
            pendingGrants.add(new PendingGrant(player.getUUID(), copy, reward.skin()));
            return;
        }
        deliver(player, copy);
    }

    private boolean needsSkinLookup(ItemStack stack, Reward reward) {
        return stack.is(Items.PLAYER_HEAD) && !reward.skin().isEmpty();
    }

    /** Instant delivery to the inventory (falls back to the ground) or stocking mode. */
    public void deliver(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (AdventConfig.GRANT_MODE.get() == AdventConfig.GrantMode.STOCKING) {
            claims.addToStocking(player.getUUID(), player.getGameProfile().getName(), stack);
            markDirty();
            return;
        }
        ItemStack copy = stack.copy();
        player.getInventory().add(copy);
        if (!copy.isEmpty() && AdventConfig.DROP_WHEN_FULL.get()) {
            player.drop(copy, false);
        }
    }

    /** Moves every gift waiting in the stocking into the inventory. */
    public int retrieveStocking(ServerPlayer player) {
        List<ItemStack> stacks = claims.takeStocking(player.getUUID());
        if (stacks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ItemStack stack : stacks) {
            ItemStack copy = stack.copy();
            player.getInventory().add(copy);
            if (!copy.isEmpty() && AdventConfig.DROP_WHEN_FULL.get()) {
                player.drop(copy, false);
            }
            count++;
        }
        markDirty();
        return count;
    }

    /**
     * Background resolution of player head skins. The HTTP lookup itself runs on the IO
     * thread (a blocking call must never touch the server thread); the gift is handed over
     * back on the server thread. Unresolved skins are retried for a few seconds.
     */
    private void processPendingGrants() {
        MinecraftServer current = server;
        if (pendingGrants.isEmpty() || current == null) {
            return;
        }
        int size = pendingGrants.size();
        for (int i = 0; i < size; i++) {
            PendingGrant pending = pendingGrants.poll();
            if (pending == null) {
                break;
            }
            if (current.getPlayerList().getPlayer(pending.playerId) == null) {
                continue; // player left: drop the pending head
            }
            if (!skinsInFlight.add(pending.skin)) {
                // already being resolved, check again a bit later
                pending.attempts++;
                if (pending.attempts < 40) {
                    pendingGrants.add(pending);
                }
                continue;
            }
            io.execute(() -> {
                SkinProfiles.Result result = SkinProfiles.resolve(pending.skin);
                skinsInFlight.remove(pending.skin);
                MinecraftServer running = server;
                if (running != null) {
                    running.execute(() -> finishPendingGrant(pending, result));
                }
            });
        }
    }

    /** Called on the server thread once the skin lookup of one pending gift finished. */
    private void finishPendingGrant(PendingGrant pending, SkinProfiles.Result result) {
        MinecraftServer current = server;
        if (current == null) {
            return;
        }
        ServerPlayer player = current.getPlayerList().getPlayer(pending.playerId);
        if (player == null) {
            return;
        }
        if (result.isUsable()) {
            ItemStack stack = pending.stack.copy();
            CompoundTag owner = new CompoundTag();
            NbtUtils.writeGameProfile(owner, result.asGameProfile());
            stack.getOrCreateTag().put("SkullOwner", owner);
            deliver(player, stack);
        } else if (pending.attempts < 40) {
            pending.attempts++;
            pendingGrants.add(pending);
        } else {
            AdventCalendar.LOGGER.warn("Giving the player head reward without a skin "
                    + "(could not resolve '{}').", pending.skin);
            deliver(player, pending.stack);
        }
    }

    public int resetClaims(UUID uuid) {
        int removed = claims.resetPlayer(uuid);
        markDirty();
        return removed;
    }

    public int resetAllClaims() {
        int removed = claims.resetAll();
        markDirty();
        return removed;
    }

    // ------------------------------------------------------------------ admin edits

    public void setReward(int day, Reward reward) {
        rewards.set(day, reward);
        markDirty();
    }

    public void clearReward(int day) {
        rewards.clear(day);
        markDirty();
    }

    public void clearAllRewards() {
        rewards.clearAll();
        markDirty();
    }

    public int fillSampleRewards() {
        int created = SampleRewards.fill(rewards, DateUtil.daysInCalendar());
        markDirty();
        return created;
    }

    public void setDateOverride(@Nullable java.time.LocalDate date) {
        DateUtil.setOverride(date);
        rewards.setDateOverride(date == null ? "" : DateUtil.format(date));
        markDirty();
        saveNow("date override");
        syncAll();
    }

    // ------------------------------------------------------------------ debug preview

    public boolean isPreview(UUID uuid) {
        return previewEnabled.getOrDefault(uuid, false);
    }

    public void setPreview(UUID uuid, boolean enabled) {
        if (enabled) {
            previewEnabled.put(uuid, true);
        } else {
            previewEnabled.remove(uuid);
        }
    }

    // ------------------------------------------------------------------ sync

    public CalendarSyncPacket buildSync(ServerPlayer player) {
        int days = DateUtil.daysInCalendar();
        int year = DateUtil.year();
        UUID uuid = player.getUUID();
        boolean admin = player.hasPermissions(2);
        boolean preview = admin && isPreview(uuid);

        List<CalendarSyncPacket.DayEntry> entries = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            Reward reward = rewards.get(day);
            if (reward == null || reward.isEmpty()) {
                continue;
            }
            boolean revealed = admin
                    || preview
                    || !AdventConfig.HIDE_LOCKED_REWARDS.get()
                    || DateUtil.isUnlocked(day)
                    || claims.isClaimed(uuid, year, day);
            entries.add(new CalendarSyncPacket.DayEntry(
                    day,
                    revealed ? reward.item().copy() : ItemStack.EMPTY,
                    admin ? reward.command() : "",
                    revealed ? reward.label() : "",
                    reward.isHidden(),
                    reward.item().isEmpty()));
        }

        return new CalendarSyncPacket(
                days,
                year,
                DateUtil.now().getMonthValue(),
                DateUtil.now().getDayOfMonth(),
                Math.min(DateUtil.unlockedThrough(), days),
                claims.claimedMask(uuid, year),
                rewards.configuredMask(),
                claims.stockingCount(uuid),
                admin,
                DateUtil.hasOverride(),
                AdventConfig.DECEMBER_ONLY.get(),
                preview,
                AdventConfig.HIDE_LOCKED_REWARDS.get(),
                entries);
    }

    public void syncTo(ServerPlayer player) {
        ModNetwork.sendTo(player, buildSync(player));
    }

    public void syncAll() {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    // ------------------------------------------------------------------ feedback

    /** Chat + sound + particles feedback after a claim attempt. */
    public void sendClaimFeedback(ServerPlayer player, int day, ClaimResult result, ItemStack granted) {
        int days = DateUtil.daysInCalendar();
        Component message;
        switch (result) {
            case SUCCESS, SUCCESS_STOCKED -> {
                ItemStack stack = granted == null ? ItemStack.EMPTY : granted;
                Component itemName = stack.isEmpty()
                        ? Component.translatable("adventcalendar.gui.day.today")
                        : stack.getHoverName();
                if (result == ClaimResult.SUCCESS_STOCKED) {
                    message = Component.translatable("adventcalendar.msg.stocking", itemName);
                } else if (stack.getCount() > 1) {
                    message = Component.translatable("adventcalendar.msg.claimed_multiple", day, itemName,
                            stack.getCount());
                } else {
                    message = Component.translatable("adventcalendar.msg.claimed", day, itemName);
                }
                message = message.copy().withStyle(ChatFormatting.GOLD);
                playClaimEffects(player, day);
            }
            case LOCKED -> message = Component.translatable(
                    AdventConfig.DECEMBER_ONLY.get() && !DateUtil.isDecember()
                            ? "adventcalendar.msg.locked_december" : "adventcalendar.msg.locked", day)
                    .withStyle(ChatFormatting.RED);
            default -> message = Component.translatable(result.messageKey(), day, days)
                    .withStyle(ChatFormatting.RED);
        }
        player.sendSystemMessage(message);
        if (!result.isSuccess()) {
            player.playNotifySound(ModSounds.LOCK.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            return;
        }
        // The chat notification has its own soft ping.
        player.playNotifySound(ModSounds.NOTIFY.get(), SoundSource.PLAYERS, 0.7F, 1.4F);
        if (AdventConfig.ANNOUNCE_CLAIMS.get() && server != null) {
            Component announce = Component.translatable("adventcalendar.msg.announce",
                    player.getDisplayName(), day).withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != player) {
                    other.sendSystemMessage(announce);
                }
            }
        }
        ModNetwork.sendTo(player, new ClaimFeedbackPacket(day, result, claimedMask(player.getUUID()),
                claims.stockingCount(player.getUUID()), granted == null ? ItemStack.EMPTY : granted));
    }

    private void playClaimEffects(ServerPlayer player, int day) {
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 1.2D;
        double z = player.getZ();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 40, 0.6D, 0.7D, 0.6D, 0.35D);
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 12, 0.4D, 0.4D, 0.4D, 0.05D);
        switch (AdventConfig.CLAIM_SOUND.get()) {
            case CUSTOM -> player.playNotifySound(ModSounds.CLAIM.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            case VANILLA -> level.playSound(null, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            case NONE -> {
            }
        }
    }

    /** Small helper used by the admin GUI to describe a day. */
    public String describe(int day) {
        Reward reward = rewards.get(day);
        return reward == null ? "" : reward.displayName().getString();
    }

    public Map<UUID, String> playerNames() {
        return claims.names();
    }

    // ------------------------------------------------------------------ helpers

    /** A head reward whose skin still has to be resolved. */
    private static final class PendingGrant {
        final UUID playerId;
        final ItemStack stack;
        final String skin;
        int attempts;

        PendingGrant(UUID playerId, ItemStack stack, String skin) {
            this.playerId = playerId;
            this.stack = stack;
            this.skin = skin;
        }
    }
}
