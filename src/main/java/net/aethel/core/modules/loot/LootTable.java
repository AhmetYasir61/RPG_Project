package net.aethel.core.modules.loot;

import net.aethel.core.api.LootService.LootEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Agirlikli loot tablosu. Nadirlik carpani yuksekken dusuk agirlikli (nadir) girdiler
 * daha sik secilir; carpan tabloyu degistirmez, secim olasiligini egriltir.
 */
final class LootTable {

    private final String id;
    private final List<LootEntry> entries;
    private final int minRolls;
    private final int maxRolls;

    LootTable(String id, List<LootEntry> entries, int minRolls, int maxRolls) {
        this.id = id;
        this.entries = List.copyOf(entries);
        this.minRolls = minRolls;
        this.maxRolls = maxRolls;
    }

    String id() {
        return id;
    }

    /**
     * Nadirlik carpani, agirliklari ters yonde egriltir: agirligi dusuk (nadir) girdiler
     * carpanla birlikte gorece daha sik cikar. Duz carpim yapsaydik sik cikanlar daha da
     * sik cikardi ve mekanik tersine islerdi.
     */
    List<Roll> roll(double rarityMultiplier, Random random) {
        List<Roll> results = new ArrayList<>();
        int rolls = minRolls + random.nextInt(Math.max(1, maxRolls - minRolls + 1));
        rolls = (int) Math.round(rolls * Math.min(3.0, Math.max(1.0, rarityMultiplier * 0.6)));

        for (int i = 0; i < rolls; i++) {
            LootEntry entry = pick(rarityMultiplier, random);
            if (entry == null) continue;
            int amount = entry.minAmount() + random.nextInt(
                    Math.max(1, entry.maxAmount() - entry.minAmount() + 1));
            results.add(new Roll(entry.itemId(), amount));
        }
        return results;
    }

    private LootEntry pick(double rarityMultiplier, Random random) {
        double total = 0;
        for (LootEntry entry : entries) total += adjustedWeight(entry, rarityMultiplier);

        double target = random.nextDouble() * total;
        for (LootEntry entry : entries) {
            target -= adjustedWeight(entry, rarityMultiplier);
            if (target <= 0) return entry;
        }
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    /** Nadir girdinin agirligi carpanla yukselir; sik girdininki sabit kalir. */
    private double adjustedWeight(LootEntry entry, double rarityMultiplier) {
        double rarityBoost = Math.pow(rarityMultiplier, entry.minRarityTier());
        return entry.weight() * rarityBoost;
    }

    /** Tek bir uretilmis loot satiri. */
    record Roll(String itemId, int amount) {}
}
