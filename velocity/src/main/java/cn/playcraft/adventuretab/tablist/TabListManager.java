package cn.playcraft.adventuretab.tablist;

import cn.playcraft.adventuretab.cache.CacheManager;
import cn.playcraft.adventuretab.config.ConfigManager;
import cn.playcraft.adventuretab.config.GroupsConfig;
import cn.playcraft.adventuretab.craftengine.CraftEngineHook;
import cn.playcraft.adventuretab.permission.LuckPermsHook;
import cn.playcraft.adventuretab.placeholder.Condition;
import cn.playcraft.adventuretab.placeholder.PlaceholderManager;
import cn.playcraft.adventuretab.sorting.SortingManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TabListManager {

    private final ProxyServer server;
    private final Logger logger;
    private ConfigManager configManager;
    private CacheManager cacheManager;
    private final LuckPermsHook luckPermsHook;
    private final CraftEngineHook craftEngineHook;

    private PlaceholderManager placeholderManager;
    private SortingManager sortingManager;
    private GroupsConfig groupsConfig;

    private final MiniMessage miniMessage = MiniMessage.builder().strict(false).build();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    // Legacy &色码 → MiniMessage 标签 映射
    private static final Map<Character, String> LEGACY_TO_MM;
    static {
        Map<Character, String> m = new HashMap<>();
        m.put('0', "<black>"); m.put('1', "<dark_blue>"); m.put('2', "<dark_green>");
        m.put('3', "<dark_aqua>"); m.put('4', "<dark_red>"); m.put('5', "<dark_purple>");
        m.put('6', "<gold>"); m.put('7', "<gray>"); m.put('8', "<dark_gray>");
        m.put('9', "<blue>"); m.put('a', "<green>"); m.put('b', "<aqua>");
        m.put('c', "<red>"); m.put('d', "<light_purple>"); m.put('e', "<yellow>");
        m.put('f', "<white>"); m.put('l', "<bold>"); m.put('m', "<strikethrough>");
        m.put('n', "<underlined>"); m.put('o', "<italic>"); m.put('k', "<obfuscated>");
        m.put('r', "<reset>");
        LEGACY_TO_MM = Map.copyOf(m);
    }

    // 预编译的 Header/Footer 条件
    private final List<DesignWithCondition> compiledDesigns = new ArrayList<>();

    public TabListManager(ProxyServer server, Logger logger, ConfigManager configManager,
                          CacheManager cacheManager, LuckPermsHook luckPermsHook,
                          CraftEngineHook craftEngineHook) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
        this.cacheManager = cacheManager;
        this.luckPermsHook = luckPermsHook;
        this.craftEngineHook = craftEngineHook;
    }

    public void setPlaceholderManager(PlaceholderManager pm) { this.placeholderManager = pm; }
    public void setSortingManager(SortingManager sm) { this.sortingManager = sm; }
    public void setGroupsConfig(GroupsConfig gc) { this.groupsConfig = gc; }

    /**
     * 重载时刷新引用并重新编译设计条件
     */
    public void reload(ConfigManager configManager, CacheManager cacheManager) {
        this.configManager = configManager;
        this.cacheManager = cacheManager;
        compileDesigns();
    }

    /**
     * 编译 Header/Footer 设计条件
     */
    public void compileDesigns() {
        compiledDesigns.clear();
        for (ConfigManager.HeaderFooterDesign design : configManager.getHeaderFooterDesigns()) {
            Condition cond = null;
            if (design.displayCondition() != null && !design.displayCondition().isEmpty()) {
                cond = new Condition(design.displayCondition(), placeholderManager);
            }
            compiledDesigns.add(new DesignWithCondition(design, cond));
        }
    }

    // =================== 更新逻辑 ===================

    public void updateAll() {
        for (Player player : server.getAllPlayers()) {
            try { updatePlayer(player); }
            catch (Exception e) { logger.debug("[AdventureTAB] 更新玩家TabList失败: {}", player.getUsername(), e); }
        }
    }

    public void updatePlayer(Player viewer) {
        setHeaderFooter(viewer);
        updateEntries(viewer);
    }

    // =================== Header/Footer (多设计+条件) ===================

    public void setHeaderFooter(Player player) {
        ConfigManager.HeaderFooterDesign active = findActiveDesign(player);
        if (active == null) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            return;
        }
        String headerRaw = String.join("\n", active.header());
        String footerRaw = String.join("\n", active.footer());
        headerRaw = resolvePlaceholders(headerRaw, player);
        footerRaw = resolvePlaceholders(footerRaw, player);

        // Header 顶部填充: 添加空行将 TAB 内容下推，为 BossBar 留出空间
        int padding = configManager.getHeaderTopPadding();
        if (padding > 0) {
            headerRaw = "\n".repeat(padding) + headerRaw;
        }

        // CustomNameplates BossBar 兼容: 将 BossBar 文字嵌入 Header 顶部
        // 这样按 TAB 时，BossBar 内容显示在 TAB 覆盖层之上 (作为 Header 的一部分)
        if (configManager.isCnpCompatEnabled()) {
            String bossBarText = craftEngineHook.getBossBarText(player.getUniqueId());
            if (bossBarText != null && !bossBarText.isEmpty()) {
                // BossBar 文字置于 Header 最顶部，后面跟原始 Header
                headerRaw = bossBarText + "\n" + headerRaw;
            }
        }

        player.sendPlayerListHeaderAndFooter(parseComponent(headerRaw), parseComponent(footerRaw));
    }

    private ConfigManager.HeaderFooterDesign findActiveDesign(Player player) {
        for (DesignWithCondition dc : compiledDesigns) {
            if (dc.condition == null || dc.condition.isMet(player)) {
                return dc.design;
            }
        }
        return null;
    }

    // =================== TabList 条目 ===================

    private void updateEntries(Player viewer) {
        com.velocitypowered.api.proxy.player.TabList tabList = viewer.getTabList();
        Set<UUID> visibleUuids = new HashSet<>();

        List<Player> playersToShow = getVisiblePlayers(viewer);

        for (Player online : playersToShow) {
            UUID uuid = online.getUniqueId();
            visibleUuids.add(uuid);

            int sortOrder = getSortOrder(online);
            Component displayName = buildDisplayName(online, viewer);
            int latency = configManager.isUpdateLatency() ? (int) online.getPing() : 0;
            int gameMode = getGameMode(viewer, online);

            Optional<TabListEntry> existing = tabList.getEntry(uuid);
            if (existing.isPresent()) {
                existing.get().setDisplayName(displayName);
                existing.get().setListOrder(sortOrder);
            } else {
                try {
                    TabListEntry entry = TabListEntry.builder()
                            .tabList(tabList)
                            .profile(online.getGameProfile())
                            .displayName(displayName)
                            .latency(latency)
                            .gameMode(gameMode)
                            .listed(true)
                            .listOrder(sortOrder)
                            .build();
                    tabList.addEntry(entry);
                } catch (Exception e) {
                    logger.debug("[AdventureTAB] 添加条目失败: {}", online.getUsername(), e);
                }
            }
        }

        // 移除不可见玩家
        for (TabListEntry entry : List.copyOf(tabList.getEntries())) {
            UUID entryUuid = entry.getProfile().getId();
            if (!visibleUuids.contains(entryUuid)) {
                tabList.removeEntry(entryUuid);
            }
        }
    }

    // =================== 全局玩家列表 (服务器分组) ===================

    /**
     * 根据 global-playerlist 配置决定 viewer 可见哪些玩家
     */
    private List<Player> getVisiblePlayers(Player viewer) {
        if (!configManager.isGlobalPlayerListEnabled()) {
            // 全局列表未启用: 显示全部在线玩家
            return new ArrayList<>(server.getAllPlayers());
        }

        String viewerServer = viewer.getCurrentServer()
                .map(c -> c.getServerInfo().getName()).orElse("");
        String viewerGroup = getServerGroup(viewerServer);

        // spy-server: 能看到所有人
        if (configManager.getSpyServers().contains(viewerServer)) {
            return new ArrayList<>(server.getAllPlayers());
        }

        List<Player> visible = new ArrayList<>();
        for (Player p : server.getAllPlayers()) {
            String pServer = p.getCurrentServer()
                    .map(c -> c.getServerInfo().getName()).orElse("");
            String pGroup = getServerGroup(pServer);

            // 同一服务器组
            if (viewerGroup != null && viewerGroup.equals(pGroup)) {
                visible.add(p);
            }
            // 都不在任何组，且未启用隔离
            else if (viewerGroup == null && pGroup == null && !configManager.isIsolateUnlistedServers()) {
                visible.add(p);
            }
            // 同一个服务器
            else if (viewerServer.equalsIgnoreCase(pServer)) {
                visible.add(p);
            }
        }
        return visible;
    }

    /**
     * 查找服务器所属分组
     */
    private String getServerGroup(String serverName) {
        for (Map.Entry<String, List<String>> entry : configManager.getServerGroups().entrySet()) {
            for (String s : entry.getValue()) {
                if (s.equalsIgnoreCase(serverName)) return entry.getKey();
            }
        }
        return null;
    }

    private int getGameMode(Player viewer, Player target) {
        if (configManager.isDisplayOthersAsSpectators() && !viewer.equals(target)) return 3;
        return 0;
    }

    // =================== 排序 ===================

    private int getSortOrder(Player player) {
        if (sortingManager != null) {
            return sortingManager.getListOrder(player);
        }
        // fallback: LuckPerms weight
        int weight = luckPermsHook.getWeight(player);
        return Integer.MAX_VALUE - weight;
    }

    // =================== 显示名 (Per-Group Prefix/Name/Suffix) ===================

    /**
     * 构建玩家在 TabList 中的显示名称
     * 优先级: groups.yml per-group/per-server → LuckPerms prefix/suffix
     */
    private Component buildDisplayName(Player target, Player viewer) {
        if (!configManager.isTablistFormattingEnabled()) {
            return Component.text(target.getUsername());
        }

        String group = luckPermsHook.getPrimaryGroup(target);
        String serverName = target.getCurrentServer()
                .map(c -> c.getServerInfo().getName()).orElse("");

        // 从 groups.yml 加载属性
        String tabPrefix = resolveGroupProperty(group, "tabprefix", serverName);
        String customTabName = resolveGroupProperty(group, "customtabname", serverName);
        String tabSuffix = resolveGroupProperty(group, "tabsuffix", serverName);

        // 默认值
        if (tabPrefix == null) tabPrefix = luckPermsHook.getPrefix(target);
        if (customTabName == null) customTabName = target.getUsername();
        if (tabSuffix == null) tabSuffix = luckPermsHook.getSuffix(target);

        // 解析占位符
        String result = relationPrefix(viewer, target)
                + resolvePlaceholders(tabPrefix, target)
                + resolvePlaceholders(customTabName, target)
                + resolvePlaceholders(tabSuffix, target);

        // CraftEngine 物品展示
        if (craftEngineHook.isAvailable() && configManager.isDisplayCustomItem()) {
            String itemModel = craftEngineHook.getPlayerItemModel(target.getUniqueId());
            if (itemModel != null && !itemModel.startsWith("emoji:")) {
                String base64 = craftEngineHook.getTextureBase64(itemModel);
                if (base64 != null) {
                    result = "<img src=\"base64:" + base64 + "\"/> " + result;
                }
            }
        }

        return parseComponent(result);
    }

    private String relationPrefix(Player viewer, Player target) {
        if (!configManager.isSocialRelationsEnabled() || viewer.equals(target)) return "";
        byte flags = craftEngineHook.getSocialRelationFlags(viewer.getUniqueId(), target.getUniqueId());
        if ((flags & 0x04) != 0) return configManager.getSocialBlockedPrefix();
        if ((flags & 0x02) != 0) return configManager.getSocialRequestPrefix();
        if ((flags & 0x01) != 0) return configManager.getSocialFriendPrefix();
        return "";
    }

    private String resolveGroupProperty(String group, String property, String server) {
        if (groupsConfig != null) {
            return groupsConfig.getProperty(group, property, server);
        }
        return null;
    }

    // =================== 占位符 & 组件解析 ===================

    private String resolvePlaceholders(String input, Player player) {
        if (input == null) return "";
        if (placeholderManager != null) {
            return placeholderManager.replace(input, player);
        }
        // 简单 fallback
        return input
                .replace("%player%", player.getUsername())
                .replace("%online%", String.valueOf(server.getPlayerCount()))
                .replace("%server%", player.getCurrentServer()
                        .map(c -> c.getServerInfo().getName()).orElse(""));
    }

    private Component parseComponent(String input) {
        try {
            boolean hasLegacy = input.contains("&") || input.contains("§");
            boolean hasMM = input.contains("<") && input.contains(">");

            if (hasLegacy) {
                // 统一转为 MiniMessage 格式
                input = convertLegacyToMiniMessage(input);
            }

            if (hasLegacy || hasMM) {
                // MiniMessage (non-strict) 解析，未知标签会渲染为文本
                return miniMessage.deserialize(input);
            }

            return Component.text(input);
        } catch (Exception e) {
            // 最终兜底: legacy 解析
            try {
                return legacySerializer.deserialize(input.replace("§", "&"));
            } catch (Exception e2) {
                return Component.text(input);
            }
        }
    }

    /**
     * 将 legacy &色码/§色码 转换为 MiniMessage 标签
     */
    private String convertLegacyToMiniMessage(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = Character.toLowerCase(input.charAt(i + 1));
                // &# hex 色码: &#RRGGBB
                if (next == '#' && i + 7 < input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    sb.append("<color:#").append(hex).append(">");
                    i += 7;
                    continue;
                }
                String mm = LEGACY_TO_MM.get(next);
                if (mm != null) {
                    sb.append(mm);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public void removePlayer(UUID uuid) {
        if (sortingManager != null) sortingManager.removePlayer(uuid);
        craftEngineHook.removeBossBarText(uuid);
        craftEngineHook.removePapiCache(uuid);
        craftEngineHook.removeSocialRelations(uuid);
    }

    // =================== 内部数据类 ===================

    private record DesignWithCondition(ConfigManager.HeaderFooterDesign design, Condition condition) {}
}
