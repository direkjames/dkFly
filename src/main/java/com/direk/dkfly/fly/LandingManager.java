package com.direk.dkfly.fly;

import com.direk.dkfly.Settings;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Brings players down when flight is turned off while they're in the air.
 * Depending on why it was turned off they get Slow Falling, fall-damage protection,
 * both, or nothing (see config "landing"). Protection ends as soon as they touch
 * the ground, or after landing.max-duration as a safety limit.
 * Author: direk james
 */
public final class LandingManager {

    private static final Vector DOWN = new Vector(0, -1, 0);

    private final Map<UUID, Protection> active = new HashMap<>();

    /** Starts the landing effect. Does nothing if the player is already on the ground. */
    public void land(Player player, Settings.Landing mode, long maxSeconds) {
        clear(player);
        if (mode == Settings.Landing.NONE || isGrounded(player)) return;

        long until = System.currentTimeMillis() + maxSeconds * 1000L;
        boolean slow = false;
        if (mode.slowFalling()) {
            int ticks = (int) Math.min(Integer.MAX_VALUE, maxSeconds * 20L);
            // ambient=false, particles=false, icon=true. The "no particles" flag marks it as ours.
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0, false, false, true));
            slow = true;
        }
        active.put(player.getUniqueId(), new Protection(until, mode.noFallDamage(), slow));
    }

    /** @return true if this fall damage should be cancelled. Ends the protection either way. */
    public boolean shouldCancelFall(Player player) {
        Protection p = active.get(player.getUniqueId());
        if (p == null) return false;
        clear(player);
        return p.noFallDamage && System.currentTimeMillis() <= p.until;
    }

    /** Called every second: ends protection for players who have landed or timed out. */
    public void tick(Player player) {
        Protection p = active.get(player.getUniqueId());
        if (p == null) return;
        boolean landed = isGrounded(player) && player.getFallDistance() == 0f;
        if (landed || player.isFlying() || System.currentTimeMillis() > p.until) clear(player);
    }

    public void clear(Player player) {
        Protection p = active.remove(player.getUniqueId());
        if (p == null || !p.gaveSlowFalling) return;
        PotionEffect effect = player.getPotionEffect(PotionEffectType.SLOW_FALLING);
        // Only remove the effect we gave, so Slow Falling potions players drank are kept.
        if (effect != null && !effect.hasParticles() && !effect.isAmbient()) {
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
    }

    public void forget(UUID uuid) {
        active.remove(uuid);
    }

    @SuppressWarnings("deprecation") // isOnGround is client-reported but fine for this purpose
    public static boolean isGrounded(Player player) {
        return player.isOnGround() || player.isInWater() || player.isClimbing() || player.isInsideVehicle();
    }

    /**
     * True if the player is higher than {@code maxHeight} blocks above the ground (or water) below them.
     */
    public static boolean isHigherThan(Player player, double maxHeight) {
        if (isGrounded(player)) return false;
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return false;
        RayTraceResult hit = loc.getWorld().rayTraceBlocks(loc, DOWN, maxHeight + 0.01,
                FluidCollisionMode.ALWAYS, true);
        return hit == null;
    }

    private record Protection(long until, boolean noFallDamage, boolean gaveSlowFalling) {}
}
