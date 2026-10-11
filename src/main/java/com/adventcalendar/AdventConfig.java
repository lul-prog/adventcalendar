package com.adventcalendar;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common (server side) configuration of the mod, written by Forge to
 * {@code config/adventcalendar-common.toml}.
 */
public final class AdventConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<DataLocation> DATA_LOCATION;
    public static final ForgeConfigSpec.ConfigValue<String> DATA_FOLDER;
    public static final ForgeConfigSpec.IntValue AUTOSAVE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOG_EXTERNAL_EDITS;

    public static final ForgeConfigSpec.BooleanValue DECEMBER_ONLY;
    public static final ForgeConfigSpec.IntValue DAYS;
    public static final ForgeConfigSpec.BooleanValue HIDE_LOCKED_REWARDS;
    public static final ForgeConfigSpec.BooleanValue NOTIFY_NEW_DOORS;

    public static final ForgeConfigSpec.EnumValue<GrantMode> GRANT_MODE;
    public static final ForgeConfigSpec.BooleanValue DROP_WHEN_FULL;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_CLAIMS;
    public static final ForgeConfigSpec.EnumValue<SoundChoice> CLAIM_SOUND;

    public static final ForgeConfigSpec.BooleanValue ALLOW_DATE_OVERRIDE;

    public static final ForgeConfigSpec.BooleanValue ALLOW_REMOTE_SKINS;
    public static final ForgeConfigSpec.IntValue SKIN_TIMEOUT_MS;
    public static final ForgeConfigSpec.IntValue SKIN_MAX_KB;

    public static final ForgeConfigSpec.BooleanValue NPC_LOOK_AT_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue NPC_AMBIENT_SOUND;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Where the rewards and claim files are stored.",
                        "WORLD  -> <world>/<data folder>/   (default, travels with the world)",
                        "CONFIG -> <game>/config/<data folder>/")
                .push("storage");
        DATA_LOCATION = b.defineEnum("data_location", DataLocation.WORLD);
        DATA_FOLDER = b.define("data_folder", "adventcalendar");
        AUTOSAVE_SECONDS = b.defineInRange("autosave_seconds", 30, 0, 3600);
        LOG_EXTERNAL_EDITS = b.define("log_external_edits", true);
        b.pop();

        b.comment("Calendar rules").push("calendar");
        DECEMBER_ONLY = b.define("december_only", true);
        DAYS = b.defineInRange("days", 24, 1, 31);
        HIDE_LOCKED_REWARDS = b.define("hide_locked_rewards", true);
        NOTIFY_NEW_DOORS = b.define("notify_new_doors", true);
        b.pop();

        b.comment("Claim behaviour").push("claims");
        GRANT_MODE = b.defineEnum("grant_mode", GrantMode.INSTANT);
        DROP_WHEN_FULL = b.define("drop_when_inventory_full", true);
        ANNOUNCE_CLAIMS = b.define("announce_claims_to_everyone", false);
        CLAIM_SOUND = b.defineEnum("claim_sound", SoundChoice.CUSTOM);
        b.pop();

        b.comment("Testing helpers used by the debug GUI and /advent debug").push("debug");
        ALLOW_DATE_OVERRIDE = b.define("allow_date_override", true);
        b.pop();

        b.comment("NPC skin downloading (custom player skins).").push("skins");
        ALLOW_REMOTE_SKINS = b.define("allow_remote_skins", true);
        SKIN_TIMEOUT_MS = b.defineInRange("download_timeout_ms", 8000, 500, 60000);
        SKIN_MAX_KB = b.defineInRange("max_skin_size_kb", 1024, 8, 16384);
        b.pop();

        b.comment("NPC defaults; every NPC can override these in its own configuration GUI.")
                .push("npc");
        NPC_LOOK_AT_PLAYERS = b.define("look_at_players", true);
        NPC_AMBIENT_SOUND = b.define("ambient_sound", false);
        b.pop();

        SPEC = b.build();
    }

    private AdventConfig() {
    }

    /** Called during mod construction. */
    public static void register() {
        net.minecraftforge.fml.ModLoadingContext.get()
                .registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, SPEC,
                        "adventcalendar-common.toml");
    }

    /** Where the JSON files live. */
    public enum DataLocation {
        WORLD,
        CONFIG
    }

    /** How claimed rewards reach the player. */
    public enum GrantMode {
        /** Immediately pushed into the inventory (or dropped when it does not fit). */
        INSTANT,
        /** Piled up in the player's "stocking" and retrieved from the calendar GUI. */
        STOCKING
    }

    /** Which sound is played when a gift is claimed. */
    public enum SoundChoice {
        CUSTOM,
        VANILLA,
        NONE
    }
}
