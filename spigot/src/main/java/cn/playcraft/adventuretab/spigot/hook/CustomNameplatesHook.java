package cn.playcraft.adventuretab.spigot.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * CustomNameplates BossBar 兼容挂钩
 * 通过反射读取 CNP 的 BossBarSender.latestContent
 * 路径: CustomNameplates.getInstance()
 *       → bossBarManager (BossBarManagerImpl)
 *       → senders (ConcurrentHashMap<UUID, BossBarDisplayController>)
 *       → BossBarDisplayController.senders (BossBarSender[])
 *       → BossBarSender.latestContent + BossBarSender.isShown
 */
public class CustomNameplatesHook {

    private final JavaPlugin plugin;
    private boolean available = false;

    // 反射缓存
    private Object cnpInstance;
    private Field bossBarManagerField;
    private Field sendersMapField;         // BossBarManagerImpl.senders
    private Field controllerSendersField;  // BossBarDisplayController.senders
    private Field senderIsShownField;      // BossBarSender.isShown
    private Field senderLatestContentField; // BossBarSender.latestContent

    public CustomNameplatesHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (Bukkit.getPluginManager().getPlugin("CustomNameplates") == null) {
            plugin.getLogger().info("[AdventureTAB] CustomNameplates 未检测到");
            return;
        }

        try {
            // 获取 CustomNameplates 单例
            Class<?> cnpClass = Class.forName("net.momirealms.customnameplates.api.CustomNameplates");
            cnpInstance = cnpClass.getMethod("getInstance").invoke(null);
            if (cnpInstance == null) {
                plugin.getLogger().warning("[AdventureTAB] CustomNameplates 实例未初始化");
                return;
            }

            // bossBarManager 字段
            bossBarManagerField = cnpClass.getDeclaredField("bossBarManager");
            bossBarManagerField.setAccessible(true);

            // BossBarManagerImpl.senders
            Class<?> managerImplClass = Class.forName(
                    "net.momirealms.customnameplates.backend.feature.bossbar.BossBarManagerImpl");
            sendersMapField = managerImplClass.getDeclaredField("senders");
            sendersMapField.setAccessible(true);

            // BossBarDisplayController.senders (BossBarSender[])
            Class<?> controllerClass = Class.forName(
                    "net.momirealms.customnameplates.backend.feature.bossbar.BossBarDisplayController");
            controllerSendersField = controllerClass.getDeclaredField("senders");
            controllerSendersField.setAccessible(true);

            // BossBarSender.isShown + latestContent
            Class<?> senderClass = Class.forName(
                    "net.momirealms.customnameplates.backend.feature.bossbar.BossBarSender");
            senderIsShownField = senderClass.getDeclaredField("isShown");
            senderIsShownField.setAccessible(true);
            senderLatestContentField = senderClass.getDeclaredField("latestContent");
            senderLatestContentField.setAccessible(true);

            available = true;
            plugin.getLogger().info("[AdventureTAB] CustomNameplates BossBar 兼容已启用");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[AdventureTAB] CustomNameplates 兼容初始化失败 (版本可能不兼容)", e);
            available = false;
        }
    }

    /**
     * 获取玩家当前正在显示的 CNP BossBar 文本 (MiniMessage 格式)
     * 如果玩家没有活跃的 BossBar，返回 null
     */
    @SuppressWarnings("unchecked")
    public String getActiveBossBarText(Player player) {
        if (!available) return null;

        try {
            Object bossBarManager = bossBarManagerField.get(cnpInstance);
            if (bossBarManager == null) return null;

            Map<UUID, Object> senders = (Map<UUID, Object>) sendersMapField.get(bossBarManager);
            if (senders == null) return null;

            Object controller = senders.get(player.getUniqueId());
            if (controller == null) return null;

            Object[] bossBarSenders = (Object[]) controllerSendersField.get(controller);
            if (bossBarSenders == null) return null;

            // 找到第一个正在显示的 BossBarSender
            StringBuilder combinedText = new StringBuilder();
            for (Object sender : bossBarSenders) {
                boolean isShown = senderIsShownField.getBoolean(sender);
                if (isShown) {
                    String content = (String) senderLatestContentField.get(sender);
                    if (content != null && !content.isEmpty()) {
                        if (!combinedText.isEmpty()) combinedText.append("\n");
                        combinedText.append(content);
                    }
                }
            }

            return combinedText.isEmpty() ? null : combinedText.toString();
        } catch (Exception e) {
            // 静默失败，避免刷日志
            return null;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
