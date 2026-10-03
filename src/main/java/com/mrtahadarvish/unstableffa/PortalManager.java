package com.mrtahadarvish.unstableffa;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * End portals that send players into an arena. Build a normal end portal in
 * the lobby, stand in/next to it and run /ufa portal create &lt;id&gt; &lt;arena&gt;.
 * Every connected end-portal block is linked to that arena.
 */
public final class PortalManager {

    public static final class Portal {
        public final String id;
        public final String world;
        public String arena;
        public final Set<Long> blocks = new HashSet<>();

        Portal(String id, String world, String arena) {
            this.id = id;
            this.world = world;
            this.arena = arena;
        }
    }

    private final UnstableFFA plugin;
    private final File file;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<String, Portal> index = new HashMap<>();   // "world:packedPos" -> portal

    public PortalManager(UnstableFFA plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
        load();
    }

    public Portal get(String id) {
        return id == null ? null : portals.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> ids() {
        return new ArrayList<>(portals.keySet());
    }

    public Iterable<Portal> all() {
        return portals.values();
    }

    /** Portal that contains this block, or null. */
    public Portal findAt(Block block) {
        return index.get(block.getWorld().getName() + ":" + ArenaManager.pack(block.getX(), block.getY(), block.getZ()));
    }

    /**
     * Links the end portal next to the player to an arena.
     *
     * @return number of portal blocks linked, 0 if no end portal was found nearby,
     *         -1 if the id is already taken
     */
    public int create(Player player, String id, ArenaManager.Arena arena) {
        String key = id.toLowerCase(Locale.ROOT);
        if (portals.containsKey(key)) {
            return -1;
        }

        Block origin = player.getLocation().getBlock();
        Block start = null;
        int best = Integer.MAX_VALUE;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    Block b = origin.getRelative(dx, dy, dz);
                    if (b.getType() == Material.END_PORTAL) {
                        int dist = dx * dx + dy * dy + dz * dz;
                        if (dist < best) {
                            best = dist;
                            start = b;
                        }
                    }
                }
            }
        }
        if (start == null) {
            return 0;
        }

        Portal portal = new Portal(key, start.getWorld().getName(), arena.name);
        Deque<Block> queue = new ArrayDeque<>();
        portal.blocks.add(ArenaManager.pack(start.getX(), start.getY(), start.getZ()));
        queue.add(start);
        while (!queue.isEmpty() && portal.blocks.size() < 2048) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block n = b.getRelative(dx, dy, dz);
                        if (n.getType() == Material.END_PORTAL
                                && portal.blocks.add(ArenaManager.pack(n.getX(), n.getY(), n.getZ()))) {
                            queue.add(n);
                        }
                    }
                }
            }
        }

        portals.put(key, portal);
        reindex();
        save();
        return portal.blocks.size();
    }

    public boolean remove(String id) {
        boolean removed = portals.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            reindex();
            save();
        }
        return removed;
    }

    private void reindex() {
        index.clear();
        for (Portal portal : portals.values()) {
            for (long packed : portal.blocks) {
                index.put(portal.world + ":" + packed, portal);
            }
        }
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("portals");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null || s.getString("world") == null) {
                continue;
            }
            Portal portal = new Portal(id, s.getString("world"), s.getString("arena"));
            portal.blocks.addAll(s.getLongList("blocks"));
            portals.put(id, portal);
        }
        reindex();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Portal portal : portals.values()) {
            String base = "portals." + portal.id;
            yaml.set(base + ".world", portal.world);
            yaml.set(base + ".arena", portal.arena);
            yaml.set(base + ".blocks", new ArrayList<>(portal.blocks));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save portals.yml: " + ex.getMessage());
        }
    }
}
