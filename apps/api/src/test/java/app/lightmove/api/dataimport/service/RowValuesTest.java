package app.lightmove.api.dataimport.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a spreadsheet cell is read as, however the file spelled it. */
class RowValuesTest {

    @Test
    @DisplayName("a gender is read from the spellings files carry, whatever their case or punctuation")
    void genderSpellingsFold() {
        assertThat(RowValues.gender("Female")).isEqualTo("female");
        assertThat(RowValues.gender(" F ")).isEqualTo("female");
        assertThat(RowValues.gender("woman")).isEqualTo("female");
        assertThat(RowValues.gender("M")).isEqualTo("male");
        assertThat(RowValues.gender("MALE")).isEqualTo("male");
        assertThat(RowValues.gender("Non-binary")).isEqualTo("other");
        assertThat(RowValues.gender("other")).isEqualTo("other");
    }

    @Test
    @DisplayName("a cell nobody can read states no gender — it is never filed as other")
    void unreadableGenderIsNotRecorded() {
        assertThat(RowValues.gender("prefer not to say")).isNull();
        assertThat(RowValues.gender("n/a")).isNull();
        assertThat(RowValues.gender("   ")).isNull();
        assertThat(RowValues.gender(null)).isNull();
    }

    @Test
    @DisplayName("a notice period folds onto the option the pickers offer, however the file spelled it")
    void noticePeriodSpellingsFold() {
        assertThat(RowValues.noticePeriod("3 Months")).isEqualTo("3 months");
        assertThat(RowValues.noticePeriod(" 3m ")).isEqualTo("3 months");
        assertThat(RowValues.noticePeriod("90 days")).isEqualTo("3 months");
        assertThat(RowValues.noticePeriod("one month")).isEqualTo("1 month");
        assertThat(RowValues.noticePeriod("26 weeks")).isEqualTo("6 months");
        assertThat(RowValues.noticePeriod("Immediate")).isEqualTo("None");
    }

    @Test
    @DisplayName("a period the pickers do not offer states nothing — it is never rounded to the nearest")
    void unofferedNoticePeriodIsNotRecorded() {
        assertThat(RowValues.noticePeriod("6 weeks")).isNull();
        assertThat(RowValues.noticePeriod("negotiable")).isNull();
        assertThat(RowValues.noticePeriod("   ")).isNull();
        assertThat(RowValues.noticePeriod(null)).isNull();
    }
}
