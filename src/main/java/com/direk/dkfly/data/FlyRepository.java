package com.direk.dkfly.data;

import com.direk.dkcore.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongUnaryOperator;

/**
 * dkfly_players table. All calls run on dkCore's single database thread,
 * so they always happen in the order they were made.
 * Author: direk james
 */
public final class FlyRepository {

    private static final String TABLE = "dkfly_players";

    private final Database db;
    private final String upsert;

    public FlyRepository(Database db) {
        this.db = db;
        this.upsert = db.upsert(TABLE, "uuid", "uuid", "seconds", "enabled", "was_flying", "updated");
    }

    public CompletableFuture<Void> createTables() {
        return db.createTables(List.of(
                "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "seconds BIGINT NOT NULL DEFAULT 0, "
                        + "enabled BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "was_flying BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "updated BIGINT NOT NULL DEFAULT 0)"
        ));
    }

    public CompletableFuture<FlyData> load(UUID uuid) {
        return db.query(c -> read(c, uuid));
    }

    public CompletableFuture<Void> save(FlyData.Snapshot s) {
        return db.execute(c -> write(c, s));
    }

    public CompletableFuture<Void> saveAll(Collection<FlyData.Snapshot> all) {
        if (all.isEmpty()) return CompletableFuture.completedFuture(null);
        return db.execute(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (FlyData.Snapshot s : all) write(c, s);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        });
    }

    /**
     * Changes an OFFLINE player's seconds in one step on the database thread.
     * @return the new value
     */
    public CompletableFuture<Long> modifyOffline(UUID uuid, LongUnaryOperator change) {
        return db.query(c -> {
            FlyData d = read(c, uuid);
            long updated = Math.max(0, change.applyAsLong(d.seconds()));
            write(c, new FlyData.Snapshot(uuid, updated, d.enabled(), d.wasFlying()));
            return updated;
        });
    }

    private FlyData read(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT seconds, enabled, was_flying FROM " + TABLE + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new FlyData(uuid, rs.getLong(1), rs.getBoolean(2), rs.getBoolean(3));
                }
            }
        }
        return FlyData.empty(uuid);
    }

    private void write(Connection c, FlyData.Snapshot s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(upsert)) {
            ps.setString(1, s.uuid().toString());
            ps.setLong(2, s.seconds());
            ps.setBoolean(3, s.enabled());
            ps.setBoolean(4, s.wasFlying());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
