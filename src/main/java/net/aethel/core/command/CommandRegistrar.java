package net.aethel.core.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.aethel.core.api.FeatureService;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @Command anotasyonlu siniflari Brigadier agacina cevirir: otomatik tab-complete,
 * otomatik yetki kontrolu ve otomatik /help. Modul yalnizca metot yazar.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CommandRegistrar {

    private final LangService lang;
    private final Logger log;
    private FeatureService features;
    private final Map<String, Object> pending = new LinkedHashMap<>();
    private final Map<String, String> owners = new LinkedHashMap<>();
    private final SuggestionRegistry suggestions = new SuggestionRegistry();

    public CommandRegistrar(LangService lang, Logger log) {
        this.lang = lang;
        this.log = log;
    }

    /**
     * Modulun kendi kimliklerini tab-complete'e acmasi icin.
     * Ornek: {@code ctx.commands().suggest("dialog", () -> nodeIds())}
     *
     * Saglayici her tus vurusunda cagrilir; bellekteki hazir bir koleksiyon
     * dondurmelidir.
     */
    public void suggest(String name, java.util.function.Supplier<java.util.Collection<String>> source) {
        suggestions.register(name, source);
    }

    public SuggestionRegistry suggestions() {
        return suggestions;
    }

    /** Ozellik servisi cekirdek kurulurken baglanir; komut kapisi buradan gecer. */
    public void features(FeatureService features) {
        this.features = features;
    }

    /**
     * Komut nesnesini kuyruga alir. Brigadier kayitlari yalnizca COMMANDS yasam
     * dongusu olayinda yapilabildigi icin, tum moduller onLoad'da tanimlarini birakir
     * ve kayit tek seferde flush() ile yapilir.
     */
    public void register(String owner, Object handler) {
        Command root = handler.getClass().getAnnotation(Command.class);
        if (root == null) {
            log.warning("@Command eksik: " + handler.getClass().getName());
            return;
        }
        pending.put(root.value(), handler);
        owners.put(root.value(), owner);
    }

    /** CorePlugin, COMMANDS olayinda bunu cagirir. */
    public void flush(Commands commands) {
        pending.forEach((name, handler) -> {
            try {
                Command root = handler.getClass().getAnnotation(Command.class);
                commands.register(build(root, handler).build(),
                        root.descriptionKey().isEmpty() ? name : root.descriptionKey(),
                        Arrays.asList(root.aliases()));
            } catch (Exception e) {
                log.log(Level.SEVERE, "Komut kaydedilemedi: " + name, e);
            }
        });
        // Sayi degil isim logluyoruz: eksik bir komutu tespit etmek icin hangilerinin
        // kaydedildigini gormek sarttir, sayi tek basina teshis ettirmez.
        log.info("Kayitli kok komut (" + pending.size() + "): " + pending.keySet());
    }

    private LiteralArgumentBuilder<CommandSourceStack> build(Command root, Object handler) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(root.value());
        node.requires(source -> available(root)
                && hasPermission(source, permissionOf(root, root.value(), null)));

        List<Method> subs = new ArrayList<>();
        Method rootMethod = null;
        for (Method method : handler.getClass().getDeclaredMethods()) {
            Command sub = method.getAnnotation(Command.class);
            if (sub == null) continue;
            if (sub.value().isEmpty()) rootMethod = method; else subs.add(method);
        }
        subs.sort(Comparator.comparing(m -> m.getAnnotation(Command.class).value()));

        for (Method method : subs) {
            Command sub = method.getAnnotation(Command.class);
            LiteralArgumentBuilder<CommandSourceStack> child = Commands.literal(sub.value());
            child.requires(source -> available(root) && available(sub)
                    && hasPermission(source, permissionOf(sub, root.value(), sub.value())));
            attach(child, method, handler, sub);
            node.then(child);
        }

        if (rootMethod != null) {
            attach(node, rootMethod, handler, rootMethod.getAnnotation(Command.class));
        } else {
            node.executes(new CommandInvoker(handler, null, List.of(), false, lang, log)::help);
        }
        return node;
    }

    /** Metot parametrelerini zincirleyerek arguman dugumlerini olusturur. */
    private void attach(ArgumentBuilder<CommandSourceStack, ?> parent,
                        Method method, Object handler, Command meta) {
        Parameter[] params = method.getParameters();
        List<CommandInvoker.Binding> bindings = new ArrayList<>();

        List<ArgumentBuilder<CommandSourceStack, ?>> chain = new ArrayList<>();
        for (int i = 1; i < params.length; i++) {          // 0. parametre daima CommandSender
            Parameter param = params[i];
            Arg arg = param.getAnnotation(Arg.class);
            boolean greedy = arg != null && arg.greedy();
            String name = arg == null || arg.value().isEmpty() ? param.getName() : arg.value();
            var resolver = ArgumentResolvers.forType(param.getType(), greedy);
            bindings.add(new CommandInvoker.Binding(name, resolver.extractor(), param.getType()));

            var argument = RequiredArgumentBuilder
                    .<CommandSourceStack, Object>argument(name, (com.mojang.brigadier.arguments.ArgumentType<Object>) resolver.type());
            String source = arg == null ? "" : arg.suggests();
            if (!source.isEmpty()) {
                argument.suggests((context, builder) -> {
                    // builder.getRemaining(): kullanicinin O ANDA yazdigi parca.
                    // Onerileri buna gore suzmezsek istemci hepsini gosterir ve
                    // uzun listelerde tamamlama ise yaramaz.
                    suggestions.suggest(source, builder.getRemaining())
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
            }
            chain.add(argument);
        }

        CommandInvoker invoker = new CommandInvoker(handler, method, bindings, meta.playerOnly(), lang, log);

        if (chain.isEmpty()) {
            parent.executes(invoker::execute);
            return;
        }
        // Zinciri sondan basa baglayip her seviyede executes koyariz: opsiyonel
        // argumanlar boylece ekstra kod olmadan calisir.
        for (int i = chain.size() - 1; i >= 0; i--) {
            ArgumentBuilder<CommandSourceStack, ?> current = chain.get(i);
            current.executes(invoker::execute);
            if (i + 1 < chain.size()) current.then(chain.get(i + 1));
        }
        parent.then(chain.get(0));
        Parameter[] all = method.getParameters();
        boolean firstOptional = all.length > 1
                && all[1].getAnnotation(Arg.class) != null
                && all[1].getAnnotation(Arg.class).optional();
        if (firstOptional) parent.executes(invoker::execute);
    }

    private String permissionOf(Command meta, String root, String sub) {
        if (!meta.permission().isEmpty()) return meta.permission();
        return sub == null ? "core.command." + root : "core.command." + root + "." + sub;
    }

    /**
     * Ozellik kapaliysa dugum agacta hic gorunmez. Yetki reddi ile ozellik kapaliligi
     * arasindaki fark onemli: yetki reddi "yapamazsin", ozellik kapaliligi "boyle bir
     * sey yok" demektir ve oyuncuya var olmayan bir mekanigi hic sizdirmaz.
     */
    private boolean available(Command meta) {
        return features == null || meta.feature().isEmpty() || features.enabled(meta.feature());
    }

    private boolean hasPermission(CommandSourceStack source, String permission) {
        CommandSender sender = source.getSender();
        return !(sender instanceof Player) || sender.hasPermission(permission);
    }
}
