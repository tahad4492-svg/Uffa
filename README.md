# MrTahaDarvish's UnstableFFA

An "unstable SMP" style FFA plugin for **Paper 1.21.11** (Java 21).

- Coins: **2 coins per kill** (configurable)
- `/kit` shop menu: admins add kits + prices, bought kits are yours **forever**
- Lobby where players can do nothing (no PvP, building, breaking, potions, items, inventory moves)
- Built-in world manager (create / import worlds, like Multiverse-Core)
- End-portal system: you place an end portal, link it to an arena, players jump in and fight
- Arena maps **reset every 30 minutes**: explosions, cobwebs, lava, fire, placed blocks... all undone, the map itself is restored exactly

## Build

You need JDK 21 and Maven:

```
mvn package
```
(or double-click `build.bat` on Windows / run `./build.sh`)

The jar is `target/UnstableFFA-1.0.0.jar`. Drop it in your server's `plugins/` folder and restart.

## Setup walkthrough (as admin / OP)

1. **Lobby**: stand in your lobby world where players should spawn and run `/ufa setlobby`.
   Players get no permissions there. You (OP) can build in the lobby only while in **creative** mode.
2. **Get your map worlds in**
   - Copy a map folder (e.g. `capital_city`) into the server root, then `/ufa world create capital_city void`
     (use `void` so nothing generates outside the map; `normal` generates terrain around it).
   - Or create a fresh one: `/ufa world create law_castle void`, `/ufa world tp law_castle`, then build/paste.
   - Worlds are remembered and reloaded on every restart.
3. **Make it an arena**: `/ufa world tp capital_city`, then `/ufa arena create capital_city`.
   It starts in *edit mode*: nothing is recorded, you can build freely in creative.
4. **Add spawn points**: stand where players should appear and run `/ufa arena addspawn capital_city` (repeat for more; one is chosen at random).
5. **Lock it**: `/ufa arena lock capital_city`. The map exactly as it is now is the saved map.
   From now on every block change is recorded and undone every 30 minutes.
   Need to change the map later? `/ufa arena edit capital_city` (undoes any player changes first), edit, lock again.
6. **Portal**: in the lobby build a normal end portal (12 end portal frames + eyes of ender), stand next to it and run
   `/ufa portal create capital capital_city`. Everyone who steps into that portal is sent to the arena with their selected kit.
7. **Kits**: put the items you want in your inventory (armor slots + offhand count too) and run
   `/kit create knight 50` (price 50 coins; `0` = free). Then `/kit seticon knight` while holding the item you want as the menu icon, or `/kit seticon knight head Notch` to use a player's head (skin fetched automatically). You can also do it at creation: `/kit create knight 50 Notch`.

## Commands

| Command | Who | What |
|---|---|---|
| `/kit` | everyone | open the kit menu (click to select / buy) |
| `/kit create <name> [price]` | admin | save your inventory as a kit |
| `/kit update <name>` | admin | overwrite a kit with your inventory |
| `/kit setprice <name> <price>` | admin | change a price (owners keep the kit) |
| `/kit seticon <name>` | admin | hand item = menu icon |
| `/kit delete <name>`, `/kit list` | admin | |
| `/kit give <player> <name>` / `revoke` | admin | unlock / remove a kit for someone |
| `/coins [player]` | everyone | show coins |
| `/coins give\|take\|set <player> <n>` | admin | |
| `/lobby` (`/hub`, `/spawn`) | everyone | go to the lobby |
| `/ufa setlobby` | admin | set lobby spawn |
| `/ufa world create <name> [normal\|flat\|void\|nether\|end]` | admin | create or import a world |
| `/ufa world list` / `/ufa world tp <name>` | admin | |
| `/ufa arena create\|addspawn\|clearspawns\|lock\|edit\|reset\|list\|delete` | admin | arena management |
| `/ufa portal create <id> <arena>` / `remove` / `list` | admin | end-portal links |
| `/ufa reload` | admin | reload config + kits |

Permissions: `ufa.use` (default everyone), `ufa.admin` (default OP).

## How the player loop works

1. Join -> teleported to the lobby with a **Kit Selector** (nether star) in the hotbar.
2. `/kit` or the star -> pick or buy a kit. A free `starter` kit exists by default.
3. Jump into an end portal -> teleported to the arena spawn with the selected kit, 3 seconds of spawn protection.
4. Kill someone -> **+2 coins**. Die -> nothing drops, you respawn in the lobby automatically.

## How the 30 minute map reset works

When an arena is locked the plugin records the **original state of each block the first time it changes**
(placing, breaking, TNT / creeper / bed / crystal explosions, fire, lava / water flow, pistons, falling sand, tree and crop growth, ice / snow, etc.).
Every 30 minutes (players get warnings at 60/30/10/5..1 seconds) it:

1. sends everyone in the arena to the lobby (configurable),
2. removes leftover entities (dropped items, primed TNT, arrows, pearls, end crystals, ...),
3. puts every recorded block back over a few ticks (no lag spike).

Because only changed blocks are stored, the map itself is never copied or touched. Pending changes are also
restored when the server stops, so the map is never left damaged.

## Config

`plugins/UnstableFFA/config.yml`: coins per kill, reset interval (default 30), warning times, blocks restored per tick,
spawn protection, auto respawn, which entities get cleared on reset, and the chat prefix.

## Known limits

- Chest / furnace / other container contents are not tracked, so players are blocked from opening containers in live arenas (`arena.block-containers`).
- Item frames and armor stands in live arenas can't be edited by players (their changes can't be restored).
- If the server **crashes** (not a normal stop) while an arena has unreset changes, those changes are lost from memory and the map stays damaged. Re-copy the original map folder in that case.
- This source was written against the Paper 1.21.11 API but has **not been compiled or run on a server yet** (the build environment could not reach the Paper Maven repository). If `mvn package` reports an API mismatch, send the error message and it can be fixed quickly.
