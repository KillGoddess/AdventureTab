package cn.playcraft.adventuretab.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 配置管理器 - 对标 TAB config.yml 结构
 * 支持: 多设计 Header/Footer、高级排序、全局玩家列表分组、
 *       占位符条件、输出替换、动画、CraftEngine、LuckPerms
 */
public class ConfigManager {

    private final Path dataDirectory;
    private final Logger logger;

    // --- Header/Footer (多设计 + 条件) ---
    private List<HeaderFooterDesign> headerFooterDesigns = new ArrayList<>();
    private int headerTopPadding = 0;

    // --- Tablist Name Formatting ---
    private boolean tablistFormattingEnabled = true;
    private String tablistDisableCondition = "";

    // --- AdventureFriend per-viewer social relations ---
    private boolean socialRelationsEnabled = true;
    private String socialFriendPrefix = "&c❤ ";
    private String socialRequestPrefix = "&e? ";
    private String socialBlockedPrefix = "&8X ";

    // --- Sorting ---
    private List<String> sortingTypes = List.of("GROUPS:owner,admin,mod,helper,builder,vip,default", "PLACEHOLDER_A_TO_Z:%player%");
    private boolean caseSensitiveSorting = true;

    // --- Global PlayerList ---
    private boolean globalPlayerListEnabled = false;
    private boolean displayOthersAsSpectators = false;
    private boolean displayVanishedAsSpectators = true;
    private boolean isolateUnlistedServers = false;
    private boolean updateLatency = false;
    private List<String> spyServers = new ArrayList<>();
    private Map<String, List<String>> serverGroups = new LinkedHashMap<>();

    // --- CraftEngine ---
    private boolean craftEngineEnabled = true;
    private boolean displayCustomItem = true;
    private boolean emojiSupport = true;
    private int maxTextureCacheSize = 200;
    private String ceChannel = "adventuretab:data";

    // --- LuckPerms ---
    private boolean luckPermsEnabled = true;
    private int defaultWeight = 0;
    private int weightCacheDuration = 10;

    // --- Performance ---
    private long throttleMs = 500;
    private int textureCacheDuration = 60;
    private int updateInterval = 5;

    // --- Placeholders ---
    private String dateFormat = "dd.MM.yyyy";
    private String timeFormat = "HH:mm:ss";
    private Map<String, Map<String, Object>> conditions = new LinkedHashMap<>();
    private Map<String, Map<String, String>> outputReplacements = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> animations = new LinkedHashMap<>();
    private int defaultRefreshInterval = 500;

    // --- CustomNameplates 兼容 ---
    private boolean cnpCompatEnabled = false;

    // --- Debug ---
    private boolean debug = false;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            if (!Files.exists(configFile)) {
                saveDefault(configFile, "velocity-config.yml");
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (InputStream is = Files.newInputStream(configFile)) {
                root = yaml.load(is);
            }
            if (root == null) {
                logger.warn("[AdventureTAB] config.yml 为空，使用默认配置");
                return;
            }

            // --- header-footer ---
            headerFooterDesigns.clear();
            Map<String, Object> hfSection = getSection(root, "header-footer");
            if (getBoolean(hfSection, "enabled", true)) {
                Map<String, Object> designs = getSection(hfSection, "designs");
                for (Map.Entry<String, Object> entry : designs.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> def = (Map<String, Object>) entry.getValue();
                        String condition = getString(def, "display-condition", null);
                        List<String> header = getStringList(def, "header");
                        List<String> footer = getStringList(def, "footer");
                        headerFooterDesigns.add(new HeaderFooterDesign(entry.getKey(), condition, header, footer));
                    }
                }
            }

            headerTopPadding = getInt(hfSection, "header-top-padding", 0);

            // --- tablist-name-formatting ---
            Map<String, Object> tnf = getSection(root, "tablist-name-formatting");
            tablistFormattingEnabled = getBoolean(tnf, "enabled", true);
            tablistDisableCondition = getString(tnf, "disable-condition", "");

