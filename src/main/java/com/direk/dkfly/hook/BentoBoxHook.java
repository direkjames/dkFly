package com.direk.dkfly.hook;

import com.direk.dkfly.Settings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.addons.GameModeAddon;
import world.bentobox.bentobox.database.objects.Island;

import java.util.Locale;
import java.util.Optional;

/**
 * AOneBlock (or any BentoBox game mode): only fly on your own/team island.
 * Only loaded when BentoBox is installed.
 * Author: direk james
 */
public final class BentoBoxHook {

    private BentoBoxHook() {}

    /** @return true if flying is allowed here. Worlds of other game modes are always allowed. */
    public static boolean canFly(Player player, Location loc, Settings s) {
        BentoBox bb = BentoBox.getInstance();
        if (bb == null || loc.getWorld() == null) return true;
        if (!bb.getIWM().inWorld(loc)) return true;

        if (!s.bentoGamemodes.isEmpty()) {
            Optional<GameModeAddon> addon = bb.getIWM().getAddon(loc.getWorld());
            if (addon.isEmpty()) return true;
            String name = addon.get().getDescription().getName().toLowerCase(Locale.ROOT);
            if (!s.bentoGamemodes.contains(name)) return true;
        }

        if (!s.bentoOwnIslandOnly) return true;

        Optional<Island> island = bb.getIslands().getIslandAt(loc);
        if (island.isEmpty()) return false;
        Island is = island.get();
        if (s.bentoProtectionRangeOnly && !is.onIsland(loc)) return false;
        return is.getMemberSet().contains(player.getUniqueId());
    }
}
