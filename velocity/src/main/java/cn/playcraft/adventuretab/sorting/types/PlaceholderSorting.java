package cn.playcraft.adventuretab.sorting.types;

import cn.playcraft.adventuretab.placeholder.PlaceholderManager;
import cn.playcraft.adventuretab.sorting.SortingType;
import com.velocitypowered.api.proxy.Player;

/**
 * 占位符排序系列:
 * - PLACEHOLDER_A_TO_Z:%placeholder%
 * - PLACEHOLDER_Z_TO_A:%placeholder%
 * - PLACEHOLDER_LOW_TO_HIGH:%placeholder%
 * - PLACEHOLDER_HIGH_TO_LOW:%placeholder%
 */
public class PlaceholderSorting implements SortingType {

    public enum Mode { A_TO_Z, Z_TO_A, LOW_TO_HIGH, HIGH_TO_LOW }

    private final String placeholder;
    private final Mode mode;
    private final PlaceholderManager placeholderManager;

    public PlaceholderSorting(String placeholder, Mode mode, PlaceholderManager placeholderManager) {
        this.placeholder = placeholder;
        this.mode = mode;
        this.placeholderManager = placeholderManager;
    }

    @Override
    public String getChars(Player player) {
        String value = placeholderManager.replace(placeholder, player);

        return switch (mode) {
            case A_TO_Z -> padString(value, false);
            case Z_TO_A -> padString(value, true);
            case LOW_TO_HIGH -> padNumber(value, false);
            case HIGH_TO_LOW -> padNumber(value, true);
        };
    }

    private String padString(String value, boolean reverse) {
        StringBuilder sb = new StringBuilder();
        String lower = value.toLowerCase();
        for (int i = 0; i < Math.min(lower.length(), 12); i++) {
            char c = lower.charAt(i);
            if (reverse) {
                sb.append((char) ('z' - c + 'a'));
            } else {
                sb.append(c);
            }
        }
        // 用空格填充以确保长度一致
        while (sb.length() < 12) {
            sb.append(reverse ? 'z' : ' ');
        }
        return sb.toString();
    }

    private String padNumber(String value, boolean reverse) {
        try {
            double num = Double.parseDouble(value);
            if (reverse) num = -num;
            // 偏移使所有值为正 (支持 -999999 到 999999)
            long shifted = (long) ((num + 1000000) * 100);
            return String.format("%012d", shifted);
        } catch (NumberFormatException e) {
            return reverse ? "999999999999" : "000000000000";
        }
    }
}
