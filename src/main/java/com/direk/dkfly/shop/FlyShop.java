package com.direk.dkfly.shop;

import com.direk.dkcore.economy.EconomyService;
import com.direk.dkcore.gui.ItemBuilder;
import com.direk.dkcore.gui.Menu;
import com.direk.dkcore.message.Messages;
import com.direk.dkcore.util.TimeUtil;
import com.direk.dkfly.DkFly;
import com.direk.dkfly.data.FlyData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GUI fly shop built from config.yml "shop".
 * Author: direk james
 */
public final class FlyShop {

    private final DkFly plugin;
    private final List<ShopItem> items = new ArrayList<>();
    private boolean enabled;
    private String title;
    private int rows;
    private String filler;
    private int infoSlot;
    private String infoMaterial;

    public FlyShop(DkFly plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        YamlConfiguration c = plugin.cfg().yaml();
        enabled = c.getBoolean("shop.enabled", true);
        title = c.getString("shop.title", "<dark_gray>Fly Shop");
        rows = Math.max(1, Math.min(6, c.getInt("shop.rows", 3)));
        filler = c.getString("shop.filler", "GRAY_STAINED_GLASS_PANE");
        infoSlot = c.getInt("shop.info-slot", 4);
        infoMaterial = c.getString("shop.info-material", "FEATHER");

        items.clear();
        ConfigurationSection sec = c.getConfigurationSection("shop.items");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection it = sec.getConfigurationSection(id);
            if (it == null) continue;
            long seconds = TimeUtil.parseSeconds(it.getString("time", ""));
            double price = it.getDouble("price", -1);
            int slot = it.getInt("slot", -1);
            if (seconds <= 0 || price < 0 || slot < 0 || slot >= rows * 9) {
                plugin.getLogger().warning("Shop item '" + id + "' has an invalid time, price or slot; skipped.");
                continue;
            }
            items.add(new ShopItem(id, slot, it.getString("material", "FEATHER"), it.getString("name", id),
                    it.getStringList("lore"), seconds, price, it.getBoolean("glow", false),
                    it.getString("permission", "")));
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int packageCount() {
        return items.size();
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

        Menu menu = new Menu(rows, m.parse(title));
        String balance = economy.format(economy.balance(p));
        String timeText = plugin.flight().isUnlimited(p)
                ? plugin.settings().placeholderUnlimited
                : plugin.settings().time.format(d.seconds());

        if (infoSlot >= 0 && infoSlot < rows * 9) {
            TagResolver r = TagResolver.resolver(Messages.text("time", timeText), Messages.text("balance", balance));
            menu.set(infoSlot, ItemBuilder.of(infoMaterial, Material.FEATHER)
                    .name(m.get("shop-info-name", r))
                    .lore(lines(m.raw("shop-info-lore"), r))
                    .build());
        }

        for (ShopItem item : items) {
            TagResolver r = TagResolver.resolver(
                    Messages.text("time", plugin.settings().time.format(item.seconds())),
                    Messages.text("price", economy.format(item.price())),
                    Messages.text("balance", balance));
            List<Component> lore = new ArrayList<>();
            for (String line : item.lore()) lore.add(m.parse(line, r));
            menu.set(item.slot(), ItemBuilder.of(item.material(), Material.FEATHER)
                    .name(m.parse(item.name(), r))
                    .lore(lore)
                    .glow(item.glow())
                    .build(), click -> buy(p, item));
        }

        if (filler != null && !filler.equalsIgnoreCase("none")) {
            menu.fill(ItemBuilder.of(filler, Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build());
        }
        menu.open(p);
    }

    private void buy(Player p, ShopItem item) {
        Messages m = plugin.messages();
        Optional<EconomyService> eco = plugin.economy().get();
        if (eco.isEmpty()) {
            m.send(p, "shop-no-economy");
            p.closeInventory();
            return;
        }
        EconomyService economy = eco.get();
        if (!item.permission().isEmpty() && !p.hasPermission(item.permission())) {
            m.send(p, "shop-no-permission");
            return;
        }
        FlyData d = plugin.data().get(p);
        long max = plugin.settings().maxTime;
        if (max > 0 && d.seconds() + item.seconds() > max) {
            m.send(p, "shop-full", Messages.text("max", plugin.settings().time.format(max)));
            return;
        }
        if (!economy.has(p, item.price())) {
            m.send(p, "shop-not-enough-money",
                    Messages.text("price", economy.format(item.price())),
                    Messages.text("balance", economy.format(economy.balance(p))));
            return;
        }
        if (!economy.withdraw(p, item.price())) {
            m.send(p, "shop-not-enough-money",
                    Messages.text("price", economy.format(item.price())),
                    Messages.text("balance", economy.format(economy.balance(p))));
            return;
        }
        d.seconds(d.seconds() + item.seconds());
        m.send(p, "shop-bought",
                Messages.text("time", plugin.settings().time.format(item.seconds())),
                Messages.text("price", economy.format(item.price())));
        // Refresh balance and time. Opening a new menu inside a click event must wait a tick.
        plugin.tasks().later(() -> { if (p.isOnline()) open(p); }, 1L);
    }

    private List<Component> lines(String joined, TagResolver r) {
        List<Component> out = new ArrayList<>();
        for (String line : joined.split("<newline>", -1)) out.add(plugin.messages().parse(line, r));
        return out;
    }

    public record ShopItem(String id, int slot, String material, String name, List<String> lore,
                           long seconds, double price, boolean glow, String permission) {}
}
