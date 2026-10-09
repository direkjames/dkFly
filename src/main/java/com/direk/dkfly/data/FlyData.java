package com.direk.dkfly.data;

import java.util.UUID;

/**
 * One player's fly state.
 * Only touched on the main thread while the player is online.
 * Author: direk james
 */
public final class FlyData {

    private final UUID uuid;
    private long seconds;
    private boolean enabled;     // /fly toggle
    private boolean wasFlying;   // in the air when they logged out
    private boolean dirty;

    public FlyData(UUID uuid, long seconds, boolean enabled, boolean wasFlying) {
        this.uuid = uuid;
        this.seconds = Math.max(0, seconds);
        this.enabled = enabled;
        this.wasFlying = wasFlying;
    }

    public static FlyData empty(UUID uuid) {
        return new FlyData(uuid, 0, false, false);
    }

    public UUID uuid() { return uuid; }
    public long seconds() { return seconds; }
    public boolean enabled() { return enabled; }
    public boolean wasFlying() { return wasFlying; }
    public boolean dirty() { return dirty; }

    public void seconds(long value) {
        long v = Math.max(0, value);
        if (v != seconds) { seconds = v; dirty = true; }
    }

    public void enabled(boolean value) {
        if (value != enabled) { enabled = value; dirty = true; }
    }

    public void wasFlying(boolean value) {
        if (value != wasFlying) { wasFlying = value; dirty = true; }
    }

    public void markClean() { dirty = false; }

    /** Copy handed to the database thread so it never reads a changing object. */
    public Snapshot snapshot() {
        return new Snapshot(uuid, seconds, enabled, wasFlying);
    }

    public record Snapshot(UUID uuid, long seconds, boolean enabled, boolean wasFlying) {}
}