            // --- adventurefriend-social ---
            Map<String, Object> afSocial = getSection(root, "adventurefriend-social");
            socialRelationsEnabled = getBoolean(afSocial, "enabled", true);
            socialFriendPrefix = getString(afSocial, "friend-prefix", socialFriendPrefix);
            socialRequestPrefix = getString(afSocial, "request-prefix", socialRequestPrefix);
            socialBlockedPrefix = getString(afSocial, "blocked-prefix", socialBlockedPrefix);

            // --- scoreboard-teams (sorting) ---
            Map<String, Object> teams = getSection(root, "scoreboard-teams");
            if (getBoolean(teams, "enabled", true)) {
                sortingTypes = getStringList(teams, "sorting-types");
                caseSensitiveSorting = getBoolean(teams, "case-sensitive-sorting", true);
            }

            // --- global-playerlist ---
            Map<String, Object> gpl = getSection(root, "global-playerlist");
            globalPlayerListEnabled = getBoolean(gpl, "enabled", false);
            displayOthersAsSpectators = getBoolean(gpl, "display-others-as-spectators", false);
            displayVanishedAsSpectators = getBoolean(gpl, "display-vanished-players-as-spectators", true);
            isolateUnlistedServers = getBoolean(gpl, "isolate-unlisted-servers", false);
            updateLatency = getBoolean(gpl, "update-latency", false);
            spyServers = getStringList(gpl, "spy-servers");
            serverGroups.clear();
            Map<String, Object> sg = getSection(gpl, "server-groups");
            for (Map.Entry<String, Object> entry : sg.entrySet()) {
                if (entry.getValue() instanceof List) {
                    serverGroups.put(entry.getKey(), ((List<?>) entry.getValue()).stream()
                            .map(Object::toString).toList());
                }
            }

            // --- craftengine ---
            Map<String, Object> ce = getSection(root, "craftengine");
            craftEngineEnabled = getBoolean(ce, "enable-integration", craftEngineEnabled);
            displayCustomItem = getBoolean(ce, "display-custom-item", displayCustomItem);
            emojiSupport = getBoolean(ce, "emoji-support", emojiSupport);
            maxTextureCacheSize = getInt(ce, "max-texture-cache-size", maxTextureCacheSize);
            ceChannel = getString(ce, "channel", ceChannel);

            // --- luckperms ---
            Map<String, Object> lp = getSection(root, "luckperms");
            luckPermsEnabled = getBoolean(lp, "enable", luckPermsEnabled);
            defaultWeight = getInt(lp, "default-weight", defaultWeight);
            weightCacheDuration = getInt(lp, "cache-duration", weightCacheDuration);

            // --- performance ---
            Map<String, Object> perf = getSection(root, "performance");
            throttleMs = getInt(perf, "throttle-ms", (int) throttleMs);
            textureCacheDuration = getInt(perf, "texture-cache-duration", textureCacheDuration);
            updateInterval = getInt(perf, "update-interval", updateInterval);

            // --- placeholders ---
            Map<String, Object> ph = getSection(root, "placeholders");
            dateFormat = getString(ph, "date-format", dateFormat);
            timeFormat = getString(ph, "time-format", timeFormat);

