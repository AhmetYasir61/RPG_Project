package net.aethel.core.storage;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Modullerin veri erisiminde uydugu ortak sozlesme. Tum donusler CompletableFuture:
 * cagiran taraf ana thread'i asla bloklamaz.
 */
public interface Repository<T, ID> {

    CompletableFuture<Optional<T>> find(ID id);

    CompletableFuture<List<T>> findAll();

    CompletableFuture<Void> save(T entity);

    CompletableFuture<Void> delete(ID id);
}
