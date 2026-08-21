package net.aethel.core.modules.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.aethel.core.api.PermissionService;
import net.aethel.core.api.PlaceholderService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Locale;

/**
 * Sohbet modulu: format, prefix/suffix, mention ve filtre. Adventure'in modern
 * chat olayi kullanilir; eski AsyncPlayerChatEvent kullanilmaz.
 */
@ModuleInfo(id = "chat", name = "Sohbet", softDepends = {"permissions", "placeholder"})
public final class ChatModule implements Module, Listener {

    private final MiniMessage mini = MiniMessage.miniMessage();
    private ConfigFile config;
    private CoreContext ctx;
    private String format = "<prefix><white><player></white><suffix> <dark_gray>»</dark_gray> <gray><message></gray>";
    private List<String> blocked = List.of();

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.config = ctx.config().open("chat.yml", 1, null, ConfigMigration.NONE);
    }

    @Override
    public void onEnable(CoreContext ctx) {
        loadSettings();
        ctx.listener(this);
    }

    @Override
    public void onReload(CoreContext ctx) {
        loadSettings();
    }

    private void loadSettings() {
        if (!config.yaml().contains("format")) {
            config.yaml().set("format", format);
            config.yaml().set("blocked-words", List.of());
            config.yaml().set("mention-sound", "entity.experience_orb.pickup");
            config.save();
        }
        this.format = config.yaml().getString("format", format);
        this.blocked = config.yaml().getStringList("blocked-words");
    }

    /**
     * Chat olayi asenkron gelir. Mesaji burada isliyoruz ama Bukkit API'sine
     * dokunmuyoruz; yalnizca metin donusumu ve alici listesi degistiriliyor.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (isBlocked(raw)) {
            event.setCancelled(true);
            ctx.lang().send(player, "chat.blocked");
            return;
        }
        String prefix = permissionValue(player, true);
        String suffix = permissionValue(player, false);

        // Oyuncu metni MiniMessage olarak AYRISTIRILMAZ: aksi halde herkes renk ve
        // tiklanabilir baglanti enjekte edebilirdi. Yalnizca duz metin olarak gomulur.
        String message = raw.replace("<", "‹").replace(">", "›");

        String rendered = format
                .replace("<prefix>", prefix)
                .replace("<suffix>", suffix)
                .replace("<player>", player.getName())
                .replace("<message>", message);

        String withPlaceholders = ctx.services().optional(PlaceholderService.class)
                .map(service -> service.apply(player, rendered)).orElse(rendered);

        Component component = mini.deserialize(withPlaceholders);
        event.renderer((source, sourceDisplayName, sourceMessage, viewer) -> component);
        notifyMentions(player, raw);
    }

    /** Adi gecen oyunculara ses calinir; MMORPG'de dikkat cekmenin ucuz yolu. */
    private void notifyMentions(Player sender, String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        String soundName = config.yaml().getString("mention-sound", "entity.experience_orb.pickup");

        for (Player online : ctx.plugin().getServer().getOnlinePlayers()) {
            if (online.equals(sender)) continue;
            if (!lower.contains(online.getName().toLowerCase(Locale.ROOT))) continue;
            net.aethel.core.util.Sounds.parse(soundName).ifPresent(sound ->
                    online.playSound(online.getLocation(), sound, 1f, 1.4f));
        }
    }

    private boolean isBlocked(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return blocked.stream().anyMatch(word -> lower.contains(word.toLowerCase(Locale.ROOT)));
    }

    private String permissionValue(Player player, boolean prefix) {
        return ctx.services().optional(PermissionService.class)
                .map(permissions -> prefix
                        ? permissions.prefix(player.getUniqueId())
                        : permissions.suffix(player.getUniqueId()))
                .orElse("");
    }
}
