package com.direk.dkfly.fly;

import com.direk.dkcore.message.Messages;
import com.direk.dkfly.DkFly;
import com.direk.dkfly.Settings;
import com.direk.dkfly.data.FlyData;
import com.direk.dkfly.hook.BentoBoxHook;
import com.direk.dkfly.hook.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.logging.Level;

/**
 * All flight rules live here: toggling, checks, draining time, countdown, landing.
 * Author: direk james
 */
public final class FlightManager {

    private static final float VANILLA_SPEED = 0.1f;

    private final DkFly plugin;
    private final LandingManager landing = new LandingManager();
    private final CombatTracker combat = new CombatTracker();

    private boolean worldGuardPresent;
    private boolean bentoBoxPresent;
    private boolean warnedPurpurAfk;

    public FlightManager(DkFly plugin) {
        this.plugin = plugin;
    }

    public void detectHooks() {
        worldGuardPresent = Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && WorldGuardHook.ready();
        bentoBoxPresent = Bukkit.getPluginManager().isPluginEnabled("BentoBox");
    }

    public boolean worldGuardActive() { return worldGuardPresent && s().worldGuard; }
    public boolean bentoBoxActive() { return bentoBoxPresent && s().bentoBox; }
    public boolean worldGuardInstalled() { return worldGuardPresent; }
    public boolean bentoBoxInstalled() { return bentoBoxPresent; }

    public LandingManager landing() { return landing; }
    public CombatTracker combat() { return combat; }

    private Settings s() { return plugin.settings(); }
    private Messages msg() { return plugin.messages(); }

    // ---------------------------------------------------------------- checks

    public boolean isUnlimited(Player p) {
        return p.hasPermission("dkfly.unlimited");
    }

    /** Creative and spectator can already fly; dkFly leaves them alone. */
    public static boolean isExemptMode(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
    }

    public Denial check(Player p, Location loc, FlyData d) {
        if (!isUnlimited(p) && d.seconds() <= 0) return Denial.NO_TIME;
        if (loc.getWorld() != null && s().worldBlacklist.contains(loc.getWorld().getName())
                && !p.hasPermission("dkfly.bypass.world")) return Denial.WORLD;
        if (s().combatEnabled && s().combatDisableFly && combat.remaining(p.getUniqueId()) > 0
                && !p.hasPermission("dkfly.bypass.combat")) return Denial.COMBAT;
        if (!p.hasPermission("dkfly.bypass.region")) {
            try {
                if (s().worldGuard && worldGuardPresent && !WorldGuardHook.canFly(p, loc)) return Denial.REGION;
                if (s().bentoBox && bentoBoxPresent && !BentoBoxHook.canFly(p, loc, s())) return Denial.ISLAND;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Region hook error (allowing flight)", t);
            }
        }
        return Denial.NONE;
    }

