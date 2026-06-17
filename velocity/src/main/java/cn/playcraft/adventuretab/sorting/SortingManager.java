package cn.playcraft.adventuretab.sorting;

import cn.playcraft.adventuretab.permission.LuckPermsHook;
import cn.playcraft.adventuretab.placeholder.PlaceholderManager;
import cn.playcraft.adventuretab.sorting.types.GroupsSorting;
import cn.playcraft.adventuretab.sorting.types.PlaceholderSorting;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排序管理器 - 对标 TAB 的 Sorting
 * 支持多排序维度链式排序:
 * - GROUPS:owner,admin,mod,default
 * - PLACEHOLDER_A_TO_Z:%player%
 * - PLACEHOLDER_Z_TO_A:%player%
 * - PLACEHOLDER_LOW_TO_HIGH:%ping%
 * - PLACEHOLDER_HIGH_TO_LOW:%ping%
 *
 * 排序键 = 各维度的 getChars() 拼接后按字典序排
 */
public class SortingManager {

    private final Logger logger;
    private final List<SortingType> sortingTypes = new ArrayList<>();
    private final Map<UUID, String> sortKeyCache = new ConcurrentHashMap<>();

    public SortingManager(Logger logger) {
        this.logger = logger;
    }

    public void load(List<String> sortingConfig, LuckPermsHook luckPermsHook, PlaceholderManager placeholderManager) {
        sortingTypes.clear();
        sortKeyCache.clear();

        if (sortingConfig == null || sortingConfig.isEmpty()) {
            logger.info("[AdventureTAB] 未配置排序规则");
            return;
        }

        for (String element : sortingConfig) {
            String[] parts = element.split(":", 2);
            String type = parts[0].toUpperCase().trim();
            String options = parts.length > 1 ? parts[1].trim() : "";

            switch (type) {
                case "GROUPS" -> sortingTypes.add(new GroupsSorting(options, luckPermsHook));
                case "PLACEHOLDER_A_TO_Z" -> sortingTypes.add(
                        new PlaceholderSorting(options, PlaceholderSorting.Mode.A_TO_Z, placeholderManager));
                case "PLACEHOLDER_Z_TO_A" -> sortingTypes.add(
                        new PlaceholderSorting(options, PlaceholderSorting.Mode.Z_TO_A, placeholderManager));
                case "PLACEHOLDER_LOW_TO_HIGH" -> sortingTypes.add(
                        new PlaceholderSorting(options, PlaceholderSorting.Mode.LOW_TO_HIGH, placeholderManager));
                case "PLACEHOLDER_HIGH_TO_LOW" -> sortingTypes.add(
                        new PlaceholderSorting(options, PlaceholderSorting.Mode.HIGH_TO_LOW, placeholderManager));
                default -> logger.warn("[AdventureTAB] 未知排序类型: {}", type);
            }
        }

        logger.info("[AdventureTAB] 已加载 {} 个排序维度", sortingTypes.size());
    }

    /**
     * 计算玩家的排序键
     */
    public String getSortKey(Player player) {
        StringBuilder sb = new StringBuilder();
        for (SortingType type : sortingTypes) {
            sb.append(type.getChars(player));
        }
        String key = sb.toString();
        sortKeyCache.put(player.getUniqueId(), key);
        return key;
    }

    /**
     * 获取排序的 listOrder 值
     * 将排序键转换为 int，字典序靠前的 listOrder 越小
     */
    public int getListOrder(Player player) {
        String key = getSortKey(player);
        // 基于哈希生成 listOrder，保证相同排序键得到相同结果
        return key.hashCode();
    }

    /**
     * 比较两个玩家的排序顺序
     * 返回负数表示 a 排在 b 前面
     */
    public int compare(Player a, Player b) {
        String keyA = getSortKey(a);
        String keyB = getSortKey(b);
        return keyA.compareTo(keyB);
    }

    public void removePlayer(UUID uuid) {
        sortKeyCache.remove(uuid);
    }

    public void clear() {
        sortKeyCache.clear();
    }
}
