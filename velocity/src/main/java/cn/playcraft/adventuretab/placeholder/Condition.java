package cn.playcraft.adventuretab.placeholder;

import com.velocitypowered.api.proxy.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件系统 - 支持 TAB 风格的条件表达式
 * 格式: "%placeholder%=value"  或  "%placeholder%!=value"  或  "%placeholder%>value"  或  "%placeholder%<value"
 * 多条件用 ";" 连接 (AND 逻辑)
 */
public class Condition {

    private final List<SubCondition> subConditions;
    private final PlaceholderManager placeholderManager;

    public Condition(String expression, PlaceholderManager placeholderManager) {
        this.placeholderManager = placeholderManager;
        this.subConditions = parse(expression);
    }

    public boolean isMet(Player player) {
        for (SubCondition sub : subConditions) {
            if (!sub.evaluate(player, placeholderManager)) return false;
        }
        return true;
    }

    private List<SubCondition> parse(String expression) {
        List<SubCondition> list = new ArrayList<>();
        if (expression == null || expression.isEmpty()) return list;

        String[] parts = expression.split(";");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.contains("!=")) {
                String[] kv = part.split("!=", 2);
                list.add(new SubCondition(kv[0].trim(), kv.length > 1 ? kv[1].trim() : "", Operator.NOT_EQUALS));
            } else if (part.contains(">=")) {
                String[] kv = part.split(">=", 2);
                list.add(new SubCondition(kv[0].trim(), kv.length > 1 ? kv[1].trim() : "", Operator.GREATER_OR_EQUAL));
            } else if (part.contains("<=")) {
                String[] kv = part.split("<=", 2);
                list.add(new SubCondition(kv[0].trim(), kv.length > 1 ? kv[1].trim() : "", Operator.LESS_OR_EQUAL));
            } else if (part.contains(">")) {
                String[] kv = part.split(">", 2);
                list.add(new SubCondition(kv[0].trim(), kv.length > 1 ? kv[1].trim() : "", Operator.GREATER));
            } else if (part.contains("<")) {
                String[] kv = part.split("<", 2);
                list.add(new SubCondition(kv[0].trim(), kv.length > 1 ? kv[1].trim() : "", Operator.LESS));
            } else if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                list.add(new SubCondition(kv[0].trim(), kv.length > 1 ? kv[1].trim() : "", Operator.EQUALS));
            }
        }
        return list;
    }

    public boolean isEmpty() {
        return subConditions.isEmpty();
    }

    private enum Operator {
        EQUALS, NOT_EQUALS, GREATER, LESS, GREATER_OR_EQUAL, LESS_OR_EQUAL
    }

    private record SubCondition(String left, String right, Operator operator) {
        boolean evaluate(Player player, PlaceholderManager pm) {
            String leftVal = pm.replace(left, player);
            String rightVal = pm.replace(right, player);

            return switch (operator) {
                case EQUALS -> leftVal.equalsIgnoreCase(rightVal);
                case NOT_EQUALS -> !leftVal.equalsIgnoreCase(rightVal);
                case GREATER, LESS, GREATER_OR_EQUAL, LESS_OR_EQUAL -> compareNumeric(leftVal, rightVal);
            };
        }

        private boolean compareNumeric(String leftVal, String rightVal) {
            try {
                double l = Double.parseDouble(leftVal);
                double r = Double.parseDouble(rightVal);
                return switch (operator) {
                    case GREATER -> l > r;
                    case LESS -> l < r;
                    case GREATER_OR_EQUAL -> l >= r;
                    case LESS_OR_EQUAL -> l <= r;
                    default -> false;
                };
            } catch (NumberFormatException e) {
                return leftVal.compareTo(rightVal) > 0 == (operator == Operator.GREATER || operator == Operator.GREATER_OR_EQUAL);
            }
        }
    }
}
