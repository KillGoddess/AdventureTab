package cn.playcraft.adventuretab.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ThrottleManager {

    private final long throttleMs;
    private final Map<UUID, Long> lastExecution = new ConcurrentHashMap<>();

    public ThrottleManager(long throttleMs) {
        this.throttleMs = throttleMs;
    }

    public boolean canExecute(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = lastExecution.get(uuid);
        if (last != null && (now - last) < throttleMs) {
            return false;
        }
        lastExecution.put(uuid, now);
        return true;
    }

    public void remove(UUID uuid) { lastExecution.remove(uuid); }
    public void clear() { lastExecution.clear(); }
}
