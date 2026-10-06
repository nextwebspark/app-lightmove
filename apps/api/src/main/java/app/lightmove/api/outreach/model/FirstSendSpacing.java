package app.lightmove.api.outreach.model;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * How far behind the first person each person of one Start is due: zero, then one to three minutes more
 * each. A mailbox that sends fifty at once is a mailbox flagged.
 */
public final class FirstSendSpacing {

    public static final Duration MIN_GAP = Duration.ofSeconds(60);
    public static final Duration MAX_GAP = Duration.ofSeconds(180);

    private FirstSendSpacing() {}

    public static List<Duration> offsetsOf(int people, RandomGenerator random) {
        List<Duration> offsets = new ArrayList<>(people);
        Duration next = Duration.ZERO;
        for (int index = 0; index < people; index++) {
            offsets.add(next);
            next = next.plusSeconds(random.nextLong(MIN_GAP.toSeconds(), MAX_GAP.toSeconds() + 1));
        }
        return offsets;
    }
}
