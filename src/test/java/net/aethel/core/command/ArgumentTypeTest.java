package net.aethel.core.command;

import com.mojang.brigadier.arguments.ArgumentType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Komut argumani tiplerinin Paper tarafindan KABUL EDILEBILIR olmasini dogrular.
 *
 * Bir kez ham bir Brigadier arguman tipi kullanildi ve Paper onu reddetti
 * ("Custom unknown argument type was passed, should be wrapped inside an
 * CustomArgumentType"): 16 komut birden kaydedilemedi. Derleme temizdi, testler
 * yesildi, hata yalnizca gercek sunucuda goruldu. Bu test o kapiyi kapatir.
 */
class ArgumentTypeTest {

    /** Kimlik tipi Paper'in sarmalayicisini uygulamali, ham ArgumentType olmamali. */
    @Test
    void identifierTypeIsWrappedForPaper() {
        ArgumentType<?> type = IdentifierArgumentType.identifier();
        assertInstanceOf(CustomArgumentType.class, type,
                "Ham Brigadier tipi: Paper bunu reddeder ve komut hic kaydedilmez");
    }

    /**
     * Namespace yazilmadiginda vanilla "minecraft" varsayar; o durumda yalnizca
     * ad kismi donmeli ki komut kendi varsayilan namespace'ini ekleyebilsin.
     * Kendi namespace'imiz ise oldugu gibi korunmali.
     *
     * getNativeType() burada CAGRILMIYOR: Paper'in vanilla saglayicisi ancak
     * calisan bir sunucuda kuruluyor. Donusum mantigi bu yuzden saf bir metoda
     * ayrildi ve test edilen o.
     */
    @Test
    void namespaceHandling() {
        assertEquals("alev_kilici",
                IdentifierArgumentType.resolve("minecraft", "alev_kilici"));
        assertEquals("aethel:alev_kilici",
                IdentifierArgumentType.resolve("aethel", "alev_kilici"));
    }
}
