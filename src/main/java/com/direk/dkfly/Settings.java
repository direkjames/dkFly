package com.direk.dkfly;

import com.direk.dkcore.util.TimeFormatter;
import com.direk.dkcore.util.TimeUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * dkFly's config.yml, read once on enable and on /flytime reload.
 * Author: direk james
 */
public final class Settings {

    /** What happens when flight is turned off while the player is in the air. */
    public enum Landing {
        SLOW_FALLING, NO_FALL_DAMAGE, BOTH, NONE;

        public boolean slowFalling() { return this == SLOW_FALLING || this == BOTH; }
        public boolean noFallDamage() { return this == NO_FALL_DAMAGE || this == BOTH; }
    }

    /** Where the live fly timer is shown. */
    public enum Display {
        BOSSBAR, ACTIONBAR, BOTH, NONE;

        public boolean bossBar() { return this == BOSSBAR || this == BOTH; }
        public boolean actionBar() { return this == ACTIONBAR || this == BOTH; }
    }

    public final Set<String> worldBlacklist;
    public final long maxTime; // 0 = no cap

    public final float defaultSpeed;
    public final Map<String, Float> speedPermissions;

    public final double selfDisableMaxHeight;
    public final Landing landingOutOfTime;
    public final Landing landingAdmin;
    public final Landing landingRestricted;
    public final Landing landingCombat;
    public final long landingMaxSeconds;

    public final Display display;
    public final BossBar.Overlay bossBarOverlay;
    public final BossBar.Color bossBarHigh;
    public final BossBar.Color bossBarMedium;
    public final BossBar.Color bossBarLow;
    public final double bossBarMediumBelow; // 0..1
    public final double bossBarLowBelow;    // 0..1

    public final boolean countdownEnabled;
    public final TreeSet<Long> countdownAt;
    public final long countdownHighest;
    public final Sound countdownSound; // null = no sound

    public final boolean persistToggle;

    public final boolean afkPause;
    public final boolean purpurAfkFallback;

    public final boolean combatEnabled;
    public final long combatCooldown;

    public final boolean worldGuard;

    public final String placeholderUnlimited;
    public final String statusOn;
    public final String statusOff;
    public final TimeFormatter time;

    public final boolean transferEnabled;
    public final long transferMin;
    public final long transferCooldown;
    public final double transferTax;
    public final boolean transferConfirm;
    public final long transferConfirmTimeout;
    public final boolean transferOffline;

    public final boolean shopEnabled;
    public final int autosaveMinutes;
    public final boolean startupBanner;

