package cn.playcraft.adventuretab.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * groups.yml 配置加载器 - 对标 TAB 的 groups.yml
 * 支持 per-group / per-server 属性:
 *   groupName:
 *     tabprefix: "&a[Admin] "
 *     customtabname: "%player%"
 *     tabsuffix: ""
 *   _DEFAULT_:
 *     tabprefix: "%luckperms-prefix%"
 *   per-server:
 *     lobby:
 *       Admin:
 *         tabprefix: "&a[Lobby-Admin] "
 */
public class GroupsConfig {

    private final Path dataDirectory;
    private final Logger logger;

    // group -> property -> value
    private final Map<String, Map<String, String>> groupProperties = new LinkedHashMap<>();
    // server -> group -> property -> value
    private final Map<String, Map<String, Map<String, String>>> perServerProperties = new LinkedHashMap<>();

    public GroupsConfig(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    @SuppressWarnings("unchecked")
    public void load() {
        groupProperties.clear();
        perServerProperties.clear();

        Path file = dataDirectory.resolve("groups.yml");
        if (!Files.exists(file)) {
            saveDefault(file);
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> root;
            try (InputStream is = Files.newInputStream(file)) {
                root = yaml.load(is);
            }
            if (root == null) {
                logger.warn("[AdventureTAB] groups.yml 为空");
                return;
            }

            for (Map.Entry<String, Object> entry : root.entrySet()) {
                String key = entry.getKey();

                if ("per-server".equalsIgnoreCase(key)) {
                    // per-server section
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> servers = (Map<String, Object>) entry.getValue();
                        for (Map.Entry<String, Object> serverEntry : servers.entrySet()) {
                            String serverName = serverEntry.getKey();
                            if (serverEntry.getValue() instanceof Map) {
                                Map<String, Object> groups = (Map<String, Object>) serverEntry.getValue();
                                for (Map.Entry<String, Object> groupEntry : groups.entrySet()) {
                                    String groupName = groupEntry.getKey();
                                    if (groupEntry.getValue() instanceof Map) {
                                        Map<String, String> props = flattenToString((Map<String, Object>) groupEntry.getValue());
                                        perServerProperties
                                                .computeIfAbsent(serverName.toLowerCase(Locale.ROOT), k -> new LinkedHashMap<>())
                                                .put(groupName.toLowerCase(Locale.ROOT), props);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // group section
                    if (entry.getValue() instanceof Map) {
                        Map<String, String> props = flattenToString((Map<String, Object>) entry.getValue());
                        groupProperties.put(key.toLowerCase(Locale.ROOT), props);
                    }
                }
            }

            logger.info("[AdventureTAB] groups.yml 已加载 ({} 组, {} 服务器覆盖)",
                    groupProperties.size(), perServerProperties.size());
        } catch (Exception e) {
            logger.error("[AdventureTAB] groups.yml 加载失败", e);
        }
    }

    /**
     * 获取属性值，优先级: per-server > group > _DEFAULT_
     */
    public String getProperty(String group, String property, String server) {
        String groupLc = group != null ? group.toLowerCase(Locale.ROOT) : "";
        String serverLc = server != null ? server.toLowerCase(Locale.ROOT) : "";
        // 1. per-server > group 覆盖
        if (!serverLc.isEmpty()) {
            Map<String, Map<String, String>> serverGroups = perServerProperties.get(serverLc);
            if (serverGroups != null) {
                Map<String, String> props = serverGroups.get(groupLc);
                if (props != null && props.containsKey(property)) {
                    return props.get(property);
                }
                // per-server _DEFAULT_
                props = serverGroups.get("_default_");
                if (props != null && props.containsKey(property)) {
                    return props.get(property);
                }
            }
        }

        // 2. group 级别
        Map<String, String> props = groupProperties.get(groupLc);
        if (props != null && props.containsKey(property)) {
            return props.get(property);
        }

        // 3. _DEFAULT_
        Map<String, String> defaults = groupProperties.get("_default_");
        if (defaults != null && defaults.containsKey(property)) {
            return defaults.get(property);
        }

        return null;
    }

    private Map<String, String> flattenToString(Map<String, Object> map) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() != null) {
                result.put(e.getKey(), e.getValue().toString());
            }
        }
        return result;
    }

    private void saveDefault(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("groups.yml")) {
                if (is != null) {
                    Files.copy(is, file);
                }
            }
        } catch (Exception e) {
            logger.error("[AdventureTAB] 无法保存默认 groups.yml", e);
        }
    }

    public Map<String, Map<String, String>> getGroupProperties() {
        return Collections.unmodifiableMap(groupProperties);
    }
}
