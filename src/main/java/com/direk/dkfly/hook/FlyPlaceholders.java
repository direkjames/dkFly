package com.direk.dkfly.hook;

import com.direk.dkfly.DkFly;
import com.direk.dkfly.data.FlyData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * %dkfly_time%, %dkfly_time_seconds%, %dkfly_enabled%, %dkfly_status%, %dkfly_unlimited%
 * Author: direk james
 */
public final class FlyPlaceholders extends PlaceholderExpansion {

    private final DkFly plugin;

    private FlyPlaceholders(DkFly plugin) {
        this.plugin = plugin;
    }

    /** Registers the expansion. Only call when PlaceholderAPI is enabled. */
    public static Object hook(DkFly plugin) {
        FlyPlaceholders exp = new FlyPlaceholders(plugin);
        if (exp.register()) plugin.getLogger().info("PlaceholderAPI placeholders registered.");
        return exp;
    }

    public static void unhook(Object exp) {
        ((FlyPlaceholders) exp).unregister();
    }

    @Override public @NotNull String getIdentifier() { return "dkfly"; }
    @Override public @NotNull String getAuthor() { return "direk james"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) return "";
        Player player = offline.getPlayer();
        if (player == null) return "";
        FlyData data = plugin.data().getIfOnline(player.getUniqueId());
        if (data == null) return "";
        boolean unlimited = plugin.flight().isUnlimited(player);

        return switch (params.toLowerCase()) {
            case "time" -> unlimited ? plugin.settings().placeholderUnlimited
                    : plugin.settings().time.format(data.seconds());
            case "time_seconds" -> String.valueOf(data.seconds());
            case "enabled" -> String.valueOf(data.enabled());
            case "status" -> legacy(data.enabled() ? plugin.settings().statusOn : plugin.settings().statusOff);
            case "unlimited" -> String.valueOf(unlimited);
            default -> null;
        };
    }

    /** Placeholders are read by scoreboards/tab plugins that expect legacy color codes. */
    private static String legacy(String miniMessage) {
        return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
