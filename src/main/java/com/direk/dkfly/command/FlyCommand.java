package com.direk.dkfly.command;

import com.direk.dkcore.command.DkCommand;
import com.direk.dkcore.message.Messages;
import com.direk.dkfly.DkFly;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /fly            toggle your own flight
 * /fly <player>   toggle someone else's (dkfly.others)
 * Author: direk james
 */
public final class FlyCommand extends DkCommand {

    private final DkFly plugin;

    public FlyCommand(DkFly plugin) {
        super("fly", "dkfly.use", plugin.messages());
        this.plugin = plugin;
    }

    @Override
    protected void root(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                messages.send(sender, "player-only");
                return;
            }
            plugin.flight().toggle(p, p);
            return;
        }

        if (!sender.hasPermission("dkfly.others")) {
            messages.send(sender, "no-permission");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            messages.send(sender, "unknown-player", Messages.text("player", args[0]));
            return;
        }
        boolean before = plugin.data().get(target).enabled();
        boolean on = plugin.flight().toggle(target, sender);
        if (sender == target) return;
        if (on == before) {
            // Denied (no time, blocked world/region, combat, creative...). The target was told why.
            messages.send(sender, "fly-other-failed", Messages.text("player", target.getName()));
        } else {
            messages.send(sender, on ? "fly-on-other" : "fly-off-other", Messages.text("player", target.getName()));
        }
    }

    @Override
    protected List<String> rootTab(CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("dkfly.others")) return onlineNames();
        return List.of();
    }
}
