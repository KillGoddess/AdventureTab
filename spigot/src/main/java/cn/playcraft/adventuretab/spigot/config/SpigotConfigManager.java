package cn.playcraft.adventuretab.spigot.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SpigotConfigManager {

    private final JavaPlugin plugin;

    private String channel = "adventuretab:data";
    private boolean sendItemUpdates = true;
    private boolean sendEmojiUpdates = true;
    private long throttleMs = 500;

    // PAPI 桥接
    private boolean papiBridgeEnabled = true;
    private List<String> papiPlaceholders = new ArrayList<>();
    private int papiRefreshTicks = 10;

    public SpigotConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        channel = plugin.getConfig().getString("channel", channel);
        sendItemUpdates = plugin.getConfig().getBoolean("send-item-updates", sendItemUpdates);
        sendEmojiUpdates = plugin.getConfig().getBoolean("send-emoji-updates", sendEmojiUpdates);
        throttleMs = plugin.getConfig().getLong("throttle-ms", throttleMs);

        // PAPI 桥接
        papiBridgeEnabled = plugin.getConfig().getBoolean("placeholderapi-bridge.enabled", true);
        papiPlaceholders = plugin.getConfig().getStringList("placeholderapi-bridge.placeholders");
        papiRefreshTicks = plugin.getConfig().getInt("placeholderapi-bridge.refresh-interval", 10);
    }

    public String getChannel() { return channel; }
    public boolean isSendItemUpdates() { return sendItemUpdates; }
    public boolean isSendEmojiUpdates() { return sendEmojiUpdates; }
    public long getThrottleMs() { return throttleMs; }
    public boolean isPapiBridgeEnabled() { return papiBridgeEnabled; }
    public List<String> getPapiPlaceholders() { return papiPlaceholders; }
    public int getPapiRefreshTicks() { return papiRefreshTicks; }
}
