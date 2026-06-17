package cn.playcraft.adventuretab.permission;

import cn.playcraft.adventuretab.cache.CacheManager;
import cn.playcraft.adventuretab.config.ConfigManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.slf4j.Logger;

import java.util.UUID;

public class LuckPermsHook {

    private final ProxyServer server;
    private final Logger logger;
    private CacheManager cacheManager;
    private final ConfigManager configManager;

    private LuckPerms luckPerms;
    private boolean available = false;
    private EventSubscription<?> recalcSubscription;

    public LuckPermsHook(ProxyServer server, Logger logger, CacheManager cacheManager, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    public void init() {
        if (!configManager.isLuckPermsEnabled()) {
            logger.info("[AdventureTAB] LuckPerms 集成已禁用");
            return;
        }
        try {
            luckPerms = LuckPermsProvider.get();
            available = true;
            recalcSubscription = luckPerms.getEventBus().subscribe(
                    UserDataRecalculateEvent.class, this::onUserDataRecalculate);
            logger.info("[AdventureTAB] LuckPerms 集成已启用");
        } catch (IllegalStateException e) {
            logger.warn("[AdventureTAB] LuckPerms 未找到，权限排序功能禁用");
            available = false;
        }
    }

    public void shutdown() {
        if (recalcSubscription != null) {
            recalcSubscription.close();
            recalcSubscription = null;
        }
    }

    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        UUID uuid = event.getUser().getUniqueId();
        int weight = resolveWeight(event.getUser());
        cacheManager.putWeight(uuid, weight);
        logger.debug("[AdventureTAB] 权限数据更新: {} -> weight={}", uuid, weight);
    }

    public int getWeight(Player player) {
        if (!available) return configManager.getDefaultWeight();
        Integer cached = cacheManager.getWeight(player.getUniqueId());
        if (cached != null) return cached;
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return configManager.getDefaultWeight();
            int weight = resolveWeight(user);
            cacheManager.putWeight(player.getUniqueId(), weight);
            return weight;
        } catch (Exception e) {
            logger.debug("[AdventureTAB] 获取权限权重失败: {}", player.getUsername(), e);
            return configManager.getDefaultWeight();
        }
    }

    private int resolveWeight(User user) {
        try {
            CachedMetaData meta = user.getCachedData().getMetaData();
            String weightStr = meta.getMetaValue("weight");
            if (weightStr != null) return Integer.parseInt(weightStr);
            String primaryGroup = user.getPrimaryGroup();
            Group group = luckPerms.getGroupManager().getGroup(primaryGroup);
            if (group != null) return group.getWeight().orElse(configManager.getDefaultWeight());
        } catch (Exception e) {
            logger.debug("[AdventureTAB] 解析权限权重异常", e);
        }
        return configManager.getDefaultWeight();
    }

    public String getPrefix(Player player) {
        if (!available) return "";
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";
            String prefix = user.getCachedData().getMetaData().getPrefix();
            return prefix != null ? prefix : "";
        } catch (Exception e) { return ""; }
    }

    public String getSuffix(Player player) {
        if (!available) return "";
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";
            String suffix = user.getCachedData().getMetaData().getSuffix();
            return suffix != null ? suffix : "";
        } catch (Exception e) { return ""; }
    }

    public String getGroupDisplayName(Player player) {
        if (!available) return "";
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";
            String primaryGroup = user.getPrimaryGroup();
            Group group = luckPerms.getGroupManager().getGroup(primaryGroup);
            if (group != null) {
                String display = group.getDisplayName();
                return display != null ? display : primaryGroup;
            }
            return primaryGroup;
        } catch (Exception e) { return ""; }
    }

    /**
     * 获取玩家主权限组名称 (原始名，非显示名)
     */
    public String getPrimaryGroup(Player player) {
        if (!available) return "default";
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "default";
            return user.getPrimaryGroup();
        } catch (Exception e) { return "default"; }
    }

    public boolean isAvailable() { return available; }
    public void setCacheManager(CacheManager cacheManager) { this.cacheManager = cacheManager; }
}
