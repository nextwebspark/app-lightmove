package app.uncava.api.task.repository;

import java.util.UUID;

/** How many tasks were filed from one preset. */
public interface TaskPresetUsage {

    UUID getPresetId();

    long getUses();
}
