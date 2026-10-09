package com.direk.dkfly.command;

import com.direk.dkcore.command.DkCommand;
import com.direk.dkcore.command.SubCommand;
import com.direk.dkcore.message.Messages;
import com.direk.dkcore.util.TimeUtil;
import com.direk.dkfly.DkFly;
import com.direk.dkfly.Settings;
import com.direk.dkfly.data.FlyData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongUnaryOperator;

/**
 * /flytime                          your fly time
 * /flytime shop                     fly shop GUI
 * /flytime pay <player> <time>      send fly time
 * /flytime confirm                  confirm a transfer
 * /flytime check <player>           someone's fly time (dkfly.check.others)
 * /flytime give|take|set <p> <t>    admin (dkfly.admin)
 * /flytime reload                   admin (dkfly.admin)
 * /flytime help
 * Author: direk james
 */
public final class FlyTimeCommand extends DkCommand {

    private static final List<String> TIME_HINTS = List.of("30s", "5m", "15m", "1h", "1d");

    private final DkFly plugin;

    public FlyTimeCommand(DkFly plugin) {
        super("flytime", "dkfly.use", plugin.messages());
        this.plugin = plugin;

        sub(new SubCommand("help", null, false, "?") {
            @Override public void execute(CommandSender sender, String[] args) { help(sender); }
        });

        sub(new SubCommand("shop", "dkfly.shop", true, "buy", "store") {
            @Override public void execute(CommandSender sender, String[] args) {
                if (!plugin.shop().enabled()) { messages.send(sender, "shop-disabled"); return; }
                plugin.shop().open((Player) sender);
            }
        });

        sub(new SubCommand("pay", "dkfly.transfer", true, "send", "transfer") {
            @Override public void execute(CommandSender sender, String[] args) {
                if (args.length < 2) { messages.send(sender, "usage-pay"); return; }
                plugin.transfers().request((Player) sender, args[0], args[1]);
            }
            @Override public List<String> tab(CommandSender sender, String[] args) {
                if (args.length == 1) return onlineNames();
                if (args.length == 2) return TIME_HINTS;
                return List.of();
            }
        });

        sub(new SubCommand("confirm", "dkfly.transfer", true) {
            @Override public void execute(CommandSender sender, String[] args) {
                plugin.transfers().confirm((Player) sender);
            }
        });

        sub(new SubCommand("check", "dkfly.check.others", false, "see") {
            @Override public void execute(CommandSender sender, String[] args) {
                if (args.length < 1) { messages.send(sender, "usage-check"); return; }
                check(sender, args[0]);
            }
            @Override public List<String> tab(CommandSender sender, String[] args) {
                return args.length == 1 ? onlineNames() : List.of();
            }
        });

        sub(adminSub("give"));
        sub(adminSub("take"));
        sub(adminSub("set"));

        sub(new SubCommand("reload", "dkfly.admin", false) {
            @Override public void execute(CommandSender sender, String[] args) {
                plugin.reloadAll();
                messages.send(sender, "reloaded");
            }
        });
    }

    @Override
    protected void root(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            help(sender);
            return;
        }
        if (args.length > 0) {
            help(sender);
            return;
        }
        FlyData d = plugin.data().get(p);
        if (plugin.flight().isUnlimited(p)) {
            messages.send(p, "balance-unlimited");
            return;
        }
        Settings s = plugin.settings();
        messages.send(p, "balance",
                Messages.text("time", s.time.format(d.seconds())),
                Messages.styled("status", d.enabled() ? s.statusOn : s.statusOff));
    }

    private void help(CommandSender sender) {
        messages.send(sender, "help");
        if (sender.hasPermission("dkfly.admin")) messages.send(sender, "help-admin");
    }

    private void check(CommandSender sender, String name) {
        plugin.core().players().find(name).thenAccept(found -> plugin.tasks().sync(() -> {
            if (found.isEmpty()) {
                messages.send(sender, "unknown-player", Messages.text("player", name));
                return;
            }
            var ref = found.get();
            plugin.data().seconds(ref.uuid()).thenAccept(sec -> plugin.tasks().sync(() ->
                    messages.send(sender, "balance-other",
                            Messages.text("player", ref.name()),
                            Messages.text("time", plugin.settings().time.format(sec)))));
        }));
    }

    private SubCommand adminSub(String action) {
        return new SubCommand(action, "dkfly.admin", false) {
            @Override public void execute(CommandSender sender, String[] args) {
                if (args.length < 2) { messages.send(sender, "usage-admin"); return; }
                admin(sender, action, args[0], args[1]);
            }
            @Override public List<String> tab(CommandSender sender, String[] args) {
                if (args.length == 1) return onlineNames();
                if (args.length == 2) return TIME_HINTS;
                return List.of();
            }
        };
    }

    private void admin(CommandSender sender, String action, String name, String timeText) {
        long amount = TimeUtil.parseSeconds(timeText);
        if (amount < 0 || (amount == 0 && !action.equals("set"))) {
            messages.send(sender, "invalid-time", Messages.text("input", timeText));
            return;
        }
        Settings s = plugin.settings();
        AtomicBoolean capped = new AtomicBoolean(false);
        LongUnaryOperator change = switch (action) {
            case "give" -> v -> capTracked(s, v + amount, capped);
            case "take" -> v -> Math.max(0, v - amount);
            default -> v -> capTracked(s, amount, capped);
        };

        plugin.core().players().find(name).thenAccept(found -> plugin.tasks().sync(() -> {
            if (found.isEmpty()) {
                messages.send(sender, "unknown-player", Messages.text("player", name));
                return;
            }
            var ref = found.get();
            plugin.data().modify(ref.uuid(), change).whenComplete((total, error) -> plugin.tasks().sync(() -> {
                if (error != null) {
                    messages.send(sender, "pay-failed");
                    return;
                }
                String key = switch (action) {
                    case "give" -> "admin-give";
                    case "take" -> "admin-take";
                    default -> "admin-set";
                };
                messages.send(sender, key,
                        Messages.text("player", ref.name()),
                        Messages.text("time", s.time.format(amount)),
                        Messages.text("total", s.time.format(total)));
                if (capped.get()) messages.send(sender, "admin-capped", Messages.text("max", s.time.format(s.maxTime)));

                Player online = Bukkit.getPlayer(ref.uuid());
                if (online != null && action.equals("give") && online != sender) {
                    messages.send(online, "received-admin", Messages.text("time", s.time.format(amount)));
                }
            }));
        }));
    }

    private static long capTracked(Settings s, long value, AtomicBoolean capped) {
        long c = s.cap(value);
        if (c < value) capped.set(true);
        return c;
    }

    @Override
    protected List<String> rootTab(CommandSender sender, String[] args) {
        return List.of();
    }
}
