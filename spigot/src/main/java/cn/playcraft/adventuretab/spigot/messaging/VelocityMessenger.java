package cn.playcraft.adventuretab.spigot.messaging;

import cn.playcraft.adventuretab.spigot.config.SpigotConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * 通过插件消息频道向 Velocity 发送数据
 * 协议:
 *   0x01 物品更新: [byte][long msb][long lsb][utf itemModel]
 *   0x02 纹理数据: [byte][utf textureKey][utf base64Data]
 *   0x03 表情数据: [byte][long msb][long lsb][utf emojiId]
 */
public class VelocityMessenger implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final SpigotConfigManager config;

    private static final byte MSG_ITEM_UPDATE = 0x01;
    private static final byte MSG_TEXTURE_DATA = 0x02;
    private static final byte MSG_EMOJI_DATA = 0x03;
    private static final byte MSG_BOSSBAR_TEXT = 0x04;
    private static final byte MSG_PAPI_VALUES = 0x05;

    public VelocityMessenger(JavaPlugin plugin, SpigotConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, config.getChannel());
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, config.getChannel(), this);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, config.getChannel());
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, config.getChannel());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // 暂不处理来自 Velocity 的消息
    }

    /**
     * 发送玩家手持物品模型更新
     */
    public void sendItemUpdate(Player player, String itemModel) {
        if (!config.isSendItemUpdates()) return;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(MSG_ITEM_UPDATE);
            out.writeLong(player.getUniqueId().getMostSignificantBits());
            out.writeLong(player.getUniqueId().getLeastSignificantBits());
            out.writeUTF(itemModel);
            player.sendPluginMessage(plugin, config.getChannel(), bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[AdventureTAB] 发送物品更新失败: " + e.getMessage());
        }
    }

    /**
     * 发送纹理 Base64 数据
     */
    public void sendTextureData(Player player, String textureKey, String base64) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(MSG_TEXTURE_DATA);
            out.writeUTF(textureKey);
            out.writeUTF(base64);
            player.sendPluginMessage(plugin, config.getChannel(), bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[AdventureTAB] 发送纹理数据失败: " + e.getMessage());
        }
    }

    /**
     * 发送表情符号数据
     */
    public void sendEmojiData(Player player, String emojiId) {
        if (!config.isSendEmojiUpdates()) return;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(MSG_EMOJI_DATA);
            out.writeLong(player.getUniqueId().getMostSignificantBits());
            out.writeLong(player.getUniqueId().getLeastSignificantBits());
            out.writeUTF(emojiId);
            player.sendPluginMessage(plugin, config.getChannel(), bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[AdventureTAB] 发送表情数据失败: " + e.getMessage());
        }
    }

    /**
     * 发送 PlaceholderAPI 解析结果到 Velocity
     * 协议: 0x05 [long msb][long lsb][short count][utf key1][utf val1]...
     */
    public void sendPapiValues(Player player, java.util.Map<String, String> values) {
        if (values.isEmpty()) return;

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(MSG_PAPI_VALUES);
            out.writeLong(player.getUniqueId().getMostSignificantBits());
            out.writeLong(player.getUniqueId().getLeastSignificantBits());
            out.writeShort(values.size());
            for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }
            player.sendPluginMessage(plugin, config.getChannel(), bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[AdventureTAB] 发送PAPI数据失败: " + e.getMessage());
        }
    }

    /**
     * 发送 CustomNameplates BossBar 文本到 Velocity
     * 协议: 0x04 [long msb][long lsb][utf bossBarText]
     * bossBarText 为空字符串表示当前无活跃 BossBar
     */
    public void sendBossBarText(Player player, String bossBarText) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(MSG_BOSSBAR_TEXT);
            out.writeLong(player.getUniqueId().getMostSignificantBits());
            out.writeLong(player.getUniqueId().getLeastSignificantBits());
            out.writeUTF(bossBarText != null ? bossBarText : "");
            player.sendPluginMessage(plugin, config.getChannel(), bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[AdventureTAB] 发送BossBar文本失败: " + e.getMessage());
        }
    }
}
