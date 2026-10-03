package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * /ufa - admin command for worlds, arenas, portals and the lobby.
 */
public final class UfaCommand implements TabExecutor {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_\\-]{1,32}$");

    private final UnstableFFA plugin;

    public UfaCommand(UnstableFFA plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setlobby" -> setLobby(sender);
            case "reload" -> {
                plugin.reloadConfig();
                plugin.arenas().reloadSettings();
                plugin.kits().load();
                plugin.send(sender, "<green>Config and kits reloaded.");
            }
            case "world" -> world(sender, args);
            case "arena" -> arena(sender, args);
            case "portal" -> portal(sender, args);
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        plugin.send(s, "<gold>MrTahaDarvish's UnstableFFA <gray>- admin commands");
        String[] lines = {
                "/ufa setlobby <dark_gray>- set the lobby spawn where you stand",
                "/ufa world create <name> [normal|flat|void|nether|end] <dark_gray>- create or import a world",
                "/ufa world list | /ufa world tp <name>",
                "/ufa arena create <name> [world] <dark_gray>- turn a world into an arena (starts in edit mode)",
                "/ufa arena addspawn <name> <dark_gray>- add a spawn point where you stand",
                "/ufa arena clearspawns <name>",
                "/ufa arena lock <name> <dark_gray>- finish editing: map is saved as-is, resets every 30 min",
                "/ufa arena edit <name> <dark_gray>- back to edit mode (changes since lock are undone)",
                "/ufa arena reset <name> <dark_gray>- reset the map right now",
                "/ufa arena list | /ufa arena delete <name>",
                "/ufa portal create <id> <arena> <dark_gray>- link the end portal next to you to an arena",
                "/ufa portal list | /ufa portal remove <id>",
                "/ufa reload",
                "/kit ... and /coins ... <dark_gray>- kit and coin admin"
        };
        for (String line : lines) {
            s.sendMessage(plugin.parse("<gray>" + line));
        }
    }

    // ---- lobby -------------------------------------------------------------------

    private void setLobby(CommandSender s) {
        if (!(s instanceof Player p)) {
            plugin.send(s, "<red>Run this as a player.");
            return;
        }
        plugin.lobby().setLobby(p.getLocation());
        plugin.send(s, "<green>Lobby spawn set. Players can't build, break, fight or use potions in world <white>"
                + UnstableFFA.esc(p.getWorld().getName()) + "<green>.");
    }

    // ---- worlds ------------------------------------------------------------------

    private void world(CommandSender s, String[] a) {
        if (a.length < 2) {
            plugin.send(s, "<red>/ufa world <create|list|tp>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "create", "import" -> {
                if (a.length < 3 || !NAME.matcher(a[2]).matches()) {
                    plugin.send(s, "<red>/ufa world create <name> [normal|flat|void|nether|end]");
                    return;
                }
                WorldManager.Kind kind = a.length >= 4 ? WorldManager.Kind.parse(a[3]) : WorldManager.Kind.NORMAL;
                if (kind == null) {
                    plugin.send(s, "<red>Type must be normal, flat, void, nether or end.");
                    return;
                }
                plugin.send(s, "<yellow>Creating / loading world <white>" + UnstableFFA.esc(a[2])
                        + "<yellow> - this can take a moment...");
                World created = plugin.worlds().create(a[2], kind);
                if (created == null) {
                    plugin.send(s, "<red>The server could not create that world. Check the console.");
                    return;
                }
                plugin.send(s, "<green>World <white>" + UnstableFFA.esc(a[2]) + "<green> is ready. Use <white>/ufa world tp "
                        + UnstableFFA.esc(a[2]) + "<green> to go there.");
            }
            case "list" -> {
                plugin.send(s, "<gold>Loaded worlds:");
                for (World w : Bukkit.getWorlds()) {
                    String tags = "";
                    if (plugin.lobby().isLobbyWorld(w)) {
                        tags += " <aqua>[lobby]";
                    }
                    ArenaManager.Arena arena = plugin.arenas().all().stream()
                            .filter(x -> x.worldName.equals(w.getName())).findFirst().orElse(null);
                    if (arena != null) {
                        tags += " <red>[arena " + UnstableFFA.esc(arena.name) + (arena.locked ? ", live" : ", editing") + "]";
                    }
                    s.sendMessage(plugin.parse("<gray>- <white>" + UnstableFFA.esc(w.getName()) + tags));
                }
            }
            case "tp" -> {
                if (!(s instanceof Player p)) {
                    plugin.send(s, "<red>Run this as a player.");
                    return;
                }
                World target = a.length >= 3 ? Bukkit.getWorld(a[2]) : null;
                if (target == null) {
                    plugin.send(s, "<red>/ufa world tp <loaded world>");
                    return;
                }
                p.teleport(target.getSpawnLocation());
            }
            default -> plugin.send(s, "<red>/ufa world <create|list|tp>");
        }
    }

    // ---- arenas ------------------------------------------------------------------

    private ArenaManager.Arena needArena(CommandSender s, String[] a) {
        ArenaManager.Arena arena = a.length >= 3 ? plugin.arenas().get(a[2]) : null;
        if (arena == null) {
            plugin.send(s, "<red>Unknown arena. Use <white>/ufa arena list<red>.");
        }
        return arena;
    }

    private void arena(CommandSender s, String[] a) {
        if (a.length < 2) {
            plugin.send(s, "<red>/ufa arena <create|addspawn|clearspawns|lock|edit|reset|list|delete>");
            return;
        }
        ArenaManager am = plugin.arenas();
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (a.length < 3 || !NAME.matcher(a[2]).matches()) {
                    plugin.send(s, "<red>/ufa arena create <name> [world]");
                    return;
                }
                World world = a.length >= 4 ? Bukkit.getWorld(a[3]) : (s instanceof Player p ? p.getWorld() : null);
                if (world == null) {
                    plugin.send(s, "<red>Give a loaded world name (see /ufa world list).");
                    return;
                }
                if (plugin.lobby().isLobbyWorld(world)) {
                    plugin.send(s, "<red>The lobby world can't also be an arena.");
                    return;
                }
                if (am.create(a[2], world) == null) {
                    plugin.send(s, "<red>That arena name or world is already used by another arena.");
                    return;
                }
                plugin.send(s, "<green>Arena <white>" + UnstableFFA.esc(a[2].toLowerCase(Locale.ROOT))
                        + "<green> created in <white>" + UnstableFFA.esc(world.getName())
                        + "<green> (edit mode). Add spawns with <white>/ufa arena addspawn<green>, then <white>/ufa arena lock<green>.");
            }
            case "delete" -> {
                if (a.length < 3 || !am.delete(a[2])) {
                    plugin.send(s, "<red>/ufa arena delete <existing arena>");
                    return;
                }
                plugin.send(s, "<green>Arena deleted. Portals linked to it will say they have no arena.");
            }
            case "list" -> {
                plugin.send(s, "<gold>Arenas:");
                for (ArenaManager.Arena arena : am.all()) {
                    String state = arena.locked
                            ? "<green>live <gray>| resets in <yellow>" + am.secondsUntilReset(arena) + "s <gray>| "
                            + am.pending(arena) + " changed blocks"
                            : "<yellow>edit mode";
                    s.sendMessage(plugin.parse("<gray>- <white>" + UnstableFFA.esc(arena.name) + " <gray>(world "
                            + UnstableFFA.esc(arena.worldName) + ", " + arena.spawns.size() + " spawns) " + state));
                }
            }
            case "addspawn" -> {
                if (!(s instanceof Player p)) {
                    plugin.send(s, "<red>Run this as a player.");
                    return;
                }
                ArenaManager.Arena arena = needArena(s, a);
                if (arena == null) {
                    return;
                }
                if (!p.getWorld().getName().equals(arena.worldName)) {
                    plugin.send(s, "<red>Stand inside the arena's world (<white>" + UnstableFFA.esc(arena.worldName)
                            + "<red>) to add a spawn.");
                    return;
                }
                am.addSpawn(arena, p.getLocation());
                plugin.send(s, "<green>Spawn #" + arena.spawns.size() + " added.");
            }
            case "clearspawns" -> {
                ArenaManager.Arena arena = needArena(s, a);
                if (arena != null) {
                    am.clearSpawns(arena);
                    plugin.send(s, "<green>All spawns removed.");
                }
            }
            case "lock" -> {
                ArenaManager.Arena arena = needArena(s, a);
                if (arena == null) {
                    return;
                }
                if (arena.spawns.isEmpty()) {
                    plugin.send(s, "<red>Add at least one spawn first (<white>/ufa arena addspawn " + UnstableFFA.esc(arena.name) + "<red>).");
                    return;
                }
                if (arena.locked) {
                    plugin.send(s, "<red>That arena is already live. Use <white>/ufa arena edit " + UnstableFFA.esc(arena.name)
                            + "<red> first if you want to change the map.");
                    return;
                }
                am.lock(arena);
                plugin.send(s, "<green>Arena <white>" + UnstableFFA.esc(arena.name)
                        + "<green> is live. The map as it is now is the saved map; it resets every "
                        + plugin.getConfig().getLong("arena.reset-interval-minutes", 30L) + " minutes.");
            }
            case "edit" -> {
                ArenaManager.Arena arena = needArena(s, a);
                if (arena != null) {
                    am.edit(arena);
                    plugin.send(s, "<green>Arena <white>" + UnstableFFA.esc(arena.name)
                            + "<green> is in edit mode. Build in creative, then <white>/ufa arena lock " + UnstableFFA.esc(arena.name) + "<green>.");
                }
            }
            case "reset" -> {
                ArenaManager.Arena arena = needArena(s, a);
                if (arena == null) {
                    return;
                }
                if (!arena.locked) {
                    plugin.send(s, "<red>That arena is in edit mode - nothing to reset.");
                    return;
                }
                am.reset(arena);
                plugin.send(s, "<green>Resetting <white>" + UnstableFFA.esc(arena.name) + "<green>...");
            }
            default -> plugin.send(s, "<red>/ufa arena <create|addspawn|clearspawns|lock|edit|reset|list|delete>");
        }
    }

    // ---- portals -----------------------------------------------------------------

    private void portal(CommandSender s, String[] a) {
        if (a.length < 2) {
            plugin.send(s, "<red>/ufa portal <create|remove|list>");
            return;
        }
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(s instanceof Player p)) {
                    plugin.send(s, "<red>Run this as a player.");
                    return;
                }
                if (a.length < 4 || !NAME.matcher(a[2]).matches()) {
                    plugin.send(s, "<red>/ufa portal create <id> <arena>");
                    return;
                }
                ArenaManager.Arena arena = plugin.arenas().get(a[3]);
                if (arena == null) {
                    plugin.send(s, "<red>Unknown arena. Use <white>/ufa arena list<red>.");
                    return;
                }
                int blocks = plugin.portals().create(p, a[2], arena);
                if (blocks == -1) {
                    plugin.send(s, "<red>A portal with that id already exists.");
                } else if (blocks == 0) {
                    plugin.send(s, "<red>No end portal found within 5 blocks. Build one (frames + eyes of ender) and stand next to it.");
                } else {
                    plugin.send(s, "<green>Portal <white>" + UnstableFFA.esc(a[2].toLowerCase(Locale.ROOT))
                            + "<green> linked to arena <white>" + UnstableFFA.esc(arena.name) + "<green> (" + blocks + " portal blocks).");
                }
            }
            case "remove" -> {
                if (a.length < 3 || !plugin.portals().remove(a[2])) {
                    plugin.send(s, "<red>/ufa portal remove <existing id>");
                    return;
                }
                plugin.send(s, "<green>Portal link removed (the blocks stay, they just do nothing now).");
            }
            case "list" -> {
                plugin.send(s, "<gold>Portals:");
                for (PortalManager.Portal portal : plugin.portals().all()) {
                    s.sendMessage(plugin.parse("<gray>- <white>" + UnstableFFA.esc(portal.id) + " <gray>-> arena <white>"
                            + UnstableFFA.esc(String.valueOf(portal.arena)) + " <dark_gray>(world "
                            + UnstableFFA.esc(portal.world) + ", " + portal.blocks.size() + " blocks)"));
                }
            }
            default -> plugin.send(s, "<red>/ufa portal <create|remove|list>");
        }
    }

    // ---- tab completion ----------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String first = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        String second = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";

        if (args.length == 1) {
            out.addAll(List.of("help", "setlobby", "reload", "world", "arena", "portal"));
        } else if (args.length == 2) {
            switch (first) {
                case "world" -> out.addAll(List.of("create", "list", "tp"));
                case "arena" -> out.addAll(List.of("create", "delete", "list", "addspawn", "clearspawns", "lock", "edit", "reset"));
                case "portal" -> out.addAll(List.of("create", "remove", "list"));
                default -> { }
            }
        } else if (args.length == 3) {
            if (first.equals("world") && second.equals("tp")) {
                Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
            } else if (first.equals("arena") && !second.equals("create") && !second.equals("list")) {
                out.addAll(plugin.arenas().names());
            } else if (first.equals("portal") && second.equals("remove")) {
                out.addAll(plugin.portals().ids());
            }
        } else if (args.length == 4) {
            if (first.equals("world") && second.equals("create")) {
                out.addAll(List.of("normal", "flat", "void", "nether", "end"));
            } else if (first.equals("arena") && second.equals("create")) {
                Bukkit.getWorlds().forEach(w -> out.add(w.getName()));
            } else if (first.equals("portal") && second.equals("create")) {
                out.addAll(plugin.arenas().names());
            }
        }

        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : out) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
