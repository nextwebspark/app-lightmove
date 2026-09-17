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
}
