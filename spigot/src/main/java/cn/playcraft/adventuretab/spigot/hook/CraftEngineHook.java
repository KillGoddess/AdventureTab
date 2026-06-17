package cn.playcraft.adventuretab.spigot.hook;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CraftEngine 后端集成
 * - 检测 CraftEngine 是否已加载
 * - 从物品的 PersistentDataContainer 提取 item_model 标识
 * - 支持 CraftEngine 的 NamespacedKey 约定
 */
public class CraftEngineHook {

    private final JavaPlugin plugin;
    private boolean available = false;

    // CraftEngine 常见的 PDC Key
    private NamespacedKey ceItemModelKey;
    private NamespacedKey ceCustomIdKey;

    public CraftEngineHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        // 检测 CraftEngine 是否在服务端加载
        if (plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            available = true;
            ceItemModelKey = new NamespacedKey("craftengine", "item_model");
            ceCustomIdKey = new NamespacedKey("craftengine", "id");
            plugin.getLogger().info("[AdventureTAB] CraftEngine 已检测到");
        } else {
            available = false;
            plugin.getLogger().info("[AdventureTAB] CraftEngine 未找到，使用原版物品检测");
        }
    }

    /**
     * 从 ItemStack 提取物品模型标识
     * 优先级: CraftEngine item_model → CraftEngine id → Material 名
     */
    public String getItemModel(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item.getType().name();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 尝试 CraftEngine PDC
        if (available) {
            try {
                if (ceItemModelKey != null && pdc.has(ceItemModelKey, PersistentDataType.STRING)) {
                    return pdc.get(ceItemModelKey, PersistentDataType.STRING);
                }
                if (ceCustomIdKey != null && pdc.has(ceCustomIdKey, PersistentDataType.STRING)) {
                    return pdc.get(ceCustomIdKey, PersistentDataType.STRING);
                }
            } catch (Exception e) {
                // 静默降级
            }
        }

        // 检查 CustomModelData（通用自定义物品检测）
        if (meta.hasCustomModelData()) {
            return item.getType().name() + ":" + meta.getCustomModelData();
        }

        return item.getType().name();
    }

    public boolean isAvailable() { return available; }
}
