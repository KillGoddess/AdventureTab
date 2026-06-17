package cn.playcraft.adventuretab.sorting;

import com.velocitypowered.api.proxy.Player;

/**
 * 排序类型基础接口 - 对标 TAB 的 SortingType
 */
public interface SortingType {

    /**
     * 获取玩家在此排序维度下的排序字符串
     * 返回的字符串将与其他类型的字符串拼接后按字典序排序
     */
    String getChars(Player player);
}
