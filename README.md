# Advent Calendar

A real-world date driven **Advent Calendar** mod for **Minecraft 1.20.1 (Forge)**.

Twenty-four doors open one by one during December. Every player tracks their own progress,
rewards can be plain items or server commands, the whole calendar is stored as editable JSON
and a fully customizable NPC (custom player skins included) hands it out in the world.

* Player GUI: 24 slots, each one showing whether the door is **locked**, **available** or
  **claimed**, plus the reward icon once the door is unlocked.
* OP admin GUI: **drag & drop** items from your inventory onto a door to assign the reward.
* Debug GUI: bypass the real date, preview every reward, simulate claims, reset progress.
* Four **custom sound events**: locked door, door unlocked, claim success, chat notification.
* Per-player progress keyed by **UUID** (LAN and servers work out of the box).
* JSON storage (`rewards.json` + `claims.json`) inside the world, hot-reloadable.

---

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.x (tested against 47.2.0) |
| Java | 17 |

## Building

```bash
./gradlew build          # the jar lands in build/libs/adventcalendar-2.0.0.jar
./gradlew runClient      # quick test client
```

Copy the jar into your `mods/` folder (client **and** server; the NPC and the JSON files live
on the server side, the GUIs on the client side - it works in single player and on a server).

---

## First steps in game

1. `/advent sample` - fills every still empty day with a Christmas themed default reward
   (also available as the *Sample rewards* button of the admin GUI).
2. `/advent spawn` - spawns the NPC where you are standing. Variants:
   `/advent spawn "Santa Claus" Notch classic` (name, skin, model).
3. Right click the NPC (or use `/advent gui`, or the *Advent Calendar* item) to open the
   calendar.
4. Configure the doors with `/advent admin` (or `/advent rewards`).
5. Test everything without waiting for December with `/advent debug`.

---

## Commands

Every command exists twice: `/advent ...` and `/adventcalendar ...`.
Everything that modifies data needs permission level 2 (operator).

| Command | Who | What it does |
|---|---|---|
| `/advent help` | everyone | Lists every command in chat |
| `/advent gui` | everyone | Opens the player calendar |
| `/advent list [player]` | everyone / OP | Shows which doors a player already claimed |
| `/advent info` | everyone | Data directory, configured doors, tracked players |
| `/advent date` | everyone | Shows the (possibly overridden) server date |
| `/advent admin` / `/advent rewards` | OP | Opens the admin GUI |
| `/advent debug` | OP | Opens the debug GUI |
| `/advent spawn [name] [skin] [auto\|classic\|slim]` | OP | Spawns an NPC where you stand |
| `/advent skin <url\|username\|local:file.png> [model]` | OP | Re-skins the nearest NPC |
| `/advent npc config` | OP | Opens the configuration GUI of the nearest NPC |
| `/advent setreward <day> [<item> [count]\|command <cmd>]` | OP | Sets a reward (without arguments it uses the item in your main hand) |
| `/advent clearreward <day>` / `/advent clearall` | OP | Removes one / every reward |
| `/advent sample` | OP | Fills the empty days with sample rewards |
| `/advent date set <YYYY-MM-DD>` \| `date day <n>` \| `date clear` | OP | Date override for testing |
| `/advent preview <true\|false>` | OP | Lets you see locked rewards in your GUI |
| `/advent claim <day> [player]` | OP | Forces a claim (skips the date check) |
| `/advent grantall` | OP | Grants every configured reward to you |
| `/advent reset <player>` / `/advent reset all` | OP | Deletes claim data |
| `/advent reload` / `/advent save` | OP | Reloads / writes the JSON files |

---

## The three GUIs

### Player calendar
Click a door to claim its gift. Locked doors play the error sound and the tooltip tells when
they open, claimed doors turn green with a check mark. If the server uses the *stocking*
grant mode, a **Collect** button appears at the bottom.

### Admin GUI (`/advent admin`)
* Pick an item from the inventory palette at the bottom and **drag it onto a door**.
* Left click a door without a brush → editor for that day: label, command reward, *hidden*
  flag (hidden rewards are sent to the client only when the door unlocks, so nobody can peek).
