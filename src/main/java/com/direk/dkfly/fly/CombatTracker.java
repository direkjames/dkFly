package com.direk.dkfly.fly;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Remembers when players were last in PvP. Author: direk james */
public final class CombatTracker {

    private final Map<UUID, Long> taggedUntil = new HashMap<>();

    public void tag(UUID uuid, long seconds) {
        if (seconds <= 0) return;
        taggedUntil.put(uuid, System.currentTimeMillis() + seconds * 1000L);
    }

    /** @return seconds of combat tag left, 0 if not tagged */
    public long remaining(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            taggedUntil.remove(uuid);
            return 0;
        }
        return (left + 999) / 1000;
    }

    public void forget(UUID uuid) {
        taggedUntil.remove(uuid);
    }

    /** Drops expired tags (players who left while tagged). */
    public void prune() {
        long now = System.currentTimeMillis();
        taggedUntil.values().removeIf(until -> until <= now);
    }
}
