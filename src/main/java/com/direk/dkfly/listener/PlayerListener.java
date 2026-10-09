package com.direk.dkfly.listener;

import com.direk.dkfly.DkFly;
import com.direk.dkfly.data.FlyData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Loading, saving and keeping flight correct across joins, worlds and game modes.
 * Author: direk james
 */
public final class PlayerListener implements Listener {

    private final DkFly plugin;

    public PlayerListener(DkFly plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        plugin.data().preload(e.getUniqueId());
    }

    // LOWEST so we put them back in the air before anything else runs.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        FlyData d = plugin.data().join(p);
        plugin.flight().restoreOnJoin(p, d);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        FlyData d = plugin.data().getIfOnline(p.getUniqueId());
        if (d != null) d.wasFlying(d.enabled() && p.isFlying());
        plugin.flight().forget(p);
        plugin.transfers().forget(p.getUniqueId());
        plugin.data().quit(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.tasks().later(() -> {
            if (e.getPlayer().isOnline()) plugin.flight().recheck(e.getPlayer());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.tasks().later(() -> {
            if (e.getPlayer().isOnline()) plugin.flight().recheck(e.getPlayer());
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.tasks().later(() -> {
            if (e.getPlayer().isOnline()) plugin.flight().recheck(e.getPlayer());
        }, 1L);
    }

    /** Switching from creative/spectator back to survival resets flight; give it back if /fly is on. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        plugin.tasks().later(() -> {
            if (e.getPlayer().isOnline()) plugin.flight().recheck(e.getPlayer());
        }, 1L);
    }
}
