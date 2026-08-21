package net.aethel.core.command;

import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tek bir komut metodunun calistiricisi: argumanlari context'ten cozer, playerOnly
 * kontrolunu yapar ve CommandException'i kullaniciya dil anahtari olarak dondurur.
 */
@SuppressWarnings("UnstableApiUsage")
final class CommandInvoker {

    /** Bir arguman adinin, context'ten okunma yontemi ve hedef Java tipi. */
    record Binding(String name, ArgumentResolvers.Extractor extractor, Class<?> type) {}

    private final Object handler;
    private final MethodHandle handle;
    private final List<Binding> bindings;
    private final boolean playerOnly;
    private final LangService lang;
    private final Logger log;

    CommandInvoker(Object handler, Method method, List<Binding> bindings,
                   boolean playerOnly, LangService lang, Logger log) {
        this.handler = handler;
        this.bindings = bindings;
        this.playerOnly = playerOnly;
        this.lang = lang;
        this.log = log;
        this.handle = method == null ? null : bind(method, handler);
    }

    private MethodHandle bind(Method method, Object target) {
        try {
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method).bindTo(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Komut metodu baglanamadi: " + method, e);
        }
    }

    int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (playerOnly && !(sender instanceof Player)) {
            lang.send(sender, "command.player-only");
            return 0;
        }
        Object[] args = new Object[bindings.size() + 1];
        args[0] = sender;
        for (int i = 0; i < bindings.size(); i++) {
            Binding binding = bindings.get(i);
            args[i + 1] = readOrDefault(ctx, binding);
        }
        try {
            handle.invokeWithArguments(args);
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        } catch (CommandException e) {
            lang.send(sender, e.messageKey());
            return 0;
        } catch (Throwable t) {
            lang.send(sender, "command.internal-error");
            log.log(Level.SEVERE, "Komut hatasi: " + handler.getClass().getSimpleName(), t);
            return 0;
        }
    }

    /** Alt komut verilmeden calistirilan kok komut icin varsayilan yardim ciktisi. */
    int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Command root = handler.getClass().getAnnotation(Command.class);
        lang.send(sender, "command.help-header", LangService.of("command", root.value()));
        for (Method method : handler.getClass().getDeclaredMethods()) {
            Command sub = method.getAnnotation(Command.class);
            if (sub == null || sub.value().isEmpty()) continue;
            lang.send(sender, "command.help-line",
                    LangService.of("command", root.value()),
                    LangService.of("sub", sub.value()));
        }
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    /**
     * Opsiyonel argumanlar Brigadier agacinda kisa yolla calistirildiginda context'te
     * bulunmaz; bu durumda ilkel tiplerde 0/false, nesnelerde null veriyoruz.
     */
    private Object readOrDefault(CommandContext<CommandSourceStack> ctx, Binding binding) {
        try {
            return binding.extractor().extract(ctx, binding.name());
        } catch (IllegalArgumentException notProvided) {
            Class<?> type = binding.type();
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == double.class) return 0.0D;
            if (type == boolean.class) return false;
            return null;
        }
    }
}
