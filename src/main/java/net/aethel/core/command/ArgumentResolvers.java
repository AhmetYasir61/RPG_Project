package net.aethel.core.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Java parametre tipi -> Brigadier arguman tipi eslesmesi. Tab-complete, dogrulama
 * ve tur donusumu tek yerde durur; komut yazan modul bunlarla hic ugrasmaz.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ArgumentResolvers {

    /** Bir Java tipi icin arguman tipi ve context'ten okuma yontemi. */
    public record Resolver(ArgumentType<?> type,
                           Extractor extractor) {}

    @FunctionalInterface
    public interface Extractor {
        Object extract(CommandContext<CommandSourceStack> ctx, String name);
    }

    private static final Map<Class<?>, Resolver> RESOLVERS = Map.of(
            // word() DEGIL: word() iki nokta kabul etmez ve namespace'li
            // kimlikler ("aethel:alev_dalgasi") hic yazilamaz.
            String.class, new Resolver(IdentifierArgumentType.identifier(),
                    (ctx, name) -> StringArgumentType.getString(ctx, name)),
            int.class, new Resolver(IntegerArgumentType.integer(),
                    (ctx, name) -> IntegerArgumentType.getInteger(ctx, name)),
            Integer.class, new Resolver(IntegerArgumentType.integer(),
                    (ctx, name) -> IntegerArgumentType.getInteger(ctx, name)),
            long.class, new Resolver(LongArgumentType.longArg(),
                    (ctx, name) -> LongArgumentType.getLong(ctx, name)),
            double.class, new Resolver(DoubleArgumentType.doubleArg(),
                    (ctx, name) -> DoubleArgumentType.getDouble(ctx, name)),
            boolean.class, new Resolver(BoolArgumentType.bool(),
                    (ctx, name) -> BoolArgumentType.getBool(ctx, name)),
            Boolean.class, new Resolver(BoolArgumentType.bool(),
                    (ctx, name) -> BoolArgumentType.getBool(ctx, name)),
            Player.class, new Resolver(ArgumentTypes.player(), ArgumentResolvers::singlePlayer)
    );

    private ArgumentResolvers() {}

    public static Resolver forType(Class<?> type, boolean greedy) {
        if (type == String.class && greedy) {
            return new Resolver(StringArgumentType.greedyString(),
                    (ctx, name) -> StringArgumentType.getString(ctx, name));
        }
        Resolver resolver = RESOLVERS.get(type);
        if (resolver == null) {
            throw new IllegalArgumentException("Komut argumani icin desteklenmeyen tip: " + type.getName());
        }
        return resolver;
    }

    public static boolean supports(Class<?> type) {
        return type == String.class || RESOLVERS.containsKey(type);
    }

    /**
     * Paper'in secici cozumleyicisi liste dondurur (@a gibi seciciler icin). Tek oyuncu
     * bekleyen komutlarda ilk sonucu aliriz; hic eslesme yoksa dil anahtari firlatilir.
     */
    private static Object singlePlayer(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            PlayerSelectorArgumentResolver resolver =
                    ctx.getArgument(name, PlayerSelectorArgumentResolver.class);
            var players = resolver.resolve(ctx.getSource());
            if (players.isEmpty()) throw new CommandException("command.player-not-found");
            return players.get(0);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new CommandException("command.player-not-found");
        }
    }
}
