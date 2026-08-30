package net.aethel.core.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item nitelikleri GERCEKTEN uygulanmali.
 *
 * Uzun sure yalnizca lore'a yaziliyorlardi: tanimda "damage: 8.5" yazsa da item
 * vanilla kilicin hasarini veriyor, tooltip'te vanilla degerler goruluyor ve
 * hicbir yere uyari dusmuyordu. Oyuncu 8.5 vurdugunu saniyordu.
 *
 * Bu test kaynak tarayarak uretim yolunun nitelik katmanini GERCEKTEN cagirdigini
 * dogrular: bir sunucu olmadan AttributeModifier kurulamaz, ama cagrinin kaybolmasi
 * yakalanabilir -- ve kaybolmasi tam olarak yasanan seydi.
 */
class ItemAttributeTest {

    private static final Path SOURCE = Path.of("src/main/java/net/aethel/core");

    @Test
    void itemFactoryAppliesAttributes() throws IOException {
        String body = Files.readString(
                SOURCE.resolve("modules/content/ItemFactory.java"));

        assertTrue(body.contains("attributes.apply("),
                "ItemFactory nitelikleri uygulamiyor: tanimdaki damage/armor "
                        + "degerleri yalnizca lore'da kalir, oyunda vanilla degerler gecerli olur");
        assertTrue(body.contains("attributes.clear("),
                "Nitelikler temizlenmeden ekleniyor: item her yenilendiginde "
                        + "eskilerin uzerine biner ve silah kalici olarak guclenir");
    }

    @Test
    void socketModuleAppliesStoneAttributes() throws IOException {
        String body = Files.readString(
                SOURCE.resolve("modules/socket/SocketModule.java"));

        assertTrue(body.contains("applyAttributes("),
                "Soket modulu tasin statlarini uygulamiyor: lore '+2 hasar' der "
                        + "ama tas hicbir sey yapmaz");
        assertTrue(body.contains("setAttributeModifiers(null)"),
                "Soket yenilemesi eski modifier'lari temizlemiyor");
    }

    /**
     * YAML ADI -> Bukkit niteligi eslemesi TEK yerde durmali.
     *
     * Aranan sey bir switch/case tablosudur ("damage" -> ATTACK_DAMAGE), canliya
     * sabit alan atayan kod degil: MobSpawner da nitelik kullanir ama YAML adi
     * cozmez, dolayisiyla cogaltma sayilmaz. Ikinci bir AD TABLOSU ise zamanla
     * ayrisir ve ayni ad iki yerde farkli sey yapar.
     */
    @Test
    void yamlNameMappingExistsOnlyOnce() throws IOException {
        List<String> owners = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SOURCE)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String body = Files.readString(file);
                if (body.contains("case \"damage\"") && body.contains("Attribute.")) {
                    owners.add(file.getFileName().toString());
                }
            }
        }
        assertTrue(owners.size() <= 1,
                "YAML nitelik ad tablosu birden fazla yerde: " + owners);
    }
}
