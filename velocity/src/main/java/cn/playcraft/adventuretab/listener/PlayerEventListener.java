package cn.playcraft.adventuretab.listener;

import cn.playcraft.adventuretab.AdventureTab;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerEventListener {

    private final AdventureTab plugin;

    public PlayerEventListener(AdventureTab plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!plugin.getThrottleManager().canExecute(uuid)) return;
        plugin.getServer().getScheduler()
                .buildTask(plugin, () -> plugin.getTabListManager().updateAll())
                .delay(500, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.getCacheManager().removePlayer(uuid);
        plugin.getThrottleManager().remove(uuid);
        plugin.getTabListManager().removePlayer(uuid);
        plugin.getServer().getScheduler()
                .buildTask(plugin, () -> plugin.getTabListManager().updateAll())
                .delay(200, TimeUnit.MILLISECONDS)
                .schedule();
    }
}
