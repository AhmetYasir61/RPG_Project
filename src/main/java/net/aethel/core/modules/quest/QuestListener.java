package net.aethel.core.modules.quest;

import net.aethel.core.api.QuestService.ObjectiveType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;

/**
 * Oyun olaylarini gorev hedeflerine baglar. Tek bir olay birden fazla gorevi
 * ilerletebilir; hangi gorevlerin etkilenecegine QuestModule karar verir.
 */
final class QuestListener implements Listener {

    private final QuestModule quests;

    QuestListener(QuestModule quests) {
        this.quests = quests;
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        quests.progress(killer, ObjectiveType.KILL, event.getEntity().getType().name(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var stack = event.getItem().getItemStack();
        quests.progress(player, ObjectiveType.COLLECT,
                stack.getType().name(), stack.getAmount());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        quests.progress(player, ObjectiveType.CRAFT,
                event.getRecipe().getResult().getType().name(), 1);
    }
}
