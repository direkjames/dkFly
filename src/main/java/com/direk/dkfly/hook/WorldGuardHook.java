package com.direk.dkfly.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Adds the "dkfly" region flag.  /rg flag spawn dkfly deny
 * Only loaded when WorldGuard is installed.
 * Author: direk james
 */
public final class WorldGuardHook {

    private static StateFlag FLAG;

    private WorldGuardHook() {}

    /** Must be called from onLoad (before WorldGuard enables). */
    public static void registerFlag(Logger log) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag("dkfly", true);
            registry.register(flag);
            FLAG = flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get("dkfly");
            if (existing instanceof StateFlag sf) {
                FLAG = sf; // registered already (e.g. after a /reload)
            } else {
                log.severe("Another plugin already registered a 'dkfly' WorldGuard flag; region checks are off.");
            }
        } catch (IllegalStateException e) {
            log.warning("WorldGuard flag could not be registered (was dkFly loaded late?). Region checks are off.");
        }
    }

    public static boolean ready() {
        return FLAG != null;
    }

    /** @return true if flying is allowed at this location for this player. */
    public static boolean canFly(Player player, Location location) {
        if (FLAG == null) return true;
        LocalPlayer lp = WorldGuardPlugin.inst().wrapPlayer(player);
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return query.testState(BukkitAdapter.adapt(location), lp, FLAG);
    }
}
