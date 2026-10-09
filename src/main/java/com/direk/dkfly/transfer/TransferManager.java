package com.direk.dkfly.transfer;

import com.direk.dkcore.message.Messages;
import com.direk.dkcore.player.PlayerService.PlayerRef;
import com.direk.dkcore.util.TimeUtil;
import com.direk.dkfly.DkFly;
import com.direk.dkfly.Settings;
import com.direk.dkfly.data.FlyData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /flytime pay <player> <time> and /flytime confirm.
 * Author: direk james
 */
public final class TransferManager {

    private final DkFly plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public TransferManager(DkFly plugin) {
        this.plugin = plugin;
    }

    private Settings s() { return plugin.settings(); }
    private Messages m() { return plugin.messages(); }
    private String fmt(long seconds) { return s().time.format(seconds); }

    public void request(Player sender, String targetName, String amountText) {
        if (!s().transferEnabled) { m().send(sender, "pay-disabled"); return; }

        long amount = TimeUtil.parseSeconds(amountText);
        if (amount <= 0) { m().send(sender, "invalid-time", Messages.text("input", amountText)); return; }
        if (amount < s().transferMin) {
            m().send(sender, "pay-min", Messages.text("min", fmt(s().transferMin)));
            return;
        }
        if (targetName.equalsIgnoreCase(sender.getName())) { m().send(sender, "pay-self"); return; }

        long cd = cooldownLeft(sender);
        if (cd > 0) { m().send(sender, "pay-cooldown", Messages.text("time", fmt(cd))); return; }

        FlyData d = plugin.data().get(sender);
        if (d.seconds() < amount) {
            m().send(sender, "pay-not-enough", Messages.text("time", fmt(d.seconds())));
            return;
        }

        plugin.core().players().find(targetName).thenAccept(found -> plugin.tasks().sync(() -> {
            if (!sender.isOnline()) return;
            if (found.isEmpty()) {
                m().send(sender, "unknown-player", Messages.text("player", targetName));
                return;
            }
            PlayerRef target = found.get();
            if (target.uuid().equals(sender.getUniqueId())) { m().send(sender, "pay-self"); return; }
            if (!s().transferOffline && Bukkit.getPlayer(target.uuid()) == null) {
                m().send(sender, "pay-offline-disabled", Messages.text("player", target.name()));
                return;
            }

            long received = amount - (long) Math.floor(amount * s().transferTax / 100.0);
            Pending p = new Pending(target, amount, received,
                    System.currentTimeMillis() + s().transferConfirmTimeout * 1000L);

            if (s().transferConfirm) {
                pending.put(sender.getUniqueId(), p);
                m().send(sender, "pay-confirm",
                        Messages.text("time", fmt(amount)),
                        Messages.text("received", fmt(received)),
                        Messages.text("player", target.name()),
                        Messages.text("timeout", fmt(s().transferConfirmTimeout)));
            } else {
                execute(sender, p);
            }
        }));
    }

    public void confirm(Player sender) {
        Pending p = pending.remove(sender.getUniqueId());
        if (p == null) { m().send(sender, "pay-no-pending"); return; }
        if (System.currentTimeMillis() > p.expires()) { m().send(sender, "pay-expired"); return; }
        execute(sender, p);
    }

    private void execute(Player sender, Pending p) {
        FlyData d = plugin.data().get(sender);
        if (d.seconds() < p.amount()) {
            m().send(sender, "pay-not-enough", Messages.text("time", fmt(d.seconds())));
            return;
        }

        // Take from the sender first; give it back if delivery fails.
        d.seconds(d.seconds() - p.amount());
        cooldownUntil.put(sender.getUniqueId(), System.currentTimeMillis() + s().transferCooldown * 1000L);

        long max = s().maxTime;
        AtomicBoolean full = new AtomicBoolean(false);
        plugin.data().modify(p.target().uuid(), current -> {
            if (max > 0 && current + p.received() > max) {
                full.set(true);
                return current;
            }
            return current + p.received();
        }).whenComplete((result, error) -> plugin.tasks().sync(() -> {
            if (error != null || full.get()) {
                refund(sender, p.amount());
                if (full.get()) {
                    m().send(sender, "pay-receiver-full",
                            Messages.text("player", p.target().name()), Messages.text("max", fmt(max)));
                } else {
                    m().send(sender, "pay-failed");
                }
                return;
            }
            m().send(sender, "pay-sent",
                    Messages.text("time", fmt(p.amount())), Messages.text("player", p.target().name()));
            Player receiver = Bukkit.getPlayer(p.target().uuid());
            if (receiver != null) {
                m().send(receiver, "pay-received",
                        Messages.text("time", fmt(p.received())), Messages.text("player", sender.getName()));
            }
            plugin.getLogger().info(sender.getName() + " sent " + p.amount() + "s of fly time to "
                    + p.target().name() + " (received " + p.received() + "s)");
        }));
    }

    private void refund(Player sender, long amount) {
        cooldownUntil.remove(sender.getUniqueId());
        if (sender.isOnline()) {
            FlyData d = plugin.data().get(sender);
            d.seconds(d.seconds() + amount);
        } else {
            plugin.data().modify(sender.getUniqueId(), v -> v + amount);
        }
    }

    private long cooldownLeft(Player p) {
        if (p.hasPermission("dkfly.bypass.cooldown")) return 0;
        Long until = cooldownUntil.get(p.getUniqueId());
        if (until == null) return 0;
        long left = until - System.currentTimeMillis();
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    public void forget(UUID uuid) {
        pending.remove(uuid);
    }

    private record Pending(PlayerRef target, long amount, long received, long expires) {}
}
