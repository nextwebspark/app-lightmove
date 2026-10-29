package app.lightmove.api.common.time.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import app.lightmove.api.common.time.model.DateTimePreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DateTimeFormatsTest {

    private static final Instant MOMENT = Instant.parse("2026-12-04T10:30:00Z");

    @Test
    @DisplayName("the standard reads 04 Dec 2026, 14:30 in Dubai")
    void readsTheStandard() {
        DateTimeFormats formats = DateTimeFormats.of(DateTimePreferences.STANDARD);
        assertThat(formats.dateTime(MOMENT)).isEqualTo("04 Dec 2026, 14:30");
        assertThat(formats.date(LocalDate.of(2026, 12, 4))).isEqualTo("04 Dec 2026");
    }

    @Test
    @DisplayName("every chosen format and zone is honoured")
    void readsEveryChoice() {
        ZoneId newYork = ZoneId.of("America/New_York");
        assertThat(formatsOf(newYork, DateFormatStyle.MMM_D_YYYY, TimeFormatStyle.H12).dateTime(MOMENT))
                .isEqualTo("Dec 4, 2026, 5:30 AM");
        assertThat(formatsOf(newYork, DateFormatStyle.DD_MM_YYYY, TimeFormatStyle.H24).date(MOMENT))
                .isEqualTo("04/12/2026");
        assertThat(formatsOf(newYork, DateFormatStyle.MM_DD_YYYY, TimeFormatStyle.H24).date(MOMENT))
                .isEqualTo("12/04/2026");
        assertThat(formatsOf(newYork, DateFormatStyle.YYYY_MM_DD, TimeFormatStyle.H24).date(MOMENT))
                .isEqualTo("2026-12-04");
    }

    @Test
    @DisplayName("a person's own choice wins over the fallback, one value at a time")
    void settlesEachValueOnItsOwn() {
        DateTimePreferences settled = DateTimePreferences.settle(null, DateFormatStyle.YYYY_MM_DD, null,
                DateTimePreferences.STANDARD);
        assertThat(settled.zone()).isEqualTo(ZoneId.of("Asia/Dubai"));
        assertThat(settled.dateFormat()).isEqualTo(DateFormatStyle.YYYY_MM_DD);
        assertThat(settled.timeFormat()).isEqualTo(TimeFormatStyle.H24);
    }

    private static DateTimeFormats formatsOf(ZoneId zone, DateFormatStyle date, TimeFormatStyle time) {
        return DateTimeFormats.of(new DateTimePreferences(zone, date, time));
    }
}
