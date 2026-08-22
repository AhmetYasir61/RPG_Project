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
                         List<String> tags) {

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

    /** Item'in PDC icinde saklanan kalici kimligi. */
    public String fullId() {
        return namespace + ":" + id;
    }
}
