package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** One Start's people, each due one to three minutes behind the one before. */
class FirstSendSpacingTest {

    @Test
    @DisplayName("the first is due at once and each after it one to three minutes later")
    void eachPersonFollowsTheOneBefore() {
        List<Duration> offsets = FirstSendSpacing.offsetsOf(50, new SplittableRandom(7));

        assertThat(offsets).hasSize(50).first().isEqualTo(Duration.ZERO);
        for (int index = 1; index < offsets.size(); index++) {
            assertThat(offsets.get(index).minus(offsets.get(index - 1)))
                    .isBetween(FirstSendSpacing.MIN_GAP, FirstSendSpacing.MAX_GAP);
        }
    }

    @Test
    @DisplayName("the same draw gives the same spacing")
    void aSeededDrawRepeats() {
        assertThat(FirstSendSpacing.offsetsOf(5, new SplittableRandom(3)))
                .isEqualTo(FirstSendSpacing.offsetsOf(5, new SplittableRandom(3)));
    }
}
