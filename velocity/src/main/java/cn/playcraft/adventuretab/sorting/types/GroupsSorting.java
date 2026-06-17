package cn.playcraft.adventuretab.sorting.types;

import cn.playcraft.adventuretab.permission.LuckPermsHook;
import cn.playcraft.adventuretab.sorting.SortingType;
import com.velocitypowered.api.proxy.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GROUPS 排序 - 按配置的组列表顺序排序
 * 格式: GROUPS:owner,admin,mod,helper,builder,vip,default
 */
public class GroupsSorting implements SortingType {

    private final Map<String, Integer> groupOrder = new HashMap<>();
    private final int defaultIndex;
    private final LuckPermsHook luckPermsHook;

    public GroupsSorting(String options, LuckPermsHook luckPermsHook) {
        this.luckPermsHook = luckPermsHook;
        List<String> groups = Arrays.asList(options.split(","));
        for (int i = 0; i < groups.size(); i++) {
            groupOrder.put(groups.get(i).trim().toLowerCase(), i);
        }
        defaultIndex = groups.size();
    }

    @Override
    public String getChars(Player player) {
        String group = luckPermsHook.getPrimaryGroup(player).toLowerCase();
        int index = groupOrder.getOrDefault(group, defaultIndex);
        // 补齐到3位数，确保字典序正确
        return String.format("%03d", index);
    }
}
