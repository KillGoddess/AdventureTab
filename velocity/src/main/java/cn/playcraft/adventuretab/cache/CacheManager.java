package cn.playcraft.adventuretab.cache;

import cn.playcraft.adventuretab.config.ConfigManager;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CacheManager {

    private final Cache<UUID, Integer> weightCache;
    private final Cache<String, String> textureCache;
    private final Cache<UUID, String> playerItemCache;

    public CacheManager(ConfigManager config) {
        this.weightCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(config.getWeightCacheDuration(), TimeUnit.MINUTES)
                .build();

        this.textureCache = Caffeine.newBuilder()
                .maximumSize(config.getMaxTextureCacheSize())
                .expireAfterWrite(config.getTextureCacheDuration(), TimeUnit.MINUTES)
                .build();

        this.playerItemCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public Integer getWeight(UUID uuid) { return weightCache.getIfPresent(uuid); }
    public void putWeight(UUID uuid, int weight) { weightCache.put(uuid, weight); }

    public String getTexture(String textureKey) { return textureCache.getIfPresent(textureKey); }
    public void putTexture(String textureKey, String base64) { textureCache.put(textureKey, base64); }

    public String getPlayerItem(UUID uuid) { return playerItemCache.getIfPresent(uuid); }
    public void putPlayerItem(UUID uuid, String itemModel) { playerItemCache.put(uuid, itemModel); }

    public void removePlayer(UUID uuid) {
        weightCache.invalidate(uuid);
        playerItemCache.invalidate(uuid);
    }

    public void invalidateAll() {
        weightCache.invalidateAll();
        textureCache.invalidateAll();
        playerItemCache.invalidateAll();
    }
}
