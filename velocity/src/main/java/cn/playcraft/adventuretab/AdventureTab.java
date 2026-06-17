package cn.playcraft.adventuretab;

import cn.playcraft.adventuretab.cache.CacheManager;
import cn.playcraft.adventuretab.command.MainCommand;
import cn.playcraft.adventuretab.config.ConfigManager;
import cn.playcraft.adventuretab.config.GroupsConfig;
import cn.playcraft.adventuretab.config.Messages;
import cn.playcraft.adventuretab.craftengine.CraftEngineHook;
import cn.playcraft.adventuretab.listener.PlayerEventListener;
import cn.playcraft.adventuretab.permission.LuckPermsHook;
import cn.playcraft.adventuretab.placeholder.AnimationManager;
import cn.playcraft.adventuretab.placeholder.PlaceholderManager;
import cn.playcraft.adventuretab.sorting.SortingManager;
import cn.playcraft.adventuretab.tablist.TabListManager;
import cn.playcraft.adventuretab.tablist.TabListUpdater;
import cn.playcraft.adventuretab.util.ThrottleManager;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import com.google.inject.Inject;
import java.nio.file.Path;

@Plugin(id = "adventuretab", name = "AdventureTAB", version = "2.1.2",
        description = "跨服TabList管理 - CraftEngine & LuckPerms 集成",
        authors = {"PlayCraft"},
        dependencies = {
                @Dependency(id = "luckperms", optional = true)
        })
public class AdventureTab {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private GroupsConfig groupsConfig;
    private CacheManager cacheManager;
    private ThrottleManager throttleManager;
    private LuckPermsHook luckPermsHook;
    private CraftEngineHook craftEngineHook;
    private PlaceholderManager placeholderManager;
    private AnimationManager animationManager;
    private SortingManager sortingManager;
    private TabListManager tabListManager;
    private TabListUpdater tabListUpdater;

    @Inject
    public AdventureTab(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 0. 消息加载
        Messages.load(dataDirectory, logger);

        // 1. 配置加载
        configManager = new ConfigManager(dataDirectory, logger);
        configManager.load();

        // 2. groups.yml 加载
        groupsConfig = new GroupsConfig(dataDirectory, logger);
        groupsConfig.load();

        // 3. 缓存管理器
        cacheManager = new CacheManager(configManager);

        // 4. 节流管理器
        throttleManager = new ThrottleManager(configManager.getThrottleMs());

        // 5. LuckPerms 集成
        luckPermsHook = new LuckPermsHook(server, logger, cacheManager, configManager);
        luckPermsHook.init();

        // 6. CraftEngine 集成
        craftEngineHook = new CraftEngineHook(server, logger, this, cacheManager, configManager);
        craftEngineHook.init();

        // 7. 占位符管理器
        placeholderManager = new PlaceholderManager(server, logger, luckPermsHook, configManager);
        placeholderManager.init();

        // 7.1 PAPI 桥接 (CraftEngineHook → PlaceholderManager)
        placeholderManager.setCraftEngineHook(craftEngineHook);

        // 8. 动画管理器
        animationManager = new AnimationManager(logger);
        animationManager.load(configManager.getAnimations());
        placeholderManager.setAnimationManager(animationManager);

        // 9. 排序管理器
        sortingManager = new SortingManager(logger);
        sortingManager.load(configManager.getSortingTypes(), luckPermsHook, placeholderManager);

        // 10. TabList 管理器
        tabListManager = new TabListManager(server, logger, configManager, cacheManager,
                luckPermsHook, craftEngineHook);
        tabListManager.setPlaceholderManager(placeholderManager);
        tabListManager.setSortingManager(sortingManager);
        tabListManager.setGroupsConfig(groupsConfig);
        tabListManager.compileDesigns();

        // 11. 定时更新器
        tabListUpdater = new TabListUpdater(tabListManager, configManager, logger);
        tabListUpdater.start();

        // 12. 事件监听器
        server.getEventManager().register(this, new PlayerEventListener(this));

        // 13. 注册命令
        registerCommands();

        logger.info("[AdventureTAB] v2.1.2 已启用 (设计: {}, 排序: {}, 全局列表: {})",
                configManager.getHeaderFooterDesigns().size(),
                configManager.getSortingTypes().size(),
                configManager.isGlobalPlayerListEnabled() ? "启用" : "禁用");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (tabListUpdater != null) tabListUpdater.stop();
        if (animationManager != null) animationManager.shutdown();
        if (cacheManager != null) cacheManager.invalidateAll();
        if (craftEngineHook != null) craftEngineHook.shutdown();
        if (luckPermsHook != null) luckPermsHook.shutdown();
        throttleManager = null;
        logger.info("[AdventureTAB] 已关闭");
    }

    private void registerCommands() {
        CommandManager cm = server.getCommandManager();
        cm.register(cm.metaBuilder("adventuretab").aliases("atab").build(),
                new MainCommand(this));
    }

    public void reload() {
        // 消息
        Messages.load(dataDirectory, logger);

        // 配置
        configManager.load();
        groupsConfig.load();

        // 缓存
        cacheManager.invalidateAll();
        cacheManager = new CacheManager(configManager);
        luckPermsHook.setCacheManager(cacheManager);
        craftEngineHook.setCacheManager(cacheManager);

        // 节流
        throttleManager = new ThrottleManager(configManager.getThrottleMs());

        // 占位符
        placeholderManager.init();

        // 动画
        animationManager.shutdown();
        animationManager.load(configManager.getAnimations());

        // 排序
        sortingManager.load(configManager.getSortingTypes(), luckPermsHook, placeholderManager);

        // TabList
        tabListManager.reload(configManager, cacheManager);
        tabListManager.setGroupsConfig(groupsConfig);

        // 更新器
        tabListUpdater.stop();
        tabListUpdater = new TabListUpdater(tabListManager, configManager, logger);
        tabListUpdater.start();

        tabListManager.updateAll();
        logger.info("[AdventureTAB] 配置已重载");
    }

    // --- Getters ---
    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public Path getDataDirectory() { return dataDirectory; }
    public ConfigManager getConfigManager() { return configManager; }
    public CacheManager getCacheManager() { return cacheManager; }
    public ThrottleManager getThrottleManager() { return throttleManager; }
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
    public CraftEngineHook getCraftEngineHook() { return craftEngineHook; }
    public TabListManager getTabListManager() { return tabListManager; }
    public PlaceholderManager getPlaceholderManager() { return placeholderManager; }
    public SortingManager getSortingManager() { return sortingManager; }
}
