package com.auxxy.rtpqueue.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple expiry map used for queue cooldowns.
 * MADE BY AUXXY
 */
public final class Cooldowns {

    private final Map<UUID, Long> expiry = new HashMap<>();

    public void set(UUID id, long seconds) {
        if (seconds <= 0) {
            expiry.remove(id);
            return;
        }
        expiry.put(id, System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean isActive(UUID id) {
        return remaining(id) > 0;
    }

    /** Remaining whole seconds, or 0 when nothing is pending. */
    public long remaining(UUID id) {
        Long until = expiry.get(id);
        if (until == null) {
            return 0L;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            expiry.remove(id);
            return 0L;
        }
        return (left + 999L) / 1000L;
    }

    public void clear(UUID id) {
        expiry.remove(id);
    }

    public void clearAll() {
        expiry.clear();
    }
}