* Right click a door → clears it. Ctrl + left click → takes a copy of the reward.
* Buttons: *Save to disk*, *Reload file*, *Sample rewards*, *Clear all*, *Spawn NPC*.

### Debug GUI (`/advent debug`)
Everything a server owner needs to test the mod:

* set the date from a text box (`YYYY-MM-DD`), shift it by one day, jump to the 1st of
  December or clear the override,
* *Preview rewards* (see all gift icons before they unlock) and *Simulate claims* (render
  every door as claimed),
* force-claim the selected door, grant every reward, reset your own or everyone's progress,
* play each of the four custom sounds.

### NPC configuration (control wand or `/advent npc config`)
Name, name colour (16 swatches), skin, model (AUTO / CLASSIC / SLIM), glowing, particles,
invulnerability, looking at players and the ambient sound. The skin field accepts:

* a direct PNG URL – `https://example.com/skin.png`
* a Minecraft username – `Notch` (resolved through the Mojang API)
* a local file – `local:myskin.png`, dropped into `config/adventcalendar/skins/`

Downloaded skins are cached in `config/adventcalendar/skins/<sha1>.png` and registered as a
dynamic texture in game, so they only have to be fetched once.

---

## Storage: two editable JSON files

By default the mod writes into `<world>/adventcalendar/` (see the `storage` section of the
config to put it into `config/adventcalendar/` instead). `FORMAT.md` - written next to the
files - documents everything; the short version:

**`rewards.json`**

```json
{
  "schema": 1,
  "days": {
    "1": { "item": "minecraft:cookie", "count": 5, "label": "Christmas Cookie" },
    "2": { "item": { "id": "minecraft:leather_chestplate",
                     "nbt": "{display:{color:11541284}}" },
           "label": "Knitted Sweater" },
    "3": { "command": "give @p minecraft:diamond 3", "label": "Top prize" },
    "24": { "item": { "id": "minecraft:player_head", "count": 1 },
            "skin": "Notch", "hidden": true }
  },
  "debug": { "date_override": "2024-12-06" }
}
```

* `item` may be the short form `"minecraft:cookie"` or an object with `id`, `count` and an
  optional SNBT `nbt` string.
* `command` runs **as the claiming player** with permission level 2 (a leading `/` is
  optional). Both an item and a command can be combined.
* `skin` (player heads only) is resolved in the background and written into `SkullOwner`.
* `hidden: true` keeps the reward invisible until the door unlocks.

**`claims.json`**

```json
{
  "schema": 1,
  "players": {
    "069a79f4-44e9-4726-a5be-fca90e38aaf5": {
      "name": "Notch",
      "years": { "2024": { "1": "2024-12-01T09:12:33Z", "2": "2024-12-02T18:44:02Z" } },
      "last_notified": { "2024": 2 },
      "stocking": [ "minecraft:cookie" ]
    }
  }
}
```

Editing either file while the server runs triggers a reload within two seconds (configurable),
so a "Christmas event" can be prepared without restarting anything.
`/advent reload` and `/advent save` do the same manually.

---

## Configuration

`config/adventcalendar-common.toml`:

| Section | Key | Default | Meaning |
|---|---|---|---|
| storage | `data_location` | `WORLD` | `WORLD` → `<world>/adventcalendar/`, `CONFIG` → `config/adventcalendar/` |
| storage | `data_folder` | `adventcalendar` | Folder name inside the chosen location |
| storage | `autosave_seconds` | `30` | Autosave interval, `0` disables it |
| storage | `log_external_edits` | `true` | Logs + reloads when the JSON files change on disk |
| calendar | `december_only` | `true` | Doors only open in December |
| calendar | `days` | `24` | Number of doors (1-31) |
| calendar | `hide_locked_rewards` | `true` | Server never sends locked reward icons |
| calendar | `notify_new_doors` | `true` | Chat ping when a new door opens |
| claims | `grant_mode` | `INSTANT` | `INSTANT` gives the item right away, `STOCKING` stores it |
| claims | `drop_when_inventory_full` | `true` | Drop the gift on the ground when the inventory is full |
| claims | `announce_claims_to_everyone` | `false` | Broadcast claims to the whole server |
| claims | `claim_sound` | `CUSTOM` | `CUSTOM`, `VANILLA` or `NONE` |
| debug | `allow_date_override` | `true` | Lets OPs use the date override |
| skins | `allow_remote_skins` | `true` | Allows downloading NPC skins from the internet |
| skins | `skin_timeout_ms` / `skin_max_kb` | `8000` / `1024` | Download limits |
| npc | `npc_look_at_players` / `npc_ambient_sound` | `true` / `true` | Defaults for freshly spawned NPCs |