    public boolean isAfk(Player p) {
        if (plugin.core().isAfk(p.getUniqueId())) return true;
        if (s().purpurAfkFallback && !plugin.core().hasAfkProvider()) {
            try {
                return p.isAfk(); // Purpur API
            } catch (NoSuchMethodError e) {
                if (!warnedPurpurAfk) {
                    warnedPurpurAfk = true;
                    plugin.getLogger().warning("Purpur AFK API not found; AFK pause only works with an AFK provider.");
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- toggling

    /** /fly — returns true if flight is now on. */
    public boolean toggle(Player p, boolean notify) {
        FlyData d = plugin.data().get(p);
        if (isExemptMode(p)) {
            if (notify) msg().send(p, "denied-gamemode");
            return false;
        }
        if (d.enabled()) {
            disable(p, d, null);
            if (notify) msg().send(p, "fly-off");
            return false;
        }
        Denial denial = check(p, p.getLocation(), d);
        if (denial != Denial.NONE) {
            if (notify) sendDenial(p, denial, false);
            return false;
        }
        enable(p, d);
        if (notify) {
            if (isUnlimited(p)) msg().send(p, "fly-on-unlimited");
            else msg().send(p, "fly-on", Messages.text("time", s().time.format(d.seconds())));
        }
        return true;
    }

    public void enable(Player p, FlyData d) {
        d.enabled(true);
        landing.clear(p);
        p.setAllowFlight(true);
        applySpeed(p);
    }

    /** Turns flight off and brings the player down safely if they were in the air. */
    public void disable(Player p, FlyData d, Denial reason) {
        d.enabled(false);
        if (!isExemptMode(p)) {
            boolean inAir = p.isFlying() || !LandingManager.isGrounded(p);
            p.setFlying(false);
            p.setAllowFlight(false);
            if (inAir) landing.safeLand(p, s());
        }
        p.setFlySpeed(VANILLA_SPEED);
        if (reason != null) sendDenial(p, reason, true);
    }

    public void applySpeed(Player p) {
        float best = s().defaultSpeed;
        for (Map.Entry<String, Float> e : s().speedPermissions.entrySet()) {
            if (e.getValue() > best && p.hasPermission(e.getKey())) best = e.getValue();
        }
        p.setFlySpeed(best);
    }

    private void sendDenial(Player p, Denial denial, boolean wasDisabled) {
        String key = wasDisabled ? denial.disabledKey : denial.denyKey;
        if (denial == Denial.COMBAT) {
            msg().send(p, key, Messages.text("time", s().time.format(combat.remaining(p.getUniqueId()))));
        } else {
            msg().send(p, key);
        }
    }

    // ---------------------------------------------------------------- join / leave

    /** Puts a returning player back in the air if they logged out flying. */
    public void restoreOnJoin(Player p, FlyData d) {
        boolean wasFlying = d.wasFlying();
        d.wasFlying(false);
        if (!d.enabled()) return;

        if (!s().persistToggle) {
            d.enabled(false);
            if (wasFlying && !isExemptMode(p)) landing.safeLand(p, s());
            return;
        }
        if (isExemptMode(p)) return;

        Denial denial = check(p, p.getLocation(), d);
        if (denial != Denial.NONE) {
            d.enabled(false);
            if (wasFlying) landing.safeLand(p, s());
            sendDenial(p, denial, true);
            return;
        }
        applyRestore(p, wasFlying);
        // The client sometimes resets abilities right after joining; apply once more next tick.
        plugin.tasks().later(() -> {
            if (p.isOnline() && d.enabled() && !isExemptMode(p)) applyRestore(p, wasFlying);
        }, 1L);

        if (wasFlying && !isUnlimited(p)) msg().send(p, "fly-restored", Messages.text("time", s().time.format(d.seconds())));
    }

    private void applyRestore(Player p, boolean flying) {
        p.setAllowFlight(true);
        applySpeed(p);
        if (flying) {
            p.setFallDistance(0f);
            p.setFlying(true);
        }
    }

    public void forget(Player p) {
        landing.forget(p.getUniqueId());
    }

    // ---------------------------------------------------------------- every second

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tick(p);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error ticking flight for " + p.getName(), t);
            }
        }
    }

    private void tick(Player p) {
        landing.tick(p);
        FlyData d = plugin.data().getIfOnline(p.getUniqueId());
        if (d == null || !d.enabled() || isExemptMode(p)) return;

        Denial denial = check(p, p.getLocation(), d);
        if (denial != Denial.NONE) {
            disable(p, d, denial);
            return;
        }

        // Something (game mode change, world plugin) took flight away; give it back.
        if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
            applySpeed(p);
        }

        if (!p.isFlying() || isUnlimited(p)) return;

        if (s().afkPause && isAfk(p)) {
            msg().actionBar(p, "actionbar-paused", Messages.text("time", s().time.format(d.seconds())));
            return;
        }

        d.seconds(d.seconds() - 1);
        long left = d.seconds();
        if (left <= 0) {
            disable(p, d, Denial.NO_TIME);
            return;
        }

        if (s().countdownEnabled && s().countdownAt.contains(left)) {
            msg().actionBar(p, "actionbar-countdown", Messages.text("time", s().time.format(left)));
            if (s().countdownSound != null) p.playSound(s().countdownSound);
        } else if (s().actionbarWhileFlying) {
            msg().actionBar(p, "actionbar-flying", Messages.text("time", s().time.format(left)));
        }
    }

    /** Re-checks a player right away (world change, teleport). */
    public void recheck(Player p) {
        FlyData d = plugin.data().getIfOnline(p.getUniqueId());
        if (d == null || !d.enabled() || isExemptMode(p)) return;
        Denial denial = check(p, p.getLocation(), d);
        if (denial != Denial.NONE) disable(p, d, denial);
        else if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
            applySpeed(p);
        }
    }

    /** PvP hit: tag and, if configured, knock them out of the sky. */
    public void onCombat(Player p) {
        if (!s().combatEnabled || p.hasPermission("dkfly.bypass.combat")) return;
        combat.tag(p.getUniqueId(), s().combatCooldown);
        if (!s().combatDisableFly) return;
        FlyData d = plugin.data().getIfOnline(p.getUniqueId());
        if (d != null && d.enabled() && !isExemptMode(p)) disable(p, d, Denial.COMBAT);
    }
}
