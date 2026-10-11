package com.adventcalendar.data;

/** Text of the {@code FORMAT.md} file written next to the JSON data files. */
final class FileFormatDoc {
    private FileFormatDoc() {
    }

    static String text() {
        return """
                # Advent Calendar data files

                Both files live next to this document: `rewards.json` and `claims.json`
                (world folder by default, `config/` when `storage.data_location = CONFIG` in
                `config/adventcalendar-common.toml`).

                Edit them by hand while the server runs: the mod notices the change within two
                seconds, reloads the data and pushes it to every connected player.

                ## rewards.json

                ```json
                {
                  "schema": 1,
                  "days": {
                    "1":  { "item": { "id": "minecraft:cookie", "count": 3 }, "label": "Cookie" },
                    "24": { "item": { "id": "minecraft:diamond_block" },
                            "command": "give @p minecraft:diamond 3" }
                  },
                  "debug": { "date_override": null }
                }
                ```

                * `days` - keys are the day numbers of the calendar (1 to 31, only the first
                  `calendar.days` entries are used).
                * `item` - either the shorthand `"minecraft:cookie"` or an object with
                  `id`, `count` and optional `nbt` (SNBT, the same text `/give` understands).
                * `command` - optional command executed as the claiming player, with command
                  block permissions. Use `@p` or `@s` to target the claimer.
                * `label` - optional custom name shown in the tooltips and GUIs.
                * `hidden` - `true` keeps the icon hidden until the door actually unlocks.
                * `skin` - for `minecraft:player_head` rewards: a username or a direct skin URL.
                * `debug.date_override` - ISO date used instead of the real system date while
                  testing (same setting as the debug GUI / `/advent date set`).

                ## claims.json

                ```json
                {
                  "schema": 1,
                  "players": {
                    "069a79f4-44e9-4726-a5be-fca90e38aaf5": {
                      "name": "Notch",
                      "years": { "2026": { "1": "2026-12-01T09:12:33Z" } },
                      "last_notified": { "2026": 1 },
                      "stocking": []
                    }
                  }
                }
                ```

                * Players are keyed by **UUID**, so LAN worlds, cracked servers and dedicated
                  servers all track claims independently per player.
                * `years` - claimed days per year; delete an entry to let that player claim the
                  day again.
                * `stocking` - gifts waiting to be picked up when
                  `claims.grant_mode = STOCKING`.
                * `last_notified` - last day the player was told about (avoids chat spam).

                Deleting a file simply resets that part of the data; the mod writes it again
                with its defaults on the next start or save.
                """;
    }
}
