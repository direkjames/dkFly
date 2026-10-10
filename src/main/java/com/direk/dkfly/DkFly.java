package com.direk.dkfly;

import com.direk.dkcore.DkPlugin;
import com.direk.dkfly.command.FlyCommand;
import com.direk.dkfly.command.FlyTimeCommand;
import com.direk.dkfly.data.FlyData;
import com.direk.dkfly.data.FlyDataManager;
import com.direk.dkfly.data.FlyRepository;
import com.direk.dkfly.fly.FlightManager;
import com.direk.dkfly.hook.FlyPlaceholders;
import com.direk.dkfly.hook.WorldGuardHook;
import com.direk.dkfly.listener.CombatListener;
import com.direk.dkfly.listener.PlayerListener;
import com.direk.dkfly.shop.FlyShop;
import com.direk.dkfly.transfer.TransferManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * dkFly - timed flight for our server.
 * Author: direk james
 */
public final class DkFly extends DkPlugin {

    private Settings settings;
    private FlyRepository repo;
    private FlyDataManager data;
    private FlightManager flight;
    private FlyShop shop;
    private TransferManager transfers;
    private Object placeholders; // FlyPlaceholders, kept as Object so PlaceholderAPI stays optional

    @Override
    public void onLoad() {
        // WorldGuard flags must be registered before WorldGuard enables.
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardHook.registerFlag(getLogger());
            } catch (Throwable t) {
                getLogger().warning("Could not register the WorldGuard flag: " + t.getMessage());
            }
        }
    }

    @Override
    protected void enable() {
        long startedAt = System.currentTimeMillis();
        removeOldSettings();
        settings = new Settings(cfg().yaml(), getLogger());

        repo = new FlyRepository(database());
        repo.createTables().join();
        data = new FlyDataManager(this, repo);
        flight = new FlightManager(this);
        shop = new FlyShop(this);
        transfers = new TransferManager(this);

        registerCommand(new FlyCommand(this));
        registerCommand(new FlyTimeCommand(this));
        listen(new PlayerListener(this), new CombatListener(this));

        // Wait one tick so every soft dependency has finished enabling.
        long enabledIn = System.currentTimeMillis() - startedAt;
        tasks().later(() -> {
            flight.detectHooks();
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                placeholders = FlyPlaceholders.hook(this);
            }
            new FlyBanner(this).print(placeholders != null, enabledIn);
        }, 1L);

        tasks().timer(flight::tick, 20L, 20L);
        long autosave = settings.autosaveMinutes * 60L * 20L;
        tasks().timer(data::saveDirty, autosave, autosave);

        // Players already online (plugin reloaded while the server was running).
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean flyingNow = p.isFlying();
            repo.load(p.getUniqueId()).thenAccept(loaded -> tasks().sync(() -> {
                if (!p.isOnline()) return;
                loaded.wasFlying(loaded.enabled() && flyingNow);
                data.adopt(p, loaded);
                flight.restoreOnJoin(p, loaded);
            }));
        }
    }

    @Override
    protected void disable() {
        if (placeholders != null) FlyPlaceholders.unhook(placeholders);
        if (flight != null) flight.display().hideAll(Bukkit.getOnlinePlayers());
        if (data == null) return;
        List<FlyData.Snapshot> all = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            FlyData d = data.getIfOnline(p.getUniqueId());
            if (d == null) continue;
            d.wasFlying(d.enabled() && p.isFlying());
            all.add(d.snapshot());
        }
        data.saveAllBlocking(all);
    }

    /** Settings from dkFly 1.0.0 that were replaced in 1.1.0. */
    private void removeOldSettings() {
        boolean changed = false;
        for (String key : List.of("landing.mode", "actionbar-while-flying", "combat.disable-fly", "bentobox")) {
            if (cfg().yaml().contains(key, true)) {
                cfg().yaml().set(key, null);
                changed = true;
            }
        }
        if (changed) {
            cfg().save();
            getLogger().info("Removed outdated dkFly 1.0.0 settings from config.yml.");
        }
    }

    @Override
    public void reloadAll() {
        super.reloadAll();
        settings = new Settings(cfg().yaml(), getLogger());
        shop.reload();
        flight.detectHooks();
        flight.onReload();
    }

    public Settings settings() { return settings; }
    public FlyDataManager data() { return data; }
    public FlightManager flight() { return flight; }
    public FlyShop shop() { return shop; }
    public TransferManager transfers() { return transfers; }
}
