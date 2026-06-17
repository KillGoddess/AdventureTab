package cn.playcraft.adventuretab.spigot.listener;

import cn.playcraft.adventuretab.spigot.hook.CraftEngineHook;
import cn.playcraft.adventuretab.spigot.messaging.VelocityMessenger;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听玩家物品变化事件，向 Velocity 推送物品模型数据
 * - PlayerItemHeldEvent: 切换快捷栏
 * - PlayerSwapHandItemsEvent: F键切换主副手
 * - PlayerJoinEvent: 玩家加入时发送初始物品
 */
public class ItemChangeListener implements Listener {

    private final JavaPlugin plugin;
    private final VelocityMessenger messenger;
    private final CraftEngineHook craftEngineHook;

    private final Map<UUID, Long> lastSend = new ConcurrentHashMap<>();
    private static final long THROTTLE_MS = 500;

    public ItemChangeListener(JavaPlugin plugin, VelocityMessenger messenger, CraftEngineHook craftEngineHook) {
        this.plugin = plugin;
        this.messenger = messenger;
        this.craftEngineHook = craftEngineHook;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!canSend(player.getUniqueId())) return;

        // 延迟1 tick 确保物品切换完成
        new BukkitRunnable() {
            @Override
            public void run() {
                sendCurrentItem(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!canSend(player.getUniqueId())) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                sendCurrentItem(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 延迟发送，等待玩家完全加载 + 连接到 Velocity 代理
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    sendCurrentItem(player);
                }
            }
        }.runTaskLater(plugin, 40L); // 2秒后
    }

    private void sendCurrentItem(Player player) {
        try {
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            String itemModel = craftEngineHook.getItemModel(mainHand);
            if (itemModel != null) {
                messenger.sendItemUpdate(player, itemModel);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[AdventureTAB] 发送物品数据失败: " + player.getName() + " - " + e.getMessage());
        }
    }

    private boolean canSend(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastSend.get(uuid);
        if (last != null && (now - last) < THROTTLE_MS) return false;
        lastSend.put(uuid, now);
        return true;
    }
}
