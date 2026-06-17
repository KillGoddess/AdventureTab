package cn.playcraft.adventuretab.tablist;

import cn.playcraft.adventuretab.config.ConfigManager;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TabListUpdater {

    private final TabListManager tabListManager;
    private final ConfigManager configManager;
    private final Logger logger;
    private ScheduledExecutorService scheduler;

    public TabListUpdater(TabListManager tabListManager, ConfigManager configManager, Logger logger) {
        this.tabListManager = tabListManager;
        this.configManager = configManager;
        this.logger = logger;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdownNow();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AdventureTAB-Updater");
            t.setDaemon(true);
            return t;
        });
        int interval = configManager.getUpdateInterval();
        scheduler.scheduleAtFixedRate(() -> {
            try { tabListManager.updateAll(); }
            catch (Exception e) { logger.error("[AdventureTAB] 定时更新TabList失败", e); }
        }, interval, interval, TimeUnit.SECONDS);
        logger.info("[AdventureTAB] 定时更新已启动 (间隔: {}秒)", interval);
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            logger.info("[AdventureTAB] 定时更新已停止");
        }
    }
}
