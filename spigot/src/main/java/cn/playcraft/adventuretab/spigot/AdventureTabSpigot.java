package cn.playcraft.adventuretab.spigot;

import cn.playcraft.adventuretab.spigot.config.SpigotConfigManager;
import cn.playcraft.adventuretab.spigot.hook.CraftEngineHook;
import cn.playcraft.adventuretab.spigot.hook.CustomNameplatesHook;
import cn.playcraft.adventuretab.spigot.listener.ItemChangeListener;
import cn.playcraft.adventuretab.spigot.messaging.VelocityMessenger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AdventureTAB 子服伴生插件
 * - 监听 CraftEngine 物品装备 / 表情事件
 * - 通过插件消息频道向 Velocity 推送数据
 * - CustomNameplates BossBar 兼容 (转发 BossBar 文字到 TAB Header)
 */
public class AdventureTabSpigot extends JavaPlugin implements Listener {

    private SpigotConfigManager configManager;
    private VelocityMessenger messenger;
    private CraftEngineHook craftEngineHook;
    private CustomNameplatesHook cnpHook;
    private BukkitTask bossBarSyncTask;
    private BukkitTask papiSyncTask;
    private boolean papiAvailable = false;

    // 缓存上一次发送的 BossBar 文字，避免重复发送
    private final Map<UUID, String> lastBossBarText = new HashMap<>();
    // 缓存上一次发送的 PAPI 值，仅在变化时发送
    private final Map<UUID, Map<String, String>> lastPapiValues = new HashMap<>();

    @Override
    public void onEnable() {
        new Metrics(this, 31171);
        // 1. 配置
        configManager = new SpigotConfigManager(this);
        configManager.load();

        // 2. 插件消息频道
        messenger = new VelocityMessenger(this, configManager);
        messenger.register();

        // 3. CraftEngine 挂钩
        craftEngineHook = new CraftEngineHook(this);
        craftEngineHook.init();

        // 4. CustomNameplates 挂钩
        cnpHook = new CustomNameplatesHook(this);
        // 延迟初始化，确保 CNP 已完全加载
        getServer().getScheduler().runTaskLater(this, () -> {
            cnpHook.init();
            if (cnpHook.isAvailable()) {
                startBossBarSync();
            }
        }, 40L); // 2秒后

        // 5. 事件监听
        getServer().getPluginManager().registerEvents(
                new ItemChangeListener(this, messenger, craftEngineHook), this);
        getServer().getPluginManager().registerEvents(this, this);

        // 6. PlaceholderAPI 桥接
        if (configManager.isPapiBridgeEnabled()) {
            getServer().getScheduler().runTaskLater(this, () -> {
                if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    papiAvailable = true;
                    startPapiSync();
                } else {
                    getLogger().info("[AdventureTAB] PlaceholderAPI 未检测到, PAPI 桥接已禁用");
                }
            }, 20L);
        }

        getLogger().info("[AdventureTAB-Spigot] v2.1.2 已启用"
                + (craftEngineHook.isAvailable() ? " (CraftEngine 已连接)" : ""));
    }

    /**
     * 启动 BossBar 文字定时同步到 Velocity
     * 每 10 tick (0.5秒) 检查一次每个在线玩家的 CNP BossBar 状态
     */
    private void startBossBarSync() {
        bossBarSyncTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                try {
                    String currentText = cnpHook.getActiveBossBarText(player);
                    String lastText = lastBossBarText.get(player.getUniqueId());

                    // 只在内容变化时发送
                    boolean changed = (currentText == null && lastText != null)
                            || (currentText != null && !currentText.equals(lastText));

                    if (changed) {
                        messenger.sendBossBarText(player, currentText);
                        if (currentText != null) {
                            lastBossBarText.put(player.getUniqueId(), currentText);
                        } else {
                            lastBossBarText.remove(player.getUniqueId());
                        }
                    }
                } catch (Exception e) {
                    // 静默忽略单个玩家的错误
                }
            }
        }, 40L, 10L); // 延迟40tick启动, 每10tick同步
        getLogger().info("[AdventureTAB] CNP BossBar 同步任务已启动");
    }

    /**
     * 启动 PlaceholderAPI 桥接定时同步
     * 定期解析配置中的 PAPI 占位符，仅在值变化时发送到 Velocity
     */
    private void startPapiSync() {
        List<String> placeholders = configManager.getPapiPlaceholders();
        if (placeholders.isEmpty()) {
            getLogger().info("[AdventureTAB] PAPI 桥接已启用但占位符列表为空");
            return;
        }

        int interval = configManager.getPapiRefreshTicks();
        // 使用同步任务 - 许多 PAPI 扩展不支持异步解析
        papiSyncTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                try {
                    Map<String, String> current = new HashMap<>();
                    for (String placeholder : placeholders) {
                        // 确保有 % 包围
                        String withPercent = placeholder.startsWith("%") ? placeholder : "%" + placeholder + "%";
                        String key = withPercent.substring(1, withPercent.length() - 1); // 去% 的 key
                        String resolved = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, withPercent);
                        // PAPI 解析失败时返回原文，跳过
                        if (!resolved.equals(withPercent)) {
                            current.put(key, resolved);
                        }
                    }

                    // 只在值变化时发送
                    Map<String, String> last = lastPapiValues.get(player.getUniqueId());
                    if (!current.equals(last)) {
                        // 检查频道是否已被代理注册 - 未注册时跳过（下次重试）
                        if (!player.getListeningPluginChannels().contains(configManager.getChannel())) {
                            continue; // 代理尚未注册频道，跳过本次
                        }
                        messenger.sendPapiValues(player, current);
                        lastPapiValues.put(player.getUniqueId(), current);
                    }
                } catch (Exception e) {
                    getLogger().warning("[AdventureTAB] PAPI 桥接异常: " + e.getMessage());
                }
            }
        }, 40L, interval);
        getLogger().info("[AdventureTAB] PAPI 桥接已启动 (占位符: " + placeholders.size()
                + ", 间隔: " + interval + "tick)");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 清除 PAPI 缓存，确保玩家加入时强制重发到 Velocity
        lastPapiValues.remove(event.getPlayer().getUniqueId());
        lastBossBarText.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastPapiValues.remove(event.getPlayer().getUniqueId());
        lastBossBarText.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onDisable() {
        if (bossBarSyncTask != null) bossBarSyncTask.cancel();
        if (papiSyncTask != null) papiSyncTask.cancel();
        if (messenger != null) messenger.unregister();
        lastBossBarText.clear();
        lastPapiValues.clear();
        getLogger().info("[AdventureTAB-Spigot] 已关闭");
    }

    public SpigotConfigManager getConfigManager() { return configManager; }
    public VelocityMessenger getMessenger() { return messenger; }
    public CraftEngineHook getCraftEngineHook() { return craftEngineHook; }
    public CustomNameplatesHook getCnpHook() { return cnpHook; }
}
