package cn.playcraft.adventuretab.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 消息管理 — 从 messages.yml 加载, 支持 MiniMessage 格式, /atab reload 热加载
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static Map<String, Object> data = new LinkedHashMap<>();

    private Messages() {}

    public static void load(Path dataDirectory, Logger logger) {
        Path file = dataDirectory.resolve("messages.yml");
        if (!Files.exists(file)) {
            try (InputStream in = Messages.class.getClassLoader().getResourceAsStream("messages.yml")) {
                if (in != null) {
                    Files.createDirectories(dataDirectory);
                    Files.copy(in, file);
                }
            } catch (IOException e) {
                logger.warn("[AdventureTAB] Failed to save default messages.yml", e);
            }
        }
        try (InputStream in = Files.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> loaded = yaml.load(in);
            data = loaded != null ? loaded : new LinkedHashMap<>();
        } catch (IOException e) {
            logger.warn("[AdventureTAB] Failed to load messages.yml", e);
        }
    }

    /**
     * 获取原始字符串
     */
    public static String raw(String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : "<red>[Missing: " + key + "]";
    }

    /**
     * 获取原始字符串并替换占位符 {0}, {1}, ...
     */
    public static String raw(String key, Object... args) {
        String text = raw(key);
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    /**
     * 获取 MiniMessage 解析后的 Component
     */
    public static Component get(String key) {
        return MM.deserialize(raw(key));
    }

    /**
     * 获取 Component (带占位符替换)
     */
    public static Component get(String key, Object... args) {
        return MM.deserialize(raw(key, args));
    }
}
