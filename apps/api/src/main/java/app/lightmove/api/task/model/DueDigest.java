package app.lightmove.api.task.model;

import java.util.List;

/** A person's open tasks due before the digest's horizon, soonest first: everything late, today's, and the next days'. */
public record DueDigest(List<DueDigestLine> lines) {

    public DueDigest {
        lines = List.copyOf(lines);
    }
}
