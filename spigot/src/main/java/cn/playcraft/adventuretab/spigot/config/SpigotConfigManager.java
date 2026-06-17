package cn.playcraft.adventuretab.spigot.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpigotConfigManager {

    private static final String ADVENTURE_PREFIX_PLACEHOLDER = "adventureprefix_prefix";

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
        papiPlaceholders = new ArrayList<>(plugin.getConfig().getStringList("placeholderapi-bridge.placeholders"));
        ensureAdventurePrefixPlaceholder();
        papiRefreshTicks = plugin.getConfig().getInt("placeholderapi-bridge.refresh-interval", 10);
    }

    private void ensureAdventurePrefixPlaceholder() {
        boolean exists = papiPlaceholders.stream()
                .anyMatch(SpigotConfigManager::isAdventurePrefixPlaceholder);
        if (!exists) {
            papiPlaceholders.add(ADVENTURE_PREFIX_PLACEHOLDER);
        }
    }

    private static boolean isAdventurePrefixPlaceholder(String placeholder) {
        if (placeholder == null) return false;
        String normalized = placeholder.trim();
        if (normalized.startsWith("%") && normalized.endsWith("%") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return ADVENTURE_PREFIX_PLACEHOLDER.equals(normalized.toLowerCase(Locale.ROOT));
    }

    public String getChannel() { return channel; }
    public boolean isSendItemUpdates() { return sendItemUpdates; }
    public boolean isSendEmojiUpdates() { return sendEmojiUpdates; }
    public long getThrottleMs() { return throttleMs; }
    public boolean isPapiBridgeEnabled() { return papiBridgeEnabled; }
    public List<String> getPapiPlaceholders() { return papiPlaceholders; }
    public int getPapiRefreshTicks() { return papiRefreshTicks; }
}
