package cn.playcraft.adventuretab.placeholder;

import cn.playcraft.adventuretab.config.ConfigManager;
import cn.playcraft.adventuretab.craftengine.CraftEngineHook;
import cn.playcraft.adventuretab.permission.LuckPermsHook;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 占位符管理器 - 对标 TAB 的 PlaceholderManagerImpl
 * - 内置占位符: %online%, %player%, %server%, %ping%, %group%, %date%, %time%, %max_players%
 * - LuckPerms 占位符: %luckperms-prefix%, %luckperms-suffix%, %luckperms-primary-group%, %vault_prefix%
 * - 条件占位符: %condition:name%
 * - 动画占位符: %animation:name%
 * - 输出替换: placeholder-output-replacements
 * - 自定义占位符刷新间隔
 */
public class PlaceholderManager {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    private final ProxyServer server;
    private final Logger logger;
    private final LuckPermsHook luckPermsHook;
    private final ConfigManager configManager;
    private AnimationManager animationManager;
    private CraftEngineHook craftEngineHook;

    // 条件占位符: name -> Condition
    private final Map<String, ConditionPlaceholder> conditionPlaceholders = new HashMap<>();
    // 输出替换: placeholder_with_percent -> { original -> replacement }
    private final Map<String, Map<String, String>> outputReplacements = new HashMap<>();

    public PlaceholderManager(ProxyServer server, Logger logger, LuckPermsHook luckPermsHook, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.luckPermsHook = luckPermsHook;
        this.configManager = configManager;
    }

    public void init() {
        // 加载条件占位符
        conditionPlaceholders.clear();
        Map<String, Map<String, Object>> conditions = configManager.getConditions();
        if (conditions != null) {
            for (Map.Entry<String, Map<String, Object>> entry : conditions.entrySet()) {
                try {
                    Map<String, Object> def = entry.getValue();
                    @SuppressWarnings("unchecked")
                    List<String> condList = (List<String>) def.getOrDefault("conditions", List.of());
                    String trueVal = String.valueOf(def.getOrDefault("true", ""));
                    String falseVal = String.valueOf(def.getOrDefault("false", ""));
                    String combined = String.join(";", condList);
                    conditionPlaceholders.put(entry.getKey(), new ConditionPlaceholder(
                            new Condition(combined, this), trueVal, falseVal));
                } catch (Exception e) {
                    logger.warn("[AdventureTAB] 条件占位符 '{}' 加载失败: {}", entry.getKey(), e.getMessage());
                }
            }
        }

        // 加载输出替换
        outputReplacements.clear();
        Map<String, Map<String, String>> replacements = configManager.getOutputReplacements();
        if (replacements != null) {
            outputReplacements.putAll(replacements);
        }

        logger.info("[AdventureTAB] 占位符管理器已初始化 (条件: {}, 输出替换: {})",
                conditionPlaceholders.size(), outputReplacements.size());
    }

    public void setAnimationManager(AnimationManager animationManager) {
        this.animationManager = animationManager;
    }

    public void setCraftEngineHook(CraftEngineHook craftEngineHook) {
        this.craftEngineHook = craftEngineHook;
    }

    /**
     * 替换文本中所有 %placeholder% 占位符
     */
    public String replace(String input, Player player) {
        if (input == null || !input.contains("%")) return input;

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String resolved = resolve(key, player);
            // 输出替换
            resolved = applyOutputReplacement("%" + key + "%", resolved);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析单个占位符 key (不含 %%)
     */
    private String resolve(String key, Player player) {
        // 内置占位符
        switch (key.toLowerCase()) {
            case "online", "velocity_total_players":
                return String.valueOf(server.getPlayerCount());
            case "max_players":
                return String.valueOf(server.getConfiguration().getShowMaxPlayers());
            case "player", "player_name":
                return player.getUsername();
            case "server", "player_server":
                return player.getCurrentServer().map(c -> c.getServerInfo().getName()).orElse("");
            case "ping", "player_ping":
                return String.valueOf(player.getPing());
            case "group", "player_group":
                return luckPermsHook.getGroupDisplayName(player);
            case "date":
                return LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                        configManager.getDateFormat()));
            case "time":
                return LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                        configManager.getTimeFormat()));
            case "staffonline": {
                long count = server.getAllPlayers().stream()
                        .filter(p -> p.hasPermission("adventuretab.staff")).count();
                return String.valueOf(count);
            }
            case "memory-used":
                return String.valueOf((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576);
            case "memory-max":
                return String.valueOf(Runtime.getRuntime().maxMemory() / 1048576);
        }

        // LuckPerms 占位符
        if (key.equalsIgnoreCase("luckperms-prefix") || key.equalsIgnoreCase("vault_prefix")) {
            return luckPermsHook.getPrefix(player);
        }
        if (key.equalsIgnoreCase("luckperms-suffix") || key.equalsIgnoreCase("vault_suffix")) {
            return luckPermsHook.getSuffix(player);
        }
        if (key.equalsIgnoreCase("luckperms-primary-group")) {
            return luckPermsHook.getGroupDisplayName(player);
        }

        // 条件占位符: condition:name
        if (key.startsWith("condition:")) {
            String condName = key.substring("condition:".length());
            ConditionPlaceholder cp = conditionPlaceholders.get(condName);
            if (cp != null) {
                String raw = cp.condition.isMet(player) ? cp.trueValue : cp.falseValue;
                return replace(raw, player); // 递归解析
            }
            return "";
        }

        // 动画占位符: animation:name
        if (key.startsWith("animation:")) {
            String animName = key.substring("animation:".length());
            if (animationManager != null) {
                return animationManager.getCurrentFrame(animName);
            }
            return "";
        }

        // 服务器在线人数: server_online_<servername>
        if (key.startsWith("server_online_")) {
            String serverName = key.substring("server_online_".length());
            return String.valueOf(server.getServer(serverName)
                    .map(s -> s.getPlayersConnected().size()).orElse(0));
        }

        // PAPI 桥接: 查询从 Spigot 转发的 PlaceholderAPI 值
        if (craftEngineHook != null) {
            String papiValue = craftEngineHook.getPapiValue(player.getUniqueId(), key);
            if (papiValue != null) {
                return papiValue;
            }
        }

        if (key.equalsIgnoreCase("adventureprefix_prefix")) {
            return "";
        }

        // 未识别的占位符原样返回
        return "%" + key + "%";
    }

    /**
     * 应用输出替换
     */
    private String applyOutputReplacement(String placeholderKey, String value) {
        Map<String, String> replacements = outputReplacements.get(placeholderKey);
        if (replacements == null) return value;
        String replacement = replacements.get(value);
        return replacement != null ? replacement : value;
    }

    private record ConditionPlaceholder(Condition condition, String trueValue, String falseValue) {}
}
