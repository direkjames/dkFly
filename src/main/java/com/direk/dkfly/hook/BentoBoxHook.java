package com.direk.dkfly.hook;

import com.direk.dkfly.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * AOneBlock (or any BentoBox game mode): only fly on your own/team island.
 * <p>
 * BentoBox is NOT a compile dependency. Its API is looked up at runtime with
 * reflection, so dkFly builds without BentoBox and still works with whatever
 * BentoBox version the server runs. If the lookup fails, the island rule is
 * turned off and flying is allowed (a warning is logged once).
 * <p>
 * Author: direk james
 */
public final class BentoBoxHook {

    private static Api api;
    private static boolean failed;

    private BentoBoxHook() {}

    /** Call once BentoBox is enabled. Returns true if the API was found. */
    public static boolean init(Logger log) {
        api = null;
        failed = false;
        Plugin bb = Bukkit.getPluginManager().getPlugin("BentoBox");
        if (bb == null || !bb.isEnabled()) return false;
        try {
            api = new Api(bb);
            return true;
        } catch (Throwable t) {
            failed = true;
            log.warning("BentoBox was found but its API could not be read (" + t + "). Island fly rules are off.");
            return false;
        }
    }

    public static boolean ready() {
        return api != null && !failed;
    }

    /** @return true if flying is allowed here. Worlds of other game modes are always allowed. */
    public static boolean canFly(Player player, Location loc, Settings s) {
        if (!ready() || loc.getWorld() == null) return true;
        try {
            Object iwm = api.getIWM.invoke(api.bentoBox);
            if (!(boolean) api.inWorld.invoke(iwm, loc)) return true;

            if (!s.bentoGamemodes.isEmpty()) {
                Optional<?> addon = (Optional<?>) api.getAddon.invoke(iwm, loc.getWorld());
                if (addon.isEmpty()) return true;
                Object description = addon.get().getClass().getMethod("getDescription").invoke(addon.get());
                String name = String.valueOf(description.getClass().getMethod("getName").invoke(description));
                if (!s.bentoGamemodes.contains(name.toLowerCase(Locale.ROOT))) return true;
            }

            if (!s.bentoOwnIslandOnly) return true;

            Object islands = api.getIslands.invoke(api.bentoBox);
            Optional<?> island = (Optional<?>) api.getIslandAt.invoke(islands, loc);
            if (island.isEmpty()) return false;
            Object is = island.get();
            if (s.bentoProtectionRangeOnly && !(boolean) is.getClass().getMethod("onIsland", Location.class).invoke(is, loc)) {
                return false;
            }
            Collection<?> members = (Collection<?>) is.getClass().getMethod("getMemberSet").invoke(is);
            return members.contains(player.getUniqueId());
        } catch (Throwable t) {
            failed = true;
            Bukkit.getLogger().warning("[dkFly] BentoBox API call failed (" + t + "). Island fly rules are off until restart.");
            return true;
        }
    }

    /** Cached reflective handles to the BentoBox API. */
    private static final class Api {
        final Object bentoBox;
        final Method getIWM, inWorld, getAddon, getIslands, getIslandAt;

        Api(Plugin plugin) throws ReflectiveOperationException {
            this.bentoBox = plugin; // the BentoBox plugin instance is the API entry point
            Class<?> cls = plugin.getClass();
            getIWM = cls.getMethod("getIWM");
            getIslands = cls.getMethod("getIslands");
            Class<?> iwmClass = getIWM.getReturnType();
            inWorld = iwmClass.getMethod("inWorld", Location.class);
            getAddon = iwmClass.getMethod("getAddon", World.class);
            getIslandAt = getIslands.getReturnType().getMethod("getIslandAt", Location.class);
        }
    }
}
