package net.aethel.core.util;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Tick basina zaman butcesi olan is kuyrugu. Buyuk koleksiyonlari tek tick'te
 * gezmek yerine (orn. tum oyuncular, tum bolgeler) isi tick'lere yayar.
 */
public final class BudgetedTask<T> implements Runnable {

    private final Deque<T> queue = new ArrayDeque<>();
    private final Consumer<T> action;
    private final long budgetNanos;

    /** budgetMillis: bir tick icinde bu ise ayrilan ust sinir (tipik 1-2 ms). */
    public BudgetedTask(Consumer<T> action, double budgetMillis) {
        this.action = action;
        this.budgetNanos = (long) (budgetMillis * 1_000_000L);
    }

    public void submit(Collection<T> items) {
        queue.addAll(items);
    }

    public void submit(T item) {
        queue.add(item);
    }

    public int pending() {
        return queue.size();
    }

    @Override
    public void run() {
        long deadline = System.nanoTime() + budgetNanos;
        T item;
        while ((item = queue.poll()) != null) {
            action.accept(item);
            if (System.nanoTime() >= deadline) return;   // kalan is bir sonraki tick'e
        }
    }
}
