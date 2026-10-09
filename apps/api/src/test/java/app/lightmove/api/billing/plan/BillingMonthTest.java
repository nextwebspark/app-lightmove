package app.lightmove.api.billing.plan;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.billing.plan.model.BillingMonth;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The month credits and fair use run over, stepped from a subscription's start or the calendar's. */
class BillingMonthTest {

    @Test
    @DisplayName("a period starting on the 31st steps to the month's last day and back to the 31st")
    void stepsFromTheAnchorEachTime() {
        Instant anchor = Instant.parse("2026-01-31T10:00:00Z");

        BillingMonth march = BillingMonth.containing(anchor, Instant.parse("2026-03-05T00:00:00Z"));

        assertThat(march.start()).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
        assertThat(march.end()).isEqualTo(Instant.parse("2026-03-31T10:00:00Z"));
    }

    @Test
    @DisplayName("a month starts at the anchor's instant, inclusive")
    void theBoundaryBelongsToTheNewMonth() {
        Instant anchor = Instant.parse("2026-01-15T00:00:00Z");

        BillingMonth month = BillingMonth.containing(anchor, Instant.parse("2026-04-15T00:00:00Z"));

        assertThat(month.start()).isEqualTo(Instant.parse("2026-04-15T00:00:00Z"));
        assertThat(month.end()).isEqualTo(Instant.parse("2026-05-15T00:00:00Z"));
    }

    @Test
    @DisplayName("a moment before the anchor falls in the month that ends at it")
    void stepsBackBeforeTheAnchor() {
        Instant anchor = Instant.parse("2026-06-10T00:00:00Z");

        BillingMonth month = BillingMonth.containing(anchor, Instant.parse("2026-06-01T00:00:00Z"));

        assertThat(month.start()).isEqualTo(Instant.parse("2026-05-10T00:00:00Z"));
        assertThat(month.end()).isEqualTo(anchor);
    }

    @Test
    @DisplayName("without a subscription the month is the UTC calendar month")
    void noAnchorIsTheCalendarMonth() {
        BillingMonth month = BillingMonth.containing(null, Instant.parse("2026-10-07T23:30:00Z"));

        assertThat(month.start()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(month.end()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }
}
