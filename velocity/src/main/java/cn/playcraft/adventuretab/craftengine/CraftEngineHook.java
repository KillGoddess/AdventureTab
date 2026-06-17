package cn.playcraft.adventuretab.craftengine;

import cn.playcraft.adventuretab.cache.CacheManager;
import cn.playcraft.adventuretab.config.ConfigManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CraftEngineHook {

    private final ProxyServer server;
    private final Logger logger;
    private final Object plugin;
    private CacheManager cacheManager;
    private final ConfigManager configManager;

    private ChannelIdentifier channel;
    private boolean available = false;

    private static final byte MSG_ITEM_UPDATE = 0x01;
    private static final byte MSG_TEXTURE_DATA = 0x02;
    private static final byte MSG_EMOJI_DATA = 0x03;
    private static final byte MSG_BOSSBAR_TEXT = 0x04;
    private static final byte MSG_PAPI_VALUES = 0x05;
    private static final byte MSG_SOCIAL_RELATIONS = 0x06;

    // CustomNameplates BossBar 文本缓存: playerUUID -> MiniMessage格式的BossBar文字
    private final Map<UUID, String> bossBarTextCache = new ConcurrentHashMap<>();
    // PAPI 桥接缓存: playerUUID -> { placeholder_key -> resolved_value }
    private final Map<UUID, Map<String, String>> papiCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Byte>> socialRelationCache = new ConcurrentHashMap<>();

    public CraftEngineHook(ProxyServer server, Logger logger, Object plugin,
                           CacheManager cacheManager, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.plugin = plugin;
        this.cacheManager = cacheManager;
        this.configManager = configManager;
    }

    public void init() {
        if (!configManager.isCraftEngineEnabled()) {
            logger.info("[AdventureTAB] CraftEngine 集成已禁用");
            return;
        }
        try {
            String channelId = configManager.getCeChannel();
            if (!channelId.contains(":")) {
                logger.error("[AdventureTAB] CraftEngine 频道格式错误: {}", channelId);
                return;
            }
            channel = MinecraftChannelIdentifier.from(channelId);
            server.getChannelRegistrar().register(channel);
            server.getEventManager().register(plugin, this);
            available = true;
            logger.info("[AdventureTAB] CraftEngine 集成已启用 (频道: {})", channelId);
        } catch (Exception e) {
            logger.error("[AdventureTAB] CraftEngine 集成初始化失败", e);
            available = false;
        }
    }

    public void shutdown() {
        if (channel != null) server.getChannelRegistrar().unregister(channel);
        available = false;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!available || channel == null) return;
        if (!event.getIdentifier().equals(channel)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        logger.debug("[AdventureTAB] 收到插件消息, 长度: {} 字节", event.getData().length);

        byte[] data = event.getData();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            byte type = in.readByte();
            switch (type) {
                case MSG_ITEM_UPDATE -> handleItemUpdate(in);
                case MSG_TEXTURE_DATA -> handleTextureData(in);
                case MSG_EMOJI_DATA -> handleEmojiData(in);
                case MSG_BOSSBAR_TEXT -> handleBossBarText(in);
                case MSG_PAPI_VALUES -> handlePapiValues(in);
                case MSG_SOCIAL_RELATIONS -> handleSocialRelations(in);
                default -> logger.debug("[AdventureTAB] 未知消息类型: 0x{}",
                        String.format("%02X", type));
            }
        } catch (Exception e) {
            logger.error("[AdventureTAB] 处理CraftEngine消息失败", e);
        }
    }

    private void handleItemUpdate(DataInputStream in) throws IOException {
        UUID playerUuid = new UUID(in.readLong(), in.readLong());
        String itemModel = in.readUTF();
        cacheManager.putPlayerItem(playerUuid, itemModel);
        logger.debug("[AdventureTAB] 玩家物品更新: {} -> {}", playerUuid, itemModel);
    }

    private void handleTextureData(DataInputStream in) throws IOException {
        String textureKey = in.readUTF();
        String base64 = in.readUTF();
        cacheManager.putTexture(textureKey, base64);
        logger.debug("[AdventureTAB] 纹理缓存更新: {}", textureKey);
    }

    private void handleEmojiData(DataInputStream in) throws IOException {
        UUID playerUuid = new UUID(in.readLong(), in.readLong());
        String emojiId = in.readUTF();
        cacheManager.putPlayerItem(playerUuid, "emoji:" + emojiId);
        logger.debug("[AdventureTAB] 表情更新: {} -> {}", playerUuid, emojiId);
    }

    private void handlePapiValues(DataInputStream in) throws IOException {
        UUID playerUuid = new UUID(in.readLong(), in.readLong());
        short count = in.readShort();
        Map<String, String> values = new ConcurrentHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = in.readUTF();
            String value = in.readUTF();
            values.put(key, value);
        }
        papiCache.put(playerUuid, values);
        logger.debug("[AdventureTAB] PAPI 更新: {} -> {} 个占位符", playerUuid, count);
    }

    /**
     * 获取玩家的 PAPI 占位符值
     * @param uuid 玩家 UUID
     * @param key 占位符 key (不含 %)
     * @return 解析后的值，或 null
     */
    public String getPapiValue(UUID uuid, String key) {
        Map<String, String> values = papiCache.get(uuid);
        if (values == null) return null;
        return values.get(key);
    }

    private void handleSocialRelations(DataInputStream in) throws IOException {
        UUID viewerUuid = new UUID(in.readLong(), in.readLong());
        int count = in.readUnsignedShort();
        Map<UUID, Byte> relations = new ConcurrentHashMap<>();
        for (int i = 0; i < count; i++) {
            UUID targetUuid = new UUID(in.readLong(), in.readLong());
            byte flags = in.readByte();
            if (flags != 0) relations.put(targetUuid, flags);
        }
        socialRelationCache.put(viewerUuid, relations);
        logger.debug("[AdventureTAB] AdventureFriend social relations: {} -> {} entries", viewerUuid, count);
    }

    public byte getSocialRelationFlags(UUID viewerUuid, UUID targetUuid) {
        Map<UUID, Byte> relations = socialRelationCache.get(viewerUuid);
        if (relations == null) return 0;
        return relations.getOrDefault(targetUuid, (byte) 0);
    }

    public void removePapiCache(UUID uuid) {
        papiCache.remove(uuid);
    }

    private void handleBossBarText(DataInputStream in) throws IOException {
        UUID playerUuid = new UUID(in.readLong(), in.readLong());
        String text = in.readUTF();
        if (text.isEmpty()) {
            bossBarTextCache.remove(playerUuid);
        } else {
            bossBarTextCache.put(playerUuid, text);
        }
        logger.debug("[AdventureTAB] CNP BossBar 更新: {} -> {}", playerUuid,
                text.isEmpty() ? "(无)" : text.substring(0, Math.min(text.length(), 30)));
    }

    /**
     * 获取玩家当前的 CNP BossBar 文本 (MiniMessage 格式)
     * @return BossBar 文本，或 null 如果无活跃 BossBar
     */
    public String getBossBarText(UUID uuid) {
        return bossBarTextCache.get(uuid);
    }

    public void removeBossBarText(UUID uuid) {
        bossBarTextCache.remove(uuid);
    }

    public void removeSocialRelations(UUID uuid) {
        socialRelationCache.remove(uuid);
        for (Map<UUID, Byte> relations : socialRelationCache.values()) {
            relations.remove(uuid);
        }
    }

    public String getPlayerItemModel(UUID uuid) {
        if (!available) return null;
        return cacheManager.getPlayerItem(uuid);
    }

    public String getTextureBase64(String textureKey) {
        if (!available) return null;
        return cacheManager.getTexture(textureKey);
    }

    public boolean isAvailable() { return available; }
    public void setCacheManager(CacheManager cacheManager) { this.cacheManager = cacheManager; }
}
