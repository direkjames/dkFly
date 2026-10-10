package com.direk.dkfly.shop;

import com.direk.dkcore.config.ConfigFile;
import com.direk.dkcore.economy.EconomyService;
import com.direk.dkcore.gui.Menu;
import com.direk.dkcore.message.Messages;
import com.direk.dkcore.util.TimeUtil;
import com.direk.dkfly.DkFly;
import com.direk.dkfly.data.FlyData;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The fly shop GUI, built from gui.yml.
 * Author: direk james
 */
public final class FlyShop {

    /** Minimum time between purchases per player, so a lagging double-click can't buy twice. */
    private static final long CLICK_COOLDOWN_MS = 250;

    private final DkFly plugin;
    private final ConfigFile file;
    private final List<Package> packages = new ArrayList<>();
    private final List<Decoration> decorations = new ArrayList<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    private String title;
    private int rows;
    private GuiItem filler;
    private GuiItem info;
    private int infoSlot = -1;
    private GuiItem close;
    private int closeSlot = -1;

    public FlyShop(DkFly plugin) {
        this.plugin = plugin;
        boolean firstRun = !new File(plugin.getDataFolder(), "gui.yml").exists();
        this.file = new ConfigFile(plugin, "gui.yml", "items", "decorations");
        if (firstRun) migrateFromConfig();
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        YamlConfiguration c = file.yaml();
        Logger log = plugin.getLogger();
        title = c.getString("title", "<dark_gray>Fly Shop");
        rows = Math.max(1, Math.min(6, c.getInt("rows", 3)));
        int size = rows * 9;
        Set<Integer> used = new HashSet<>();

        filler = null;
        ConfigurationSection f = c.getConfigurationSection("filler");
        if (f != null && f.getBoolean("enabled", true)) filler = GuiItem.from(f, "GRAY_STAINED_GLASS_PANE", log);

        info = null;
        infoSlot = -1;
        ConfigurationSection i = c.getConfigurationSection("info");
        if (i != null && i.getBoolean("enabled", true)) {
            infoSlot = slot(i.getInt("slot", 4), size, "info", used, log);
            if (infoSlot >= 0) info = GuiItem.from(i, "FEATHER", log);
        }

        close = null;
        closeSlot = -1;
        ConfigurationSection cl = c.getConfigurationSection("close");
        if (cl != null && cl.getBoolean("enabled", true)) {
            closeSlot = slot(cl.getInt("slot", size - 5), size, "close", used, log);
            if (closeSlot >= 0) close = GuiItem.from(cl, "BARRIER", log);
        }

        packages.clear();
        ConfigurationSection items = c.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection it = items.getConfigurationSection(id);
                if (it == null) continue;
                long seconds = TimeUtil.parseSeconds(it.getString("time", ""));
                double price = it.getDouble("price", -1);
                if (seconds <= 0) {
                    log.warning("gui.yml: package '" + id + "' has an invalid time; skipped.");
                    continue;
                }
                if (price < 0 || Double.isNaN(price) || Double.isInfinite(price)) {
                    log.warning("gui.yml: package '" + id + "' has an invalid price; skipped.");
                    continue;
                }
                int s = slot(it.getInt("slot", -1), size, "items." + id, used, log);
                if (s < 0) continue;
                packages.add(new Package(id, s, GuiItem.from(it, "FEATHER", log), seconds, price,
                        it.getString("permission", "")));
            }
        }

        decorations.clear();
        ConfigurationSection decos = c.getConfigurationSection("decorations");
        if (decos != null) {
            for (String id : decos.getKeys(false)) {
                ConfigurationSection d = decos.getConfigurationSection(id);
                if (d == null) continue;
                List<Integer> slots = new ArrayList<>(d.getIntegerList("slots"));
                if (d.isInt("slot")) slots.add(d.getInt("slot"));
                GuiItem icon = GuiItem.from(d, "LIGHT_BLUE_STAINED_GLASS_PANE", log);
                for (int raw : slots) {
                    int s = slot(raw, size, "decorations." + id, used, log);
                    if (s >= 0) decorations.add(new Decoration(s, icon));
                }
            }
        }
    }

    /** Validates a slot; warns about out-of-range or duplicate slots. */
    private static int slot(int s, int size, String path, Set<Integer> used, Logger log) {
        if (s < 0 || s >= size) {
            log.warning("gui.yml: '" + path + "' slot " + s + " is outside the GUI (0-" + (size - 1) + "); skipped.");
            return -1;
        }
        if (!used.add(s)) {
            log.warning("gui.yml: '" + path + "' uses slot " + s + ", which is already taken; skipped.");
            return -1;
        }
        return s;
    }

    public boolean enabled() {
        return plugin.settings().shopEnabled;
    }

    public int packageCount() {
        return packages.size();
    }

    public void open(Player p) {
        Optional<EconomyService> eco = plugin.economy().get();
        if (eco.isEmpty()) {
            plugin.messages().send(p, "shop-no-economy");
            return;
        }
        EconomyService economy = eco.get();
        Messages m = plugin.messages();
        FlyData d = plugin.data().get(p);

        String balance = economy.format(economy.balance(p));
        String flyTime = plugin.flight().isUnlimited(p)
                ? plugin.settings().placeholderUnlimited
                : plugin.settings().time.format(d.seconds());
        TagResolver base = TagResolver.resolver(
                Messages.text("balance", balance),
                Messages.text("player", p.getName()));

        Menu menu = new Menu(rows, m.parse(title, base));

        for (Decoration deco : decorations) {
            menu.set(deco.slot(), deco.icon().build(p, m, base));
        }
        if (info != null) {
            menu.set(infoSlot, info.build(p, m, TagResolver.resolver(base, Messages.text("time", flyTime))));
        }
        if (close != null) {
            menu.set(closeSlot, close.build(p, m, base), click -> plugin.tasks().later(p::closeInventory, 1L));
        }
        for (Package pkg : packages) {
            TagResolver r = TagResolver.resolver(base,
                    Messages.text("time", plugin.settings().time.format(pkg.seconds())),
                    Messages.text("price", economy.format(pkg.price())));
            menu.set(pkg.slot(), pkg.icon().build(p, m, r), click -> buy(p, pkg));
        }
        if (filler != null) menu.fill(filler.build(p, m, base));
        menu.open(p);
    }

    private void buy(Player p, Package pkg) {
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < CLICK_COOLDOWN_MS) return;
        lastClick.put(p.getUniqueId(), now);

        Messages m = plugin.messages();
        Optional<EconomyService> eco = plugin.economy().get();
        if (eco.isEmpty()) {
            m.send(p, "shop-no-economy");
            p.closeInventory();
            return;
        }
        EconomyService economy = eco.get();
        if (!pkg.permission().isEmpty() && !p.hasPermission(pkg.permission())) {
            m.send(p, "shop-no-permission");
            return;
        }
        FlyData d = plugin.data().get(p);
        long max = plugin.settings().maxTime;
        if (max > 0 && d.seconds() + pkg.seconds() > max) {
            m.send(p, "shop-full", Messages.text("max", plugin.settings().time.format(max)));
            return;
        }
        if (!economy.has(p, pkg.price()) || !economy.withdraw(p, pkg.price())) {
            m.send(p, "shop-not-enough-money",
                    Messages.text("price", economy.format(pkg.price())),
                    Messages.text("balance", economy.format(economy.balance(p))));
            return;
        }
        plugin.data().modify(p.getUniqueId(), v -> v + pkg.seconds()); // saved right away
        m.send(p, "shop-bought",
                Messages.text("time", plugin.settings().time.format(pkg.seconds())),
                Messages.text("price", economy.format(pkg.price())));
        plugin.getLogger().info(p.getName() + " bought " + pkg.id() + " (" + pkg.seconds() + "s) for " + pkg.price());
        // Refresh balance and time. Opening a new menu inside a click event must wait a tick.
        plugin.tasks().later(() -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) open(p);
        }, 1L);
    }

    public void forget(UUID uuid) {
        lastClick.remove(uuid);
    }

    /**
     * dkFly 1.0.0 kept the shop in config.yml. On the first start with gui.yml,
     * copy those settings over so customised packages aren't lost, then remove them from config.yml.
     */
    private void migrateFromConfig() {
        YamlConfiguration cfg = plugin.cfg().yaml();
        ConfigurationSection old = cfg.getConfigurationSection("shop");
        if (old == null || !old.isConfigurationSection("items")) return;
        YamlConfiguration gui = file.yaml();

        gui.set("title", old.getString("title", gui.getString("title")));
        gui.set("rows", old.getInt("rows", gui.getInt("rows")));
        String filler = old.getString("filler", "GRAY_STAINED_GLASS_PANE");
        if (filler.equalsIgnoreCase("none")) gui.set("filler.enabled", false);
        else gui.set("filler.material", filler);
        int infoSlot = old.getInt("info-slot", 4);
        if (infoSlot < 0) gui.set("info.enabled", false);
        else gui.set("info.slot", infoSlot);
        gui.set("info.material", old.getString("info-material", "FEATHER"));
        gui.set("info.texture", "");

        gui.set("items", null);
        ConfigurationSection items = old.getConfigurationSection("items");
        for (String id : items.getKeys(false)) {
            ConfigurationSection it = items.getConfigurationSection(id);
            if (it == null) continue;
            for (String key : it.getKeys(false)) gui.set("items." + id + "." + key, it.get(key));
        }
        // The new close button and decorations might sit on the old packages' slots.
        gui.set("close.enabled", false);
        gui.set("decorations", null);
        gui.createSection("decorations");
        file.save();

        for (String key : List.of("title", "rows", "filler", "info-slot", "info-material", "items")) {
            cfg.set("shop." + key, null);
        }
        plugin.cfg().save();
        plugin.getLogger().info("Moved the fly shop from config.yml to gui.yml.");
    }

    private record Package(String id, int slot, GuiItem icon, long seconds, double price, String permission) {}

    private record Decoration(int slot, GuiItem icon) {}
}
