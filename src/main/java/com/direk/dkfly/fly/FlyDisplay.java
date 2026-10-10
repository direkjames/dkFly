package com.direk.dkfly.fly;

import com.direk.dkcore.message.Messages;
import com.direk.dkfly.DkFly;
import com.direk.dkfly.Settings;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shows remaining fly time on a boss bar and/or the action bar (config "display.mode").
 * One boss bar per player, created once and reused; it's only visible while they fly.
 * Author: direk james
 */
public final class FlyDisplay {

    public enum State { FLYING, PAUSED, COUNTDOWN }

    private final DkFly plugin;
    private final Map<UUID, Bar> bars = new HashMap<>();

    public FlyDisplay(DkFly plugin) {
        this.plugin = plugin;
    }

    /**
     * Updates the timer for a player who is flying with limited time.
     *
     * @param left      seconds left
     * @param fullTime  seconds they had when this flight started (the 100% mark)
     */
    public void show(Player p, State state, long left, long fullTime) {
        Settings s = plugin.settings();
        Messages m = plugin.messages();
        var timeTag = Messages.text("time", s.time.format(left));

        if (s.display.bossBar()) {
            String key = switch (state) {
                case FLYING -> "bossbar-flying";
                case PAUSED -> "bossbar-paused";
                case COUNTDOWN -> "bossbar-countdown";
            };
            Component title = m.get(key, timeTag);
            float progress = fullTime <= 0 ? 1f : (float) Math.max(0, Math.min(1, (double) left / fullTime));
            BossBar.Color color;
            if (state == State.COUNTDOWN || progress <= s.bossBarLowBelow) color = s.bossBarLow;
            else if (progress <= s.bossBarMediumBelow) color = s.bossBarMedium;
            else color = s.bossBarHigh;

            Bar bar = bars.get(p.getUniqueId());
            if (bar == null) {
                bar = new Bar(BossBar.bossBar(title, progress, color, s.bossBarOverlay));
                bars.put(p.getUniqueId(), bar);
            } else {
                bar.bar.name(title);
                bar.bar.progress(progress);
                bar.bar.color(color);
                bar.bar.overlay(s.bossBarOverlay);
            }
            if (!bar.visible) {
                p.showBossBar(bar.bar);
                bar.visible = true;
            }
        }

        // Countdown warnings always reach the player: on the action bar too unless the boss bar shows them.
        if (s.display.actionBar() || (state == State.COUNTDOWN && !s.display.bossBar())) {
            String key = switch (state) {
                case FLYING -> "actionbar-flying";
                case PAUSED -> "actionbar-paused";
                case COUNTDOWN -> "actionbar-countdown";
            };
            m.actionBar(p, key, timeTag);
        }
    }

    /** Hides the boss bar (landed, fly off, unlimited...). Cheap to call every second. */
    public void hide(Player p) {
        Bar bar = bars.get(p.getUniqueId());
        if (bar != null && bar.visible) {
            p.hideBossBar(bar.bar);
            bar.visible = false;
        }
    }

    public void forget(Player p) {
        Bar bar = bars.remove(p.getUniqueId());
        if (bar != null && bar.visible) p.hideBossBar(bar.bar);
    }

    /** Hides every boss bar (reload or shutdown). */
    public void hideAll(Iterable<? extends Player> players) {
        for (Player p : players) forget(p);
        bars.clear();
    }

    private static final class Bar {
        final BossBar bar;
        boolean visible;

        Bar(BossBar bar) {
            this.bar = bar;
        }
    }
}
