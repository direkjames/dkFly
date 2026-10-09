package com.direk.dkfly.fly;

import com.direk.dkfly.Settings;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Brings players down safely when their flight is turned off mid-air.
 * Slow Falling and/or fall-damage protection last until they touch the ground
 * (or landing.max-duration runs out).
 * Author: direk james
 */
public final class LandingManager {

    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, Boolean> gaveSlowFalling = new HashMap<>();

    public void safeLand(Player player, Settings s) {
        if (isGrounded(player)) return;
        long until = System.currentTimeMillis() + s.landingMaxSeconds * 1000L;
        protectedUntil.put(player.getUniqueId(), until);

        boolean slow = s.landingMode != Settings.LandingMode.NO_FALL_DAMAGE;
        if (slow) {
            int ticks = (int) Math.min(Integer.MAX_VALUE, s.landingMaxSeconds * 20L);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0, false, false, true));
            gaveSlowFalling.put(player.getUniqueId(), true);
        }
        // Even in SLOW_FALLING mode we keep the record so the effect is removed on landing.
    }

    /** @return true if this fall damage should be cancelled. */
    public boolean shouldCancelFall(Player player, Settings s) {
        Long until = protectedUntil.get(player.getUniqueId());
        if (until == null) return false;
        clear(player);
        return s.landingMode != Settings.LandingMode.SLOW_FALLING && System.currentTimeMillis() <= until;
    }

    /** Called every second: ends protection for players who have landed or timed out. */
    public void tick(Player player) {
        Long until = protectedUntil.get(player.getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until || (isGrounded(player) && player.getFallDistance() == 0f)
                || player.isFlying()) {
            clear(player);
        }
    }

    public void clear(Player player) {
        protectedUntil.remove(player.getUniqueId());
        if (gaveSlowFalling.remove(player.getUniqueId()) != null) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.SLOW_FALLING);
            // Only remove the effect we gave (ambient=false, no particles) so potions players drank stay.
            if (effect != null && !effect.hasParticles()) {
                player.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }

    public void forget(UUID uuid) {
        protectedUntil.remove(uuid);
        gaveSlowFalling.remove(uuid);
    }

    public boolean isProtected(Player player) {
        return protectedUntil.containsKey(player.getUniqueId());
    }

    @SuppressWarnings("deprecation")
    static boolean isGrounded(Player player) {
        return player.isOnGround() || player.isInWater() || player.isClimbing();
    }
}
