package com.direk.dkfly.data;

import com.direk.dkfly.DkFly;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongUnaryOperator;
import java.util.logging.Level;

/**
 * Keeps online players' fly data in memory and saves it to the database.
 * Data is loaded during async pre-login, so it's ready the moment the player joins
 * (that's what lets us keep them in the air instead of dropping them).
 * <p>
 * Author: direk james
 */
public final class FlyDataManager {

    /** How long a login may take (including resource pack / configuration phase) before offline edits are allowed again. */
    private static final long LOADING_TIMEOUT_MS = 120_000;

    private final DkFly plugin;
    private final FlyRepository repo;
    private final Map<UUID, FlyData> online = new ConcurrentHashMap<>();
    private final Map<UUID, FlyData> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loading = new ConcurrentHashMap<>();
    /**
     * Guards "is this player logging in?" + "queue a database job" so the two can't interleave.
     * Without it an offline edit could be queued after a login's load, and the joining
     * player would then overwrite it with stale data (lost or duplicated time).
     */
    private final Object loginLock = new Object();

    public FlyDataManager(DkFly plugin, FlyRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    /** Called from AsyncPlayerPreLoginEvent (off the main thread). Blocks until loaded. */
    public void preload(UUID uuid) {
        CompletableFuture<FlyData> load;
        synchronized (loginLock) {
            loading.put(uuid, System.currentTimeMillis());
            load = repo.load(uuid);
        }
        try {
            preloaded.put(uuid, load.join());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load fly data for " + uuid, e);
        }
    }

    /** Called on join (main thread). Never returns null. */
    public FlyData join(Player player) {
        loading.remove(player.getUniqueId());
        FlyData data = preloaded.remove(player.getUniqueId());
        if (data == null) {
            // Should not happen, but never block the main thread: start empty, fix up when loaded.
            plugin.getLogger().warning("Fly data for " + player.getName() + " was not preloaded; loading now.");
            data = FlyData.empty(player.getUniqueId());
            FlyData placeholder = data;
            repo.load(player.getUniqueId()).thenAccept(loaded -> plugin.tasks().sync(() -> {
                if (online.get(player.getUniqueId()) == placeholder && player.isOnline()) {
                    online.put(player.getUniqueId(), loaded);
                    plugin.flight().restoreOnJoin(player, loaded);
                }
            }));
        }
        online.put(player.getUniqueId(), data);
        return data;
    }

    /** Used after a plugin reload for players who are already online. */
    public void adopt(Player player, FlyData data) {
        online.put(player.getUniqueId(), data);
    }

    /** Called on quit (main thread). Saves and forgets the player. */
    public void quit(Player player) {
        FlyData data = online.remove(player.getUniqueId());
        preloaded.remove(player.getUniqueId());
        if (data != null) {
            repo.save(data.snapshot());
            data.markClean();
        }
    }

    public FlyData get(Player player) {
        return online.computeIfAbsent(player.getUniqueId(), FlyData::empty);
    }

    public FlyData getIfOnline(UUID uuid) {
        return online.get(uuid);
    }

    /** True while the player is in the middle of logging in. Offline edits are refused then. */
    public boolean isLoggingIn(UUID uuid) {
        Long since = loading.get(uuid);
        return since != null && System.currentTimeMillis() - since < LOADING_TIMEOUT_MS;
    }

    /**
     * Changes a player's seconds whether they're online or offline.
     * Completes with the new value, or fails if the player is mid-login.
     * Must be called on the main thread.
     */
    public CompletableFuture<Long> modify(UUID uuid, LongUnaryOperator change) {
        FlyData data = online.get(uuid);
        if (data != null) {
            data.seconds(change.applyAsLong(data.seconds()));
            saveNow(uuid);
            return CompletableFuture.completedFuture(data.seconds());
        }
        synchronized (loginLock) {
            if (isLoggingIn(uuid)) {
                return CompletableFuture.failedFuture(new IllegalStateException("player is logging in"));
            }
            return repo.modifyOffline(uuid, change);
        }
    }

    /** Reads seconds for any player (cache first). */
    public CompletableFuture<Long> seconds(UUID uuid) {
        FlyData data = online.get(uuid);
        if (data != null) return CompletableFuture.completedFuture(data.seconds());
        return repo.load(uuid).thenApply(FlyData::seconds);
    }

    /**
     * Saves an online player right away (after a purchase, transfer or admin change),
     * so a crash can't undo something they paid for.
     */
    public void saveNow(UUID uuid) {
        FlyData d = online.get(uuid);
        if (d == null) return;
        repo.save(d.snapshot());
        d.markClean();
    }

    /** Saves players whose data changed, and forgets logins that never finished (kicked, banned, full). */
    public void saveDirty() {
        long now = System.currentTimeMillis();
        loading.entrySet().removeIf(e -> {
            boolean stale = now - e.getValue() >= LOADING_TIMEOUT_MS;
            if (stale) preloaded.remove(e.getKey());
            return stale;
        });
        List<FlyData.Snapshot> batch = new ArrayList<>();
        for (FlyData d : online.values()) {
            if (d.dirty()) {
                batch.add(d.snapshot());
                d.markClean();
            }
        }
        repo.saveAll(batch);
    }

    /** Saves everyone and waits (shutdown only). */
    public void saveAllBlocking(List<FlyData.Snapshot> snapshots) {
        try {
            repo.saveAll(snapshots).join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save fly data on shutdown", e);
        }
    }

    public Iterable<FlyData> all() {
        return online.values();
    }
}
