package com.direk.dkfly;

import com.direk.dkcore.util.TimeFormatter;
import com.direk.dkcore.util.TimeUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * dkFly's config.yml, read once on enable and on /flytime reload.
 * Author: direk james
 */
public final class Settings {

    public enum LandingMode { SLOW_FALLING, NO_FALL_DAMAGE, BOTH }

    public final Set<String> worldBlacklist;
    public final long maxTime; // 0 = no cap

    public final float defaultSpeed;
    public final Map<String, Float> speedPermissions;

    public final LandingMode landingMode;
    public final long landingMaxSeconds;

    public final boolean countdownEnabled;
    public final Set<Long> countdownAt;
    public final Sound countdownSound; // null = no sound
    public final boolean actionbarWhileFlying;

    public final boolean persistToggle;

    public final boolean afkPause;
    public final boolean purpurAfkFallback;

    public final boolean combatEnabled;
    public final boolean combatDisableFly;
    public final long combatCooldown;

    public final boolean worldGuard;

    public final boolean bentoBox;
    public final Set<String> bentoGamemodes;
    public final boolean bentoOwnIslandOnly;
    public final boolean bentoProtectionRangeOnly;

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

    public final int autosaveMinutes;

    public Settings(YamlConfiguration c, Logger log) {
        worldBlacklist = new HashSet<>(c.getStringList("world-blacklist"));
        maxTime = TimeUtil.parseOr(c.getString("max-time", "none"), 0);

        defaultSpeed = clampSpeed(c.getDouble("speed.default", 0.1));
        speedPermissions = new LinkedHashMap<>();
        for (String entry : c.getStringList("speed.permissions")) {
            int i = entry.lastIndexOf(':');
            if (i <= 0) { log.warning("Bad speed permission entry: " + entry); continue; }
            try {
                speedPermissions.put(entry.substring(0, i).trim(), clampSpeed(Double.parseDouble(entry.substring(i + 1).trim())));
            } catch (NumberFormatException e) {
                log.warning("Bad speed in entry: " + entry);
            }
        }

        LandingMode lm;
        try {
            lm = LandingMode.valueOf(c.getString("landing.mode", "BOTH").toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warning("Unknown landing.mode, using BOTH");
            lm = LandingMode.BOTH;
        }
        landingMode = lm;
        landingMaxSeconds = Math.max(1, TimeUtil.parseOr(c.getString("landing.max-duration", "30s"), 30));

        countdownEnabled = c.getBoolean("countdown.enabled", true);
        countdownAt = new HashSet<>();
        for (Object o : c.getList("countdown.at", List.of())) {
            try { countdownAt.add(Long.parseLong(String.valueOf(o))); } catch (NumberFormatException ignored) {}
        }
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
        actionbarWhileFlying = c.getBoolean("actionbar-while-flying", true);

        persistToggle = c.getBoolean("persist-toggle", true);

        afkPause = c.getBoolean("afk.pause-timer", true);
        purpurAfkFallback = c.getBoolean("afk.use-purpur-afk-fallback", true);

        combatEnabled = c.getBoolean("combat.enabled", true);
        combatDisableFly = c.getBoolean("combat.disable-fly", true);
        combatCooldown = TimeUtil.parseOr(c.getString("combat.cooldown", "15s"), 15);

        worldGuard = c.getBoolean("worldguard.enabled", true);

        bentoBox = c.getBoolean("bentobox.enabled", true);
        bentoGamemodes = new HashSet<>();
        for (String s : c.getStringList("bentobox.gamemodes")) bentoGamemodes.add(s.toLowerCase());
        bentoOwnIslandOnly = c.getBoolean("bentobox.own-island-only", true);
        bentoProtectionRangeOnly = c.getBoolean("bentobox.protection-range-only", true);

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

        autosaveMinutes = Math.max(1, c.getInt("autosave-minutes", 5));
    }

    /** Applies the max-time cap (if any). */
    public long cap(long seconds) {
        return maxTime > 0 ? Math.min(seconds, maxTime) : seconds;
    }

    public List<String> speedPermissionNames() {
        return new ArrayList<>(speedPermissions.keySet());
    }

    private static float clampSpeed(double v) {
        return (float) Math.max(0.01, Math.min(1.0, v));
    }
}
