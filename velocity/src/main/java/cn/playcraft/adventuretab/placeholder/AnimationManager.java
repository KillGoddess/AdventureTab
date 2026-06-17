package cn.playcraft.adventuretab.placeholder;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 动画管理器 - 对标 TAB 的动画系统
 * 每个动画包含多个帧，按配置的间隔循环播放
 * 使用 %animation:name% 引用
 */
public class AnimationManager {

    private final Logger logger;
    private final Map<String, Animation> animations = new HashMap<>();
    private ScheduledExecutorService scheduler;

    public AnimationManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * 加载动画配置
     * @param animationsConfig key -> { "texts": [...], "interval": 200 }
     */
    @SuppressWarnings("unchecked")
    public void load(Map<String, Map<String, Object>> animationsConfig) {
        animations.clear();
        if (animationsConfig == null || animationsConfig.isEmpty()) {
            logger.info("[AdventureTAB] 无动画配置");
            return;
        }

        for (Map.Entry<String, Map<String, Object>> entry : animationsConfig.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> def = entry.getValue();

            List<String> texts = (List<String>) def.getOrDefault("texts", List.of());
            int interval = def.get("interval") instanceof Number n ? n.intValue() : 200;

            if (texts.isEmpty()) continue;
            animations.put(name, new Animation(texts, interval));
        }

        // 启动动画定时器
        startTimer();

        logger.info("[AdventureTAB] 已加载 {} 个动画", animations.size());
    }

    private void startTimer() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        if (animations.isEmpty()) return;

        // 找到所有动画间隔的 GCD 作为 tick 间隔
        int tickInterval = animations.values().stream()
                .mapToInt(a -> a.interval)
                .reduce(this::gcd)
                .orElse(200);
        tickInterval = Math.max(tickInterval, 50); // 最小 50ms

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AdventureTAB-Animation");
            t.setDaemon(true);
            return t;
        });

        int finalTickInterval = tickInterval;
        scheduler.scheduleAtFixedRate(() -> {
            for (Animation anim : animations.values()) {
                anim.tick(finalTickInterval);
            }
        }, tickInterval, tickInterval, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    public String getCurrentFrame(String name) {
        Animation anim = animations.get(name);
        return anim != null ? anim.getCurrentFrame() : "";
    }

    private int gcd(int a, int b) {
        while (b != 0) { int t = b; b = a % b; a = t; }
        return a;
    }

    private static class Animation {
        final List<String> frames;
        final int interval;
        int currentFrame = 0;
        long elapsed = 0;

        Animation(List<String> frames, int interval) {
            this.frames = frames;
            this.interval = interval;
        }

        void tick(int ms) {
            elapsed += ms;
            if (elapsed >= interval) {
                elapsed -= interval;
                currentFrame = (currentFrame + 1) % frames.size();
            }
        }

        String getCurrentFrame() {
            return frames.get(currentFrame);
        }
    }
}
