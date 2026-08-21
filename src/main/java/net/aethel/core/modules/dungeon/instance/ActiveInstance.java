package net.aethel.core.modules.dungeon.instance;

import net.aethel.core.api.DungeonService;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Calisan bir dungeon ornegi. Dunya, oyuncu listesi, cekirdek konumu ve cokme
 * durumunu tasir; DungeonModule bunlarin uzerinden yasam dongusunu yurutur.
 */
public final class ActiveInstance {

    private final UUID id = UUID.randomUUID();
    private final String definitionId;
    private final World world;
    private final long createdAt = System.currentTimeMillis();
    private final List<UUID> players = new CopyOnWriteArrayList<>();

    private DungeonService.State state = DungeonService.State.GENERATING;
    private Location entrance;
    private Location core;
    private Location exit;
    private long collapseEndsAt;
    private UUID coreBreaker;

    public ActiveInstance(String definitionId, World world) {
        this.definitionId = definitionId;
        this.world = world;
    }

    public UUID id() { return id; }
    public String definitionId() { return definitionId; }
    public World world() { return world; }
    public long createdAt() { return createdAt; }
    public DungeonService.State state() { return state; }
    public Location entrance() { return entrance; }
    public Location core() { return core; }
    public Location exit() { return exit; }
    public UUID coreBreaker() { return coreBreaker; }

    public void state(DungeonService.State state) { this.state = state; }
    public void entrance(Location entrance) { this.entrance = entrance; }
    public void core(Location core) { this.core = core; }
    public void exit(Location exit) { this.exit = exit; }

    public List<UUID> players() {
        return new ArrayList<>(players);
    }

    public void addPlayer(UUID player) {
        if (!players.contains(player)) players.add(player);
    }

    public void removePlayer(UUID player) {
        players.remove(player);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    /** Cokme geri sayimini baslatir. */
    public void beginCollapse(UUID breaker, int seconds) {
        this.state = DungeonService.State.COLLAPSING;
        this.coreBreaker = breaker;
        this.collapseEndsAt = System.currentTimeMillis() + seconds * 1000L;
    }

    public boolean collapsing() {
        return state == DungeonService.State.COLLAPSING;
    }

    /** Cokmeye kalan sure (saniye); geri sayim baslamadiysa 0. */
    public long collapseSecondsLeft() {
        if (!collapsing()) return 0;
        return Math.max(0, (collapseEndsAt - System.currentTimeMillis()) / 1000);
    }

    public boolean collapseFinished() {
        return collapsing() && System.currentTimeMillis() >= collapseEndsAt;
    }

    /** Servis kaydina cevrilmis hali. */
    public DungeonService.Instance snapshot() {
        return new DungeonService.Instance(id, definitionId, world.getName(),
                state, createdAt, players());
    }
}
