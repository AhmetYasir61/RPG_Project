package net.aethel.core.modules.placeholder;

import net.aethel.core.api.PlaceholderResolver;
import net.aethel.core.api.PlaceholderService;
import net.aethel.core.api.ProfileService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kendi placeholder motorumuz: %aethel_<modul>_<anahtar>%. PlaceholderAPI kuruluysa
 * kopru kurulur, kurulu degilse sistem aynen calisir; PAPI hicbir zaman zorunlu degildir.
 */
@ModuleInfo(id = "placeholder", name = "Placeholder")
public final class PlaceholderModule implements Module, PlaceholderService {

    /** %aethel_rpg_level% -> prefix="rpg", key="level" */
    private static final Pattern PATTERN =
            Pattern.compile("%aethel_([a-z0-9]+)_([a-z0-9_]+)%", Pattern.CASE_INSENSITIVE);

    private final Map<String, PlaceholderResolver> resolvers = new ConcurrentHashMap<>();
    private CoreContext ctx;
    private PapiBridge bridge;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.services().register(PlaceholderService.class, this, "placeholder");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        registerCoreResolvers();
        if (ctx.plugin().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.bridge = new PapiBridge(ctx, this);
            bridge.hook();
        }
    }

    @Override
    public void onDisable(CoreContext ctx) {
        if (bridge != null) bridge.unhook();
        resolvers.clear();
    }

    /** Cekirdegin kendi placeholder'lari; modul kapali olsa da bunlar calisir. */
    private void registerCoreResolvers() {
        register("player", (player, key) -> {
            if (player == null) return null;
            return switch (key) {
                case "name" -> player.getName();
                case "world" -> player.getWorld().getName();
                case "health" -> String.valueOf((int) player.getHealth());
                case "x" -> String.valueOf(player.getLocation().getBlockX());
                case "y" -> String.valueOf(player.getLocation().getBlockY());
                case "z" -> String.valueOf(player.getLocation().getBlockZ());
                default -> null;
            };
        });
        register("profile", (player, key) -> {
            if (player == null) return null;
            return ctx.services().optional(ProfileService.class)
                    .flatMap(profiles -> profiles.cached(player.getUniqueId()))
                    .map(profile -> switch (key) {
                        case "playtime" -> String.valueOf(profile.playtimeSeconds() / 3600);
                        case "first_join" -> String.valueOf(profile.firstJoin());
                        default -> profile.attribute(key.replace('_', ':')).orElse(null);
                    })
                    .orElse(null);
        });
        register("server", (player, key) -> switch (key) {
            case "online" -> String.valueOf(ctx.plugin().getServer().getOnlinePlayers().size());
            case "max" -> String.valueOf(ctx.plugin().getServer().getMaxPlayers());
            case "tps" -> String.format("%.1f", ctx.plugin().getServer().getTPS()[0]);
            default -> null;
        });
    }

    @Override
    public void register(String prefix, PlaceholderResolver resolver) {
        resolvers.put(prefix.toLowerCase(java.util.Locale.ROOT), resolver);
    }

    @Override
    public void unregister(String prefix) {
        resolvers.remove(prefix.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Metindeki tum placeholder'lari cozer. Cozulemeyen placeholder OLDUGU GIBI birakilir:
     * bos string yazmak, eksik veriyi gizleyip hatanin fark edilmesini geciktirir.
     */
    @Override
    public String apply(Player player, String text) {
        if (text == null || text.indexOf('%') < 0) return text;

        Matcher matcher = PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = value(player, matcher.group(1), matcher.group(2), matcher.group());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        String output = result.toString();
        return bridge == null ? output : bridge.apply(player, output);
    }

    @Override
    public String value(Player player, String prefix, String key, String fallback) {
        PlaceholderResolver resolver = resolvers.get(prefix.toLowerCase(java.util.Locale.ROOT));
        if (resolver == null) return fallback;
        String value = resolver.resolve(player, key);
        return value == null ? fallback : value;
    }

    /** PAPI koprusunun bizim cozuculere erisimi icin. */
    java.util.Set<String> prefixes() {
        return resolvers.keySet();
    }
}