---

## Sound events

| ID | Played when | File |
|---|---|---|
| `adventcalendar:lock` | a locked door is clicked | `assets/adventcalendar/sounds/lock.ogg` |
| `adventcalendar:unlock` | a door becomes available | `assets/adventcalendar/sounds/unlock.ogg` |
| `adventcalendar:claim` | a gift is claimed successfully | `assets/adventcalendar/sounds/claim.ogg` |
| `adventcalendar:notify` | chat/action-bar notification, NPC ping | `assets/adventcalendar/sounds/notify.ogg` |

They are registered as real `SoundEvent`s (`ModSounds`), declared in `sounds.json` and the
`.ogg` files can simply be replaced by your own. Subtitles/text keys live in
`assets/adventcalendar/lang/{en_us,es_es}.json`.

## Project layout

```
src/main/java/com/adventcalendar/
├── AdventCalendar.java       mod entry point + event wiring
├── AdventConfig.java         ForgeConfigSpec (adventcalendar-common.toml)
├── ModSounds / ModItems / ModEntities
├── client/                   screens, skin manager, renderer, client state
├── command/AdventCommand.java
├── data/                     JSON files, claim logic, sample rewards, format docs
├── entity/AdventNpcEntity.java
├── item/                     control wand + hand held calendar
├── network/                  7 packets + server side handlers
└── util/                     date helper, JSON/HTTP helpers, skin profiles
tools/                        asset + language generators, static checker
```

`tools/` is only used during development:

* `python3 tools/generate_assets.py` - rebuilds the GUI atlas, the item texture, the mod logo
  and the four `.ogg` sounds.
* `python3 tools/generate_lang.py` - regenerates the language files and validates that every
  `%s` placeholder matches the arguments the code passes.
* `python3 tools/check_java.py` - static check of every `.java` file against the vanilla
  1.20.1 / Forge 1.20.1 sources (used because CI here has no JDK).

## Troubleshooting

* **No NPC after `/advent spawn`?** Check that you have permission level 2 and that no other
  mod blocks entity spawning; the NPC is persistent and never despawns.
* **Skin stays Steve/Alex?** The download happens in the background; check
  `config/adventcalendar/skins/` and the log (`Could not download skin ...`). With
  `allow_remote_skins = false` only `local:` skins are loaded.
* **Doors open on the wrong day?** The server clock is authoritative; `/advent date` shows
  what the mod sees. Use `date_override` for testing.
* **Rewards missing for some players?** Progress is stored per UUID and per year; check
  `claims.json` next to the world (or run `/advent list <player>`).

---

## Guía rápida (español)

1. `/advent sample` rellena las puertas vacías con recompensas navideñas de ejemplo.
2. `/advent spawn` crea el NPC donde estás (o `/advent spawn "Papá Noel" Notch slim`).
3. Clic derecho en el NPC abre el calendario de 24 puertas; cada puerta muestra su estado
   (bloqueada, disponible, reclamada) y el icono de la recompensa cuando se desbloquea.
4. `/advent admin` abre el panel de operador: elige un objeto del inventario y **arrástralo
   sobre una puerta** para asignarlo; clic derecho la borra.
5. `/advent debug` permite forzar la fecha, previsualizar regalos, simular reclamos y
   reiniciar el progreso.
6. Los datos viven en `<mundo>/adventcalendar/rewards.json` y `claims.json` (o en
   `config/adventcalendar/`, configurable) y se recargan solos 2 segundos después de editarlos.
