package app.lightmove.api.core.storage.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues bucket keys for {@link StorageDeletionSweeper} in the transaction deleting the rows that named them (V174):
 * rolled back, nothing is queued; committed, the files go even if the instance dies before the bucket is called.
 */
@Component
@RequiredArgsConstructor
public class StorageDeletionQueue {

    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Collection<String> storageKeys) {
        List<String> keys = storageKeys.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO app_lm_storage_deletion (storage_key) VALUES (?)
                ON CONFLICT (storage_key) DO NOTHING
                """, keys, keys.size(), (statement, key) -> statement.setString(1, key));
    }
}
