package net.aethel.core.modules.npc;

import net.aethel.core.api.DialogService;
import net.aethel.core.api.NpcService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.module.ModuleUnavailableException;
import net.aethel.core.nms.VersionAdapter;
import net.aethel.core.nms.VersionAdapters;
import net.aethel.core.packet.PacketBridge;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paket tabanli NPC modulu. NPC'ler sunucuda entity olusturmaz; gorunurluk mesafeye
 * gore yonetilir ve tiklama aksiyonlari diyalog sistemine baglanir.
 */
@ModuleInfo(id = "npc", name = "NPC", softDepends = {"dialog", "menu"})
public final class NpcModule implements Module, NpcService {

    /** NPC'lerin gorunur oldugu mesafe; disinda paket gonderilmez. */
    private static final double VIEW_DISTANCE_SQUARED = 48 * 48;

    private final Map<String, Npc> definitions = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, PacketNpc>> rendered = new ConcurrentHashMap<>();
    private final PacketBridge bridge;
    private ConfigFile config;
    private VersionAdapter nms;
    private CoreContext ctx;

    public NpcModule(PacketBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.config = ctx.config().open("npcs.yml", 1, null, ConfigMigration.NONE);
        if (!bridge.isAvailable()) {
            throw new ModuleUnavailableException("paket katmani kullanilamiyor");
        }
        this.nms = VersionAdapters.detect(ctx.logger()).orElseThrow(() ->
                new ModuleUnavailableException("bu surum icin NMS adapteri yok"));
        ctx.services().register(NpcService.class, this, "npc");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        load();
        ctx.listener(new NpcListener(this, ctx, bridge));
        // Gorunurluk saniyede bir taranir; NPC konumlari sabittir, daha sik gerekmez.
        ctx.scheduler().repeating("npc", 20L, 20L, this::refreshAll);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        rendered.forEach((id, viewers) -> viewers.forEach((uuid, npc) -> {
            Player player = ctx.plugin().getServer().getPlayer(uuid);
            if (player != null) npc.hide(player);
        }));
        rendered.clear();
        definitions.clear();
    }

    private void load() {
        definitions.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("npcs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            Location location = section == null ? null : section.getLocation("location");
            if (location == null) continue;
            definitions.put(id, new Npc(id,
                    section.getString("display", id), location,
                    section.getString("skin"),
                    section.getString("dialog"),
                    section.getStringList("actions"),
                    section.getBoolean("look-at-player", true)));
        }
        ctx.logger().info("NPC: " + definitions.size());
    }

    @Override
    public Optional<Npc> npc(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public Collection<Npc> all() {
        return List.copyOf(definitions.values());
    }

    @Override
    public Npc create(String id, Location location, String displayName) {
        Npc npc = new Npc(id, displayName, location, null, null, new ArrayList<>(), true);
        definitions.put(id, npc);
        String base = "npcs." + id;
        config.yaml().set(base + ".location", location);
        config.yaml().set(base + ".display", displayName);
        config.save();
        return npc;
    }

    @Override
    public void remove(String id) {
        definitions.remove(id);
        Map<UUID, PacketNpc> viewers = rendered.remove(id);
        if (viewers != null) viewers.forEach((uuid, npc) -> {
            Player player = ctx.plugin().getServer().getPlayer(uuid);
            if (player != null) npc.hide(player);
        });
        config.yaml().set("npcs." + id, null);
        config.save();
    }

    @Override
    public void show(Player player, String id) {
        Npc definition = definitions.get(id);
        if (definition == null) return;
        Map<UUID, PacketNpc> viewers = rendered.computeIfAbsent(id, key -> new ConcurrentHashMap<>());
        if (viewers.containsKey(player.getUniqueId())) return;

        PacketNpc npc = new PacketNpc(bridge, nms.nextEntityId(), definition.displayName(),
                definition.location(), null, null);
        npc.show(player);
        viewers.put(player.getUniqueId(), npc);
    }

    @Override
    public void hide(Player player, String id) {
        Map<UUID, PacketNpc> viewers = rendered.get(id);
        if (viewers == null) return;
        PacketNpc npc = viewers.remove(player.getUniqueId());
        if (npc != null) npc.hide(player);
    }

    @Override
    public void refresh(Player player) {
        for (Npc definition : definitions.values()) {
            boolean near = definition.location().getWorld().equals(player.getWorld())
                    && player.getLocation().distanceSquared(definition.location()) <= VIEW_DISTANCE_SQUARED;
            if (near) show(player, definition.id()); else hide(player, definition.id());
        }
    }

    private void refreshAll() {
        ctx.plugin().getServer().getOnlinePlayers().forEach(this::refresh);
    }

    /** Bir entity id'sinin hangi NPC'ye ait oldugunu bulur (tiklama olayinda kullanilir). */
    Optional<Npc> byEntityId(Player viewer, int entityId) {
        for (Map.Entry<String, Map<UUID, PacketNpc>> entry : rendered.entrySet()) {
            PacketNpc npc = entry.getValue().get(viewer.getUniqueId());
            if (npc != null && npc.entityId() == entityId) {
                return npc(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** NPC'ye tiklandiginda diyalog baslatir ve aksiyonlari calistirir. */
    void handleClick(Player player, Npc npc) {
        if (npc.dialogId() != null) {
            ctx.services().optional(DialogService.class)
                    .ifPresent(dialogs -> dialogs.start(player, npc.dialogId()));
        }
        npc.clickActions().forEach(action -> runAction(player, action));
    }

    private void runAction(Player player, String action) {
        int end = action.indexOf(']');
        if (!action.startsWith("[") || end < 0) return;
        String type = action.substring(1, end).toLowerCase(java.util.Locale.ROOT);
        String value = action.substring(end + 1).trim();

        switch (type) {
            case "command" -> player.performCommand(value);
            case "menu" -> ctx.services().optional(net.aethel.core.api.MenuService.class)
                    .ifPresent(menus -> menus.openNamed(player, value));
            case "message" -> player.sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value));
            default -> ctx.logger().warning("Bilinmeyen NPC eylemi: " + type);
        }
    }
}
