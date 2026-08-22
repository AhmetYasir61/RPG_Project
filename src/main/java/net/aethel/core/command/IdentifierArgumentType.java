package net.aethel.core.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.NamespacedKey;

import java.util.Collection;
import java.util.List;

/**
 * "aethel:alev_kilici" gibi namespace'li kimlikler icin arguman tipi.
 *
 * Neden gerekli: Brigadier'in {@code StringArgumentType.word()} tipi IKI NOKTA
 * kabul etmez (izinli karakterler harf, rakam, _ - . +). Bu yuzden namespace'li
 * hicbir kimlik yazilamiyor, istemci "Expected whitespace to end one argument"
 * diyordu.
 *
 * Neden HAM bir Brigadier tipi olmaz: Paper, tanimadigi bir arguman tipini
 * reddeder ("Custom unknown argument type was passed, should be wrapped inside
 * an CustomArgumentType") ve o komut HIC kaydedilmez. Bir kez oyle denendi ve
 * 16 komut birden dustu; bu yuzden taban tip vanilla'nin kendi
 * resource_location'i, sarmalayici da Paper'in kendi arayuzudur.
 *
 * Namespace yazilmazsa vanilla "minecraft" varsayar; bu durumda YALNIZCA ad
 * kismi dondurulur ve varsayilan namespace'i komut kendi ekler. Projede
 * minecraft namespace'inde kimlik bulunmadigi icin bu ayrim guvenlidir.
 */
public final class IdentifierArgumentType implements CustomArgumentType.Converted<String, NamespacedKey> {

    private static final IdentifierArgumentType INSTANCE = new IdentifierArgumentType();

    private IdentifierArgumentType() {}

    public static IdentifierArgumentType identifier() {
        return INSTANCE;
    }

    @Override
    public ArgumentType<NamespacedKey> getNativeType() {
        return ArgumentTypes.namespacedKey();
    }

    @Override
    public String convert(NamespacedKey key) throws CommandSyntaxException {
        return resolve(key.getNamespace(), key.getKey());
    }

    /**
     * Saf donusum; sunucu calisma zamani gerektirmedigi icin test edilebilir.
     * Vanilla namespace yazilmayan girdiye "minecraft" koyar; o durumda yalnizca
     * ad kismi doner ve varsayilan namespace'i komut kendi ekler.
     */
    static String resolve(String namespace, String key) {
        return NamespacedKey.MINECRAFT.equals(namespace) ? key : namespace + ":" + key;
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("alev_kilici", "aethel:alev_kilici");
    }
}