            // --- conditions ---
            conditions.clear();
            Map<String, Object> condSection = getSection(root, "conditions");
            for (Map.Entry<String, Object> entry : condSection.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    conditions.put(entry.getKey(), (Map<String, Object>) entry.getValue());
                }
            }

            // --- placeholder-output-replacements ---
            outputReplacements.clear();
            Map<String, Object> replSection = getSection(root, "placeholder-output-replacements");
            for (Map.Entry<String, Object> entry : replSection.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, String> rMap = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> r : ((Map<String, Object>) entry.getValue()).entrySet()) {
                        rMap.put(r.getKey(), r.getValue() != null ? r.getValue().toString() : "");
                    }
                    outputReplacements.put(entry.getKey(), rMap);
                }
            }

            // --- animations ---
            animations.clear();
            Map<String, Object> animSection = getSection(root, "animations");
            for (Map.Entry<String, Object> entry : animSection.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    animations.put(entry.getKey(), (Map<String, Object>) entry.getValue());
                }
            }

            // --- placeholder-refresh-intervals ---
            defaultRefreshInterval = getInt(getSection(root, "placeholder-refresh-intervals"), "default-refresh-interval", 500);

            // --- customnameplates-compat ---
            Map<String, Object> cnp = getSection(root, "customnameplates-compat");
            cnpCompatEnabled = getBoolean(cnp, "enabled", true);

            // --- debug ---
            debug = getBoolean(root, "debug", false);

            logger.info("[AdventureTAB] 配置加载成功 (设计: {}, 排序: {}, 全局列表: {}, 动画: {})",
                    headerFooterDesigns.size(), sortingTypes.size(),
                    globalPlayerListEnabled ? "启用" : "禁用", animations.size());
        } catch (Exception e) {
            logger.error("[AdventureTAB] 配置加载失败，使用默认配置", e);
        }
    }

    private void saveDefault(Path file, String resource) {
        try {
            Files.createDirectories(file.getParent());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
                if (is != null) Files.copy(is, file);
            }
        } catch (Exception e) {
            logger.error("[AdventureTAB] 无法保存默认配置: {}", resource, e);
        }
    }

    // --- Utility methods ---
    @SuppressWarnings("unchecked")
    private Map<String, Object> getSection(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return new LinkedHashMap<>();
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? val.toString() : def;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        return def;
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List) {
            return ((List<?>) val).stream().map(Object::toString).toList();
        }
        return new ArrayList<>();
    }

    // ======== Getters ========

    // Header/Footer
    public List<HeaderFooterDesign> getHeaderFooterDesigns() { return headerFooterDesigns; }
    public int getHeaderTopPadding() { return headerTopPadding; }

    // Tablist formatting
    public boolean isTablistFormattingEnabled() { return tablistFormattingEnabled; }
    public String getTablistDisableCondition() { return tablistDisableCondition; }
    public boolean isSocialRelationsEnabled() { return socialRelationsEnabled; }
    public String getSocialFriendPrefix() { return socialFriendPrefix; }
    public String getSocialRequestPrefix() { return socialRequestPrefix; }
    public String getSocialBlockedPrefix() { return socialBlockedPrefix; }

    // Sorting
    public List<String> getSortingTypes() { return sortingTypes; }
    public boolean isCaseSensitiveSorting() { return caseSensitiveSorting; }

    // Global PlayerList
    public boolean isGlobalPlayerListEnabled() { return globalPlayerListEnabled; }
    public boolean isDisplayOthersAsSpectators() { return displayOthersAsSpectators; }
    public boolean isDisplayVanishedAsSpectators() { return displayVanishedAsSpectators; }
    public boolean isIsolateUnlistedServers() { return isolateUnlistedServers; }
    public boolean isUpdateLatency() { return updateLatency; }
    public List<String> getSpyServers() { return spyServers; }
    public Map<String, List<String>> getServerGroups() { return serverGroups; }

    // CraftEngine
    public boolean isCraftEngineEnabled() { return craftEngineEnabled; }
    public boolean isDisplayCustomItem() { return displayCustomItem; }
    public boolean isEmojiSupport() { return emojiSupport; }
    public int getMaxTextureCacheSize() { return maxTextureCacheSize; }
    public String getCeChannel() { return ceChannel; }

    // LuckPerms
    public boolean isLuckPermsEnabled() { return luckPermsEnabled; }
    public int getDefaultWeight() { return defaultWeight; }
    public int getWeightCacheDuration() { return weightCacheDuration; }

    // Performance
    public long getThrottleMs() { return throttleMs; }
    public int getTextureCacheDuration() { return textureCacheDuration; }
    public int getUpdateInterval() { return updateInterval; }

    // Placeholders
    public String getDateFormat() { return dateFormat; }
    public String getTimeFormat() { return timeFormat; }
    public Map<String, Map<String, Object>> getConditions() { return conditions; }
    public Map<String, Map<String, String>> getOutputReplacements() { return outputReplacements; }
    public Map<String, Map<String, Object>> getAnimations() { return animations; }
    public int getDefaultRefreshInterval() { return defaultRefreshInterval; }

    // CustomNameplates
    public boolean isCnpCompatEnabled() { return cnpCompatEnabled; }

    // Debug
    public boolean isDebug() { return debug; }

    public Path getDataDirectory() { return dataDirectory; }

    /**
     * Header/Footer 设计数据类
     */
    public record HeaderFooterDesign(String name, String displayCondition, List<String> header, List<String> footer) {}
}
