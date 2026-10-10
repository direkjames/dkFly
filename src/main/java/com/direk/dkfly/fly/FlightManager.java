package com.direk.dkfly.fly;

import com.direk.dkcore.message.Messages;
import com.direk.dkfly.DkFly;
import com.direk.dkfly.Settings;
import com.direk.dkfly.data.FlyData;
import com.direk.dkfly.hook.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * All flight rules live here: toggling, checks, draining time, countdown and landing.
 * <p>
 * Time is measured exactly: every switch between flying and not flying is timestamped
 * (see {@link #onToggleFlight}), and the 1-second task charges whole seconds from that
 * running total. Briefly stopping between ticks can't dodge the charge.
 * <p>
 * Author: direk james
 */
public final class FlightManager {

    private static final float VANILLA_SPEED = 0.1f;
    /** World/region/permission checks run when the player changes block, or at least this often. */
    private static final long FULL_CHECK_INTERVAL_MS = 5_000;
    private static final int PRUNE_EVERY_TICKS = 60;

    private final DkFly plugin;
    private final LandingManager landing = new LandingManager();
    private final CombatTracker combat = new CombatTracker();
    private final FlyDisplay display;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private boolean worldGuardPresent;
    private boolean warnedPurpurAfk;
    private int tickCount;

    public FlightManager(DkFly plugin) {
        this.plugin = plugin;
        this.display = new FlyDisplay(plugin);
    }

    public void detectHooks() {
        worldGuardPresent = Bukkit.getPluginManager().isPluginEnabled("WorldGuard") && WorldGuardHook.ready();
    }

    public boolean worldGuardInstalled() { return worldGuardPresent; }

    public LandingManager landing() { return landing; }
    public FlyDisplay display() { return display; }

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

    /** Every rule: time, world, combat, region. */
    public Denial check(Player p, Location loc, FlyData d) {
        Denial quick = quickCheck(p, d);
        if (quick != Denial.NONE) return quick;
        World w = loc.getWorld();
        if (w != null && s().worldBlacklist.contains(w.getName()) && !p.hasPermission("dkfly.bypass.world")) {
            return Denial.WORLD;
        }
        if (s().worldGuard && worldGuardPresent && !p.hasPermission("dkfly.bypass.region")) {
            try {
                if (!WorldGuardHook.canFly(p, loc)) return Denial.REGION;
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "WorldGuard check failed (allowing flight)", t);
            }
        }
        return Denial.NONE;
    }

    /** The cheap rules that can change without the player moving: time and combat. */
    private Denial quickCheck(Player p, FlyData d) {
        if (d.seconds() <= 0 && !isUnlimited(p)) return Denial.NO_TIME;
        if (s().combatEnabled && combat.remaining(p.getUniqueId()) > 0
                && !p.hasPermission("dkfly.bypass.combat")) return Denial.COMBAT;
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

    /**
     * /fly or /fly &lt;player&gt;.
     *
     * @param by who ran the command; if it's someone else, the admin landing rule applies
     * @return true if flight is on afterwards
     */
    public boolean toggle(Player p, CommandSender by) {
        boolean admin = by != p;
        FlyData d = plugin.data().get(p);
        if (isExemptMode(p)) {
            msg().send(p, "denied-gamemode");
            return false;
        }
        if (d.enabled()) {
            if (!admin && LandingManager.isHigherThan(p, s().selfDisableMaxHeight)) {
                // Turning fly off yourself mid-air would be a free, damage-free drop. Land first.
                msg().send(p, "land-first");
                return true;
            }
            disable(p, d, null, admin ? s().landingAdmin : Settings.Landing.NONE);
            msg().send(p, "fly-off");
            return false;
        }
        Denial denial = check(p, p.getLocation(), d);
        if (denial != Denial.NONE) {
            sendDenial(p, denial, false);
            return false;
        }
        enable(p, d);
        if (isUnlimited(p)) msg().send(p, "fly-on-unlimited");
        else msg().send(p, "fly-on", Messages.text("time", s().time.format(d.seconds())));
        return true;
    }

    public void enable(Player p, FlyData d) {
        d.enabled(true);
        landing.clear(p);
        Session sess = session(p);
        sess.fullTime = d.seconds();
        sess.nextFullCheck = 0;
        p.setAllowFlight(true);
        applySpeed(p);
    }

    /**
     * Turns flight off. If the player was in the air, the given landing rule applies.
     *
     * @param reason why (sends the matching "disabled" message), or null for a plain toggle
     */
    public void disable(Player p, FlyData d, Denial reason, Settings.Landing landingMode) {
        d.enabled(false);
        Session sess = sessions.get(p.getUniqueId());
        if (sess != null) sess.stopClock(System.currentTimeMillis());
        display.hide(p);
        if (!isExemptMode(p)) {
            boolean inAir = p.isFlying() || !LandingManager.isGrounded(p);
            p.setFlying(false);
            p.setAllowFlight(false);
            if (inAir) landing.land(p, landingMode, s().landingMaxSeconds);
        }
        p.setFlySpeed(VANILLA_SPEED);
        if (reason != null) sendDenial(p, reason, true);
    }

    /** Disables because a rule was broken, with that rule's landing behaviour. */
    private void deny(Player p, FlyData d, Denial reason) {
        disable(p, d, reason, landingFor(reason));
    }

    private Settings.Landing landingFor(Denial reason) {
        return switch (reason) {
            case NO_TIME -> s().landingOutOfTime;
            case WORLD, REGION -> s().landingRestricted;
            case COMBAT -> s().landingCombat;
            case NONE -> Settings.Landing.NONE;
        };
    }

    public void applySpeed(Player p) {
        float best = s().defaultSpeed;
        for (Map.Entry<String, Float> e : s().speedPermissions.entrySet()) {
            if (e.getValue() > best && p.hasPermission(e.getKey())) best = e.getValue();
        }
        if (p.getFlySpeed() != best) p.setFlySpeed(best);
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
            if (wasFlying && !isExemptMode(p)) landing.land(p, s().landingAdmin, s().landingMaxSeconds);
            return;
        }
        if (isExemptMode(p)) return;

        Denial denial = check(p, p.getLocation(), d);
        if (denial != Denial.NONE) {
            d.enabled(false);
            if (wasFlying) landing.land(p, landingFor(denial), s().landingMaxSeconds);
            sendDenial(p, denial, true);
            return;
        }
        Session sess = session(p);
        sess.fullTime = d.seconds();
        applyRestore(p, wasFlying);
        // The client sometimes resets abilities right after joining; apply once more next tick.
        plugin.tasks().later(() -> {
            if (p.isOnline() && d.enabled() && !isExemptMode(p)) applyRestore(p, wasFlying);
        }, 1L);

        if (wasFlying && !isUnlimited(p)) {
            msg().send(p, "fly-restored", Messages.text("time", s().time.format(d.seconds())));
        }
    }

    private void applyRestore(Player p, boolean flying) {
        p.setAllowFlight(true);
        applySpeed(p);
        if (flying && !p.isFlying()) {
            p.setFallDistance(0f);
            p.setFlying(true);
            onToggleFlight(p, true);
        }
    }

    public void forget(Player p) {
        landing.forget(p.getUniqueId());
        display.forget(p);
        sessions.remove(p.getUniqueId());
    }

    // ---------------------------------------------------------------- time keeping

    /** Called for every PlayerToggleFlightEvent, so time is charged to the millisecond. */
    public void onToggleFlight(Player p, boolean nowFlying) {
        FlyData d = plugin.data().getIfOnline(p.getUniqueId());
        if (d == null || !d.enabled()) return;
        Session sess = session(p);
        long now = System.currentTimeMillis();
        if (nowFlying) {
            if (sess.flyingSince < 0) sess.flyingSince = now;
        } else {
            sess.stopClock(now);
        }
    }

    private Session session(Player p) {
        return sessions.computeIfAbsent(p.getUniqueId(), u -> new Session());
    }

    // ---------------------------------------------------------------- every second

    public void tick() {
        if (++tickCount % PRUNE_EVERY_TICKS == 0) combat.prune();
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
        if (d == null || !d.enabled() || isExemptMode(p)) {
            display.hide(p);
            return;
        }
        Session sess = session(p);
        long now = System.currentTimeMillis();
        Location loc = p.getLocation();

        // Full checks (world, region, speed) only when the player moved to another block or every 5s.
        boolean full = now >= sess.nextFullCheck || sess.movedBlock(loc);
        Denial denial = full ? check(p, loc, d) : quickCheck(p, d);
        if (denial != Denial.NONE) {
            deny(p, d, denial);
            return;
        }
        if (full) {
            sess.nextFullCheck = now + FULL_CHECK_INTERVAL_MS;
            sess.rememberBlock(loc);
            applySpeed(p);
        }
        // Something (a world plugin, game mode change) took flight away; give it back.
        if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
            applySpeed(p);
        }

        boolean flying = p.isFlying();
        if (isUnlimited(p)) {
            sess.stopClock(now);
            sess.carryMs = 0;
            display.hide(p);
            return;
        }
        sess.fullTime = Math.max(sess.fullTime, d.seconds());

        if (s().afkPause && isAfk(p)) {
            sess.restartClock(now, flying); // time while AFK is not charged
            if (flying) display.show(p, FlyDisplay.State.PAUSED, d.seconds(), sess.fullTime);
            else display.hide(p);
            return;
        }

        sess.collect(now, flying);
        long charge = sess.carryMs / 1000;
        boolean countdown = false;
        if (charge > 0) {
            sess.carryMs -= charge * 1000;
            long before = d.seconds();
            long after = Math.max(0, before - charge);
            d.seconds(after);
            if (after <= 0) {
                deny(p, d, Denial.NO_TIME);
                return;
            }
            // Did we pass one of the countdown marks (after <= mark < before)?
            if (s().countdownEnabled && !s().countdownAt.subSet(after, true, before, false).isEmpty()) {
                countdown = true;
                if (s().countdownSound != null) p.playSound(s().countdownSound);
            }
        }

        if (flying) {
            display.show(p, countdown ? FlyDisplay.State.COUNTDOWN : FlyDisplay.State.FLYING, d.seconds(), sess.fullTime);
        } else {
            display.hide(p);
        }
    }

    /** Re-checks a player right away (world change, teleport, respawn, game mode change). */
    public void recheck(Player p) {
        FlyData d = plugin.data().getIfOnline(p.getUniqueId());
        if (d == null || !d.enabled()) return;
        if (isExemptMode(p)) {
            display.hide(p);
            return;
        }
        Session sess = session(p);
        sess.nextFullCheck = 0;
        Denial denial = check(p, p.getLocation(), d);
        if (denial != Denial.NONE) {
            deny(p, d, denial);
        } else if (!p.getAllowFlight()) {
            p.setAllowFlight(true);
            applySpeed(p);
        }
    }

    /** PvP hit: tag and knock them out of the sky (combat landing rule applies). */
    public void onCombat(Player p) {
        if (!s().combatEnabled || p.hasPermission("dkfly.bypass.combat")) return;
        combat.tag(p.getUniqueId(), s().combatCooldown);
        FlyData d = plugin.data().getIfOnline(p.getUniqueId());
        if (d != null && d.enabled() && !isExemptMode(p)) deny(p, d, Denial.COMBAT);
    }

    /** Called on reload: settings may have changed the display mode. */
    public void onReload() {
        display.hideAll(Bukkit.getOnlinePlayers());
        for (Session sess : sessions.values()) sess.nextFullCheck = 0;
    }

    /** Per-player flight bookkeeping (main thread only). */
    private static final class Session {
        long flyingSince = -1; // when the current flight segment started, -1 = not flying
        long carryMs;          // flown time not yet charged
        long fullTime;         // seconds at the start of this flight, the boss bar's 100%
        long nextFullCheck;
        UUID worldId;
        int bx, by, bz;

        void collect(long now, boolean flying) {
            if (flyingSince >= 0) carryMs += Math.max(0, now - flyingSince);
            flyingSince = flying ? now : -1;
        }

        void stopClock(long now) {
            collect(now, false);
        }

        /** Starts a fresh segment without charging the time since the last one (AFK). */
        void restartClock(long now, boolean flying) {
            flyingSince = flying ? now : -1;
        }

        boolean movedBlock(Location l) {
            return l.getWorld() == null || !l.getWorld().getUID().equals(worldId)
                    || l.getBlockX() != bx || l.getBlockY() != by || l.getBlockZ() != bz;
        }

        void rememberBlock(Location l) {
            worldId = l.getWorld() == null ? null : l.getWorld().getUID();
            bx = l.getBlockX();
            by = l.getBlockY();
            bz = l.getBlockZ();
        }
    }
}
