package com.direk.dkfly;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

/**
 * dkFly's console banner: logo, version, hooks and settings.
 * Printed one tick after enable, once every soft dependency is known.
 * The logo is plain text and values are unparsed placeholders, so
 * characters like {@code \} and {@code <} can't break the formatting.
 * Turn it off with "startup-banner: false".
 * Author: direk james
 */
final class FlyBanner {

    private static final String[] LOGO = {
            "     _   _      _____   _",
            "  __| | | | __ |  ___| | |  _   _",
            " / _` | | |/ / | |_    | | | | | |",
            "| (_| | |   <  |  _|   | | | |_| |",
            " \\__,_| |_|\\_\\ |_|     |_|  \\__, |",
            "                            |___/"
    };

    private static final TextColor TOP = TextColor.color(0x7cc6ff);
    private static final TextColor BOTTOM = TextColor.color(0xe0f4ff);
    private static final String LINE = "<dark_gray>" + "-".repeat(56);
    private static final String YES = "<green>ON</green>";
    private static final String NO = "<dark_gray>off</dark_gray>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final DkFly plugin;

    FlyBanner(DkFly plugin) {
        this.plugin = plugin;
    }

    void print(boolean placeholderApi, long enabledMs) {
        String version = plugin.getPluginMeta().getVersion();
        if (!plugin.settings().startupBanner) {
            plugin.getLogger().info("dkFly v" + version + " enabled in " + enabledMs + " ms");
            return;
        }
        Settings s = plugin.settings();
        var flight = plugin.flight();

        blank();
        line(LINE);
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        for (int i = 0; i < LOGO.length; i++) {
            float t = (float) i / (LOGO.length - 1);
            console.sendMessage(Component.text(LOGO[i], TextColor.lerp(t, TOP, BOTTOM)));
        }
        blank();
        line("  <white><bold>dkFly</bold></white> <gray>v<v></gray>  <dark_gray>|</dark_gray>  <gray>by</gray> <#7cc6ff>direk james",
                v(version));
        line("  <dark_gray><italic>Timed flight for the dk suite  (dkCore v<v>)", v(plugin.core().getPluginMeta().getVersion()));
        line(LINE);

        // Integrations
        row("WorldGuard", hook(flight.worldGuardInstalled(), s.worldGuard,
                "flag <white>dkfly</white> registered"));
        row("PAPI", placeholderApi ? YES + " <dark_gray>%dkfly_time% and 4 more" : NO);
        String eco = plugin.economy().name();
        row("Economy", eco.equals("none") ? "<yellow>none found (fly shop disabled)" : YES + " <dark_gray><v>", v(eco));
        String afk;
        if (!s.afkPause) afk = NO;
        else if (plugin.core().hasAfkProvider()) afk = YES + " <dark_gray>pauses time (DirekAntiAFK)";
        else if (s.purpurAfkFallback) afk = YES + " <dark_gray>pauses time (Purpur fallback)";
        else afk = "<yellow>no AFK provider";
        row("AFK", afk);
        line(LINE);

        // Settings
        row("Shop", plugin.shop().enabled() ? YES + " <dark_gray><v> packages" : NO, v(plugin.shop().packageCount()));
        row("Transfers", s.transferEnabled ? YES + " <dark_gray>min <v>, tax <t>%" : NO,
                v(s.time.format(s.transferMin)), t("t", trim(s.transferTax)));
        row("Blacklist", "<white><v></white> <dark_gray>world(s)", v(s.worldBlacklist.size()));
        row("Max time", "<white><v>", v(s.maxTime > 0 ? s.time.format(s.maxTime) : "no limit"));
        row("Combat", s.combatEnabled ? YES + " <dark_gray><v> cooldown" : NO, v(s.time.format(s.combatCooldown)));
        row("Display", "<white><v>", v(s.display.name().toLowerCase()));
        row("Status", "<green>Enabled</green> <dark_gray>in <v> ms", v(enabledMs));
        line(LINE);
        blank();
    }

    private static String hook(boolean installed, boolean enabledInConfig, String detail) {
        if (!installed) return "<dark_gray>not installed";
        if (!enabledInConfig) return "<yellow>installed, off in config";
        return YES + " <dark_gray>" + detail;
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void row(String label, String value, TagResolver... values) {
        line("  <#7cc6ff>" + String.format("%-11s", label) + "</#7cc6ff><gray>" + value, values);
    }

    private static void line(String miniMessage, TagResolver... values) {
        Bukkit.getConsoleSender().sendMessage(MM.deserialize(miniMessage, TagResolver.resolver(values)));
    }

    private static void blank() {
        Bukkit.getConsoleSender().sendMessage(Component.text(" "));
    }

    private static TagResolver v(Object value) {
        return Placeholder.unparsed("v", String.valueOf(value));
    }

    private static TagResolver t(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
