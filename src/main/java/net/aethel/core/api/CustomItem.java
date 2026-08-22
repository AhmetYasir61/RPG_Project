package net.aethel.core.api;

import java.util.List;
import java.util.Map;

/**
 * Bir custom item tanimi. contents/<ns>/items/*.yml dosyasindan okunur; gorunum
 * 2D texture + item_model bileseni ile saglanir, 3D model gerekmez.
 */
public record CustomItem(String id,
                         String namespace,
                         String displayName,
                         List<String> lore,
                         String baseMaterial,
                         String texture,
                         String rarity,
                         int customModelData,
                         Map<String, Double> attributes,
                         Map<String, Integer> enchantments,
                         Integer maxDurability,
                         boolean unbreakable,
                         boolean glow,
                         FoodProperties food,
                         EquipProperties equip,
                         List<String> tags,
                         Socketing socketing) {

    /**
     * Soket ve evrim tanimi; null ise item soket almaz.
     *
     * thresholds: her asamanin gerektirdigi OLDURME sayisi, artan sirada. Ilk
     * eleman daima 0'dir (takilan tasin ilk, en soluk hali). Son asamaya
     * ulasildiginda item finalId'ye DONUSUR -- bakir kilic alev kilici olur.
     */
    public record Socketing(int slots, List<Integer> thresholds, String finalId) {

        /** Verilen oldurme sayisinin denk geldigi asama indeksi. */
        public int stageFor(int kills) {
            int stage = 0;
            for (int i = 0; i < thresholds.size(); i++) {
                if (kills >= thresholds.get(i)) stage = i;
            }
            return stage;
        }

        public int lastStage() {
            return Math.max(0, thresholds.size() - 1);
        }

        /** Son asamada donusecek bir item tanimliysa true. */
        public boolean evolves() {
            return finalId != null && !finalId.isBlank();
        }
    }

    /** Yiyecek bileseni; null ise item yenilemez. */
    public record FoodProperties(int nutrition, float saturation, boolean alwaysEdible,
                                 float eatSeconds) {}

    /** Giyilebilirlik; slot "head", "chest", "legs", "feet" olabilir. */
    public record EquipProperties(String slot, double armor, double toughness,
                                  String equipSound) {}

    /**
     * item_model bileseninin degeri. Istemci bunu
     * assets/<namespace>/items/<id>.json dosyasina cozer -- yani ONEK YOKTUR.
     *
     * Burada bir kez "item/" oneki vardi ve istemci
     * assets/aethel/items/item/<id>.json ariyordu; oyle bir dosya uretilmiyor,
     * tanim bulunamiyor ve TUM custom itemlar mor-siyah kare gorunuyordu.
     * Sunucu gunluguene hicbir sey yazilmiyordu.
     *
     * "item/" oneki yalnizca MODEL dosyasinin yolunda vardir
     * (assets/<ns>/models/item/<id>.json) ve ona tanimin ICINDEN,
     * {@link #modelPath()} ile isaret edilir.
     */
    public String modelKey() {
        return namespace + ":" + id;
    }

    /** Tanimin gosterdigi model dosyasi: assets/<ns>/models/item/<id>.json */
    public String modelPath() {
        return namespace + ":item/" + id;
    }

    /** Soket alabiliyor mu. */
    public boolean socketable() {
        return socketing != null && socketing.slots() > 0;
    }

    /**
     * Bir tas takiliyken ve belirli bir asamadayken kullanilacak item_model.
     * Taban asamada (0) taban kimlik kullanilir: gereksiz doku uretilmez.
     */
    public String modelKey(String stoneId, int stage) {
        if (stoneId == null || stoneId.isBlank() || stage <= 0) return modelKey();
        return modelKey() + "_" + bare(stoneId) + "_" + stage;
    }

    /** "aethel:ates_tasi" -> "ates_tasi" */
    public static String bare(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** Item'in PDC icinde saklanan kalici kimligi. */
    public String fullId() {
        return namespace + ":" + id;
    }
}