    public Settings(YamlConfiguration c, Logger log) {
        worldBlacklist = new HashSet<>(c.getStringList("world-blacklist"));
        maxTime = TimeUtil.parseOr(c.getString("max-time", "none"), 0);

        defaultSpeed = clampSpeed(c.getDouble("speed.default", 0.1));
        speedPermissions = new LinkedHashMap<>();
        for (String entry : c.getStringList("speed.permissions")) {
            int i = entry.lastIndexOf(':');
            if (i <= 0) { log.warning("Bad speed permission entry: " + entry); continue; }
            try {
                speedPermissions.put(entry.substring(0, i).trim(),
                        clampSpeed(Double.parseDouble(entry.substring(i + 1).trim())));
            } catch (NumberFormatException e) {
                log.warning("Bad speed in entry: " + entry);
            }
        }

        selfDisableMaxHeight = Math.max(0, c.getDouble("landing.self-disable-max-height", 3));
        landingOutOfTime = landing(c, "landing.out-of-time", Landing.BOTH, log);
        landingAdmin = landing(c, "landing.admin", Landing.BOTH, log);
        landingRestricted = landing(c, "landing.restricted-area", Landing.NO_FALL_DAMAGE, log);
        landingCombat = landing(c, "landing.combat", Landing.NONE, log);
        landingMaxSeconds = Math.max(1, TimeUtil.parseOr(c.getString("landing.max-duration", "30s"), 30));

        display = parse(Display.class, c.getString("display.mode", "BOSSBAR"), Display.BOSSBAR, "display.mode", log);
        bossBarOverlay = parse(BossBar.Overlay.class, c.getString("display.bossbar.style", "PROGRESS"),
                BossBar.Overlay.PROGRESS, "display.bossbar.style", log);
        bossBarHigh = parse(BossBar.Color.class, c.getString("display.bossbar.color-high", "GREEN"),
                BossBar.Color.GREEN, "display.bossbar.color-high", log);
        bossBarMedium = parse(BossBar.Color.class, c.getString("display.bossbar.color-medium", "YELLOW"),
                BossBar.Color.YELLOW, "display.bossbar.color-medium", log);
        bossBarLow = parse(BossBar.Color.class, c.getString("display.bossbar.color-low", "RED"),
                BossBar.Color.RED, "display.bossbar.color-low", log);
        bossBarMediumBelow = percent(c.getDouble("display.bossbar.medium-below-percent", 50));
        bossBarLowBelow = percent(c.getDouble("display.bossbar.low-below-percent", 20));

        countdownEnabled = c.getBoolean("countdown.enabled", true);
        countdownAt = new TreeSet<>();
        for (Object o : c.getList("countdown.at", List.of())) {
            try {
                long v = Long.parseLong(String.valueOf(o).trim());
                if (v > 0) countdownAt.add(v);
            } catch (NumberFormatException e) {
                log.warning("Ignoring bad countdown value: " + o);
            }
        }
        countdownHighest = countdownAt.isEmpty() ? 0 : countdownAt.last();
        Sound snd = null;
        if (c.getBoolean("countdown.sound.enabled", true)) {
            try {
                snd = Sound.sound(Key.key(c.getString("countdown.sound.key", "block.note_block.pling")),
                        Sound.Source.MASTER,
                        (float) c.getDouble("countdown.sound.volume", 1.0),
                        (float) c.getDouble("countdown.sound.pitch", 1.4));
            } catch (Exception e) {
                log.warning("Invalid countdown sound key, sound disabled.");
            }
        }
        countdownSound = snd;

        persistToggle = c.getBoolean("persist-toggle", true);

        afkPause = c.getBoolean("afk.pause-timer", true);
        purpurAfkFallback = c.getBoolean("afk.use-purpur-afk-fallback", true);

        combatEnabled = c.getBoolean("combat.enabled", true);
        combatCooldown = TimeUtil.parseOr(c.getString("combat.cooldown", "15s"), 15);

        worldGuard = c.getBoolean("worldguard.enabled", true);

        placeholderUnlimited = c.getString("placeholders.unlimited", "∞");
        statusOn = c.getString("placeholders.status-on", "<green>ON");
        statusOff = c.getString("placeholders.status-off", "<red>OFF");
        time = new TimeFormatter(c.getConfigurationSection("time-format"));

        transferEnabled = c.getBoolean("transfer.enabled", true);
        transferMin = Math.max(1, TimeUtil.parseOr(c.getString("transfer.min-amount", "1m"), 60));
        transferCooldown = TimeUtil.parseOr(c.getString("transfer.cooldown", "1m"), 60);
        transferTax = Math.max(0, Math.min(100, c.getDouble("transfer.tax-percent", 0)));
        transferConfirm = c.getBoolean("transfer.require-confirm", true);
        transferConfirmTimeout = Math.max(5, TimeUtil.parseOr(c.getString("transfer.confirm-timeout", "30s"), 30));
        transferOffline = c.getBoolean("transfer.allow-offline", true);

        shopEnabled = c.getBoolean("shop.enabled", true);
        autosaveMinutes = Math.max(1, c.getInt("autosave-minutes", 5));
        startupBanner = c.getBoolean("startup-banner", true);
    }

    /** Applies the max-time cap (if any). */
    public long cap(long seconds) {
        return maxTime > 0 ? Math.min(seconds, maxTime) : seconds;
    }

    private static Landing landing(YamlConfiguration c, String path, Landing def, Logger log) {
        return parse(Landing.class, c.getString(path, def.name()), def, path, log);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E def, String path, Logger log) {
        if (value == null) return def;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Unknown value '" + value + "' for " + path + ", using " + def.name());
            return def;
        }
    }

    private static double percent(double p) {
        return Math.max(0, Math.min(100, p)) / 100.0;
    }

    private static float clampSpeed(double v) {
        return (float) Math.max(0.01, Math.min(1.0, v));
    }
}
