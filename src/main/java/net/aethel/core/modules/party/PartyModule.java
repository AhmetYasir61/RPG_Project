package net.aethel.core.modules.party;

import net.aethel.core.api.PartyService;
import net.aethel.core.api.WaypointService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parti modulu. Uyeler birbirini saydam kafa waypoint'i ile takip eder; parti hicbir
 * zaman isinlanma saglamaz, yalnizca nerede olduklarini gosterir.
 */
@ModuleInfo(id = "party", name = "Parti", depends = {"profile"}, softDepends = {"waypoint"})
public final class PartyModule implements Module, PartyService {

    /** Davetin gecerlilik suresi; unutulmus davetler birikmesin. */
    private static final long INVITE_TIMEOUT_MILLIS = 60_000L;

    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> memberIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Set<UUID> tracking = ConcurrentHashMap.newKeySet();
    private CoreContext ctx;

    /** Bekleyen davet. */
    private record Invite(UUID partyId, UUID inviter, long expiresAt) {}

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.services().register(PartyService.class, this, "party");
        ctx.commands().register("party", new PartyCommand(ctx, this));

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("party.tracking", true, "Uye takibi (saydam kafa waypoint isareti)");
        features.declare("party.shared-xp", true, "Ortak XP paylasimi");
        features.declare("party.shared-loot", true, "Ortak loot");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.listener(new PartyListener(this));
        // Suresi dolan davetler temizlenir.
        ctx.scheduler().repeating("party", 20L * 30, 20L * 30, () -> {
            long now = System.currentTimeMillis();
            invites.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        });
    }

    @Override
    public void onDisable(CoreContext ctx) {
        parties.clear();
        memberIndex.clear();
        invites.clear();
        tracking.clear();
    }

    @Override
    public Optional<Party> partyOf(UUID player) {
        UUID partyId = memberIndex.get(player);
        return partyId == null ? Optional.empty() : Optional.ofNullable(parties.get(partyId));
    }

    @Override
    public Party create(Player leader) {
        UUID id = UUID.randomUUID();
        Party party = new Party(id, leader.getUniqueId(),
                new ArrayList<>(List.of(leader.getUniqueId())), true, 1.0);
        parties.put(id, party);
        memberIndex.put(leader.getUniqueId(), id);
        return party;
    }

    @Override
    public boolean invite(Player inviter, Player target) {
        Party party = partyOf(inviter.getUniqueId()).orElseGet(() -> create(inviter));
        if (!party.leader().equals(inviter.getUniqueId())) return false;
        if (memberIndex.containsKey(target.getUniqueId())) return false;

        invites.put(target.getUniqueId(), new Invite(party.id(), inviter.getUniqueId(),
                System.currentTimeMillis() + INVITE_TIMEOUT_MILLIS));
        ctx.lang().send(target, "party.invited", LangService.of("player", inviter.getName()));
        return true;
    }

    @Override
    public boolean accept(Player player, UUID partyId) {
        Invite invite = invites.remove(player.getUniqueId());
        if (invite == null || invite.expiresAt() < System.currentTimeMillis()) return false;

        Party party = parties.get(invite.partyId());
        if (party == null) return false;
        party.members().add(player.getUniqueId());
        memberIndex.put(player.getUniqueId(), party.id());
        broadcast(party, "party.joined", LangService.of("player", player.getName()));
        trackMembers(player, true);
        return true;
    }

    @Override
    public void leave(Player player) {
        partyOf(player.getUniqueId()).ifPresent(party -> {
            party.members().remove(player.getUniqueId());
            memberIndex.remove(player.getUniqueId());
            trackMembers(player, false);
            broadcast(party, "party.left", LangService.of("player", player.getName()));

            // Lider ayrilirsa parti dagilmaz; liderlik siradaki uyeye gecer.
            if (party.leader().equals(player.getUniqueId())) {
                if (party.members().isEmpty()) {
                    disband(party.id());
                } else {
                    Party promoted = new Party(party.id(), party.members().get(0),
                            party.members(), party.sharedLoot(), party.xpShare());
                    parties.put(party.id(), promoted);
                    broadcast(promoted, "party.new-leader",
                            LangService.of("player", nameOf(promoted.leader())));
                }
            }
        });
    }

    @Override
    public void disband(UUID partyId) {
        Party party = parties.remove(partyId);
        if (party == null) return;
        party.members().forEach(member -> {
            memberIndex.remove(member);
            Player player = ctx.plugin().getServer().getPlayer(member);
            if (player != null) trackMembers(player, false);
        });
    }

    /**
     * Takip acildiginda her uye icin bir waypoint isareti olusturulur. Waypoint modulu
     * kapaliysa parti calismaya devam eder, yalnizca gorsel takip olmaz.
     */
    @Override
    public void trackMembers(Player player, boolean enabled) {
        // Ozellik kapaliysa takip hic kurulmaz: oyuncu boyle bir mekanigin
        // varligindan haberdar bile olmaz.
        if (!ctx.feature("party.tracking")) return;
        var waypoints = ctx.services().optional(WaypointService.class);
        if (waypoints.isEmpty()) return;

        if (!enabled) {
            tracking.remove(player.getUniqueId());
            waypoints.get().clear(player);
            return;
        }
        tracking.add(player.getUniqueId());
        waypoints.get().clear(player);
        partyOf(player.getUniqueId()).ifPresent(party -> party.members().stream()
                .filter(member -> !member.equals(player.getUniqueId()))
                .map(member -> ctx.plugin().getServer().getPlayer(member))
                .filter(java.util.Objects::nonNull)
                .forEach(member -> waypoints.get().trackPlayer(player, member)));
    }

    @Override
    public boolean isTracking(UUID player) {
        return tracking.contains(player);
    }

    @Override
    public List<Party> parties() {
        return List.copyOf(parties.values());
    }

    void broadcast(Party party, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        party.members().forEach(member -> {
            Player player = ctx.plugin().getServer().getPlayer(member);
            if (player != null) ctx.lang().send(player, key, args);
        });
    }

    private String nameOf(UUID uuid) {
        Player player = ctx.plugin().getServer().getPlayer(uuid);
        return player == null ? uuid.toString().substring(0, 8) : player.getName();
    }

    CoreContext context() {
        return ctx;
    }
}
