package app.lightmove.api.task.model;

import java.util.List;

/** A person's open tasks for the morning's digest: how many are due today and overdue, and the first few named. */
public record DueDigest(int dueToday, int overdue, List<DueDigestLine> lines) {

    public DueDigest {
        lines = List.copyOf(lines);
    }

    public boolean isEmpty() {
        return dueToday == 0 && overdue == 0;
    }
}
