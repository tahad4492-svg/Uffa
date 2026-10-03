package com.mrtahadarvish.unstableffa;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores coins, owned kits and the selected kit for every player (data.yml).
 */
public final class DataManager {

    private final UnstableFFA plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private boolean dirty;

    public DataManager(UnstableFFA plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        // save every 30 seconds if something changed
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                saveNow();
            }
        }, 600L, 600L);
    }

    private String path(UUID id, String key) {
        return "players." + id + "." + key;
    }

    // ---- coins -------------------------------------------------------------

    public long getCoins(UUID id) {
        return yaml.getLong(path(id, "coins"), plugin.getConfig().getLong("coins.starting", 0L));
    }

    public void setCoins(UUID id, long amount) {
        yaml.set(path(id, "coins"), Math.max(0L, amount));
        dirty = true;
    }

    public void addCoins(UUID id, long amount) {
        setCoins(id, getCoins(id) + amount);
    }

    /** @return false (and changes nothing) if the player can't afford it */
    public boolean takeCoins(UUID id, long amount) {
        long current = getCoins(id);
        if (current < amount) {
            return false;
        }
        setCoins(id, current - amount);
        return true;
    }

    // ---- kits --------------------------------------------------------------

    public boolean ownsKit(UUID id, String kitId) {
        return yaml.getStringList(path(id, "kits")).contains(kitId);
    }

    public void grantKit(UUID id, String kitId) {
        List<String> owned = new ArrayList<>(yaml.getStringList(path(id, "kits")));
        if (!owned.contains(kitId)) {
            owned.add(kitId);
            yaml.set(path(id, "kits"), owned);
            dirty = true;
        }
    }

    public void revokeKit(UUID id, String kitId) {
        List<String> owned = new ArrayList<>(yaml.getStringList(path(id, "kits")));
        if (owned.remove(kitId)) {
            yaml.set(path(id, "kits"), owned);
            dirty = true;
        }
    }

    public String getSelectedKit(UUID id) {
        return yaml.getString(path(id, "selected"));
    }

    public void setSelectedKit(UUID id, String kitId) {
        yaml.set(path(id, "selected"), kitId);
        dirty = true;
    }

    // ---- io ----------------------------------------------------------------

    public void saveNow() {
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }
}
