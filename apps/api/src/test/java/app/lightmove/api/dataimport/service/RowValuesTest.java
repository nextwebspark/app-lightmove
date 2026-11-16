package app.lightmove.api.dataimport.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a spreadsheet cell is read as, however the file spelled it. */
class RowValuesTest {

    @Test
    @DisplayName("a currency is read off the cell's letters, and written only where ISO issues it")
    void currencyIsAnIsoCodeOrNothing() {
        assertThat(RowValues.currency("AED 450,000")).isEqualTo("AED");
        assertThat(RowValues.currency(" sar ")).isEqualTo("SAR");
        assertThat(RowValues.currency("ZZZ")).isNull();
        assertThat(RowValues.currency("Dirham")).isNull();
        assertThat(RowValues.currency("  ")).isNull();
    }

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

    @Test
    @DisplayName("a figure is read at the magnitude the file wrote it in")
    void magnitudeSuffixesScale() {
        assertThat(RowValues.number("$1.2bn")).isEqualTo(1_200_000_000L);
        assertThat(RowValues.number("1.2 bn USD")).isEqualTo(1_200_000_000L);
        assertThat(RowValues.number("5k")).isEqualTo(5_000L);
        assertThat(RowValues.number("2.5M")).isEqualTo(2_500_000L);
        assertThat(RowValues.number("AED 450,000")).isEqualTo(450_000L);
        assertThat(RowValues.number("450,000 AED")).isEqualTo(450_000L);
        assertThat(RowValues.number("1 200 000")).isEqualTo(1_200_000L);
        assertThat(RowValues.number("1.200.000")).isEqualTo(1_200_000L);
        assertThat(RowValues.number("1.234,56")).isEqualTo(1_235L);
        assertThat(RowValues.number("10,001+")).isEqualTo(10_001L);
        assertThat(RowValues.number("3000")).isEqualTo(3_000L);
        assertThat(RowValues.number("-12")).isEqualTo(-12L);
    }

    @Test
    @DisplayName("a range or a lone decimal comma states no single figure, so it is not guessed at")
    void ambiguousFiguresAreNotRecorded() {
        assertThat(RowValues.number("1001-5000")).isNull();
        assertThat(RowValues.number("51 – 200")).isNull();
        assertThat(RowValues.number("1,5")).isNull();
        assertThat(RowValues.number("5.000")).isNull();
        assertThat(RowValues.number("1.234")).isNull();
        assertThat(RowValues.number("n/a")).isNull();
        assertThat(RowValues.number("   ")).isNull();
    }

    @Test
    @DisplayName("an email cell is stored canonical, and a placeholder is not an address")
    void emailIsAnAddressOrNothing() {
        assertThat(RowValues.email(" Layla@ACWA.example ")).isEqualTo("layla@acwa.example");
        assertThat(RowValues.email("mailto:layla@acwa.example")).isEqualTo("layla@acwa.example");
        assertThat(RowValues.email("n/a")).isNull();
        assertThat(RowValues.email("-")).isNull();
        assertThat(RowValues.email("TBC")).isNull();
        assertThat(RowValues.email("layla@acwa")).isNull();
    }

    @Test
    @DisplayName("a LinkedIn profile is stored in its canonical spelling, scheme or not")
    void linkedinProfileIsCanonical() {
        assertThat(RowValues.linkedinProfile("linkedin.com/in/Layla-Haddad"))
                .isEqualTo("https://www.linkedin.com/in/layla-haddad/");
        assertThat(RowValues.linkedinProfile("https://uk.linkedin.com/in/layla-haddad?trk=x"))
                .isEqualTo("https://www.linkedin.com/in/layla-haddad/");
        assertThat(RowValues.linkedinProfile("see notes")).isEqualTo("see notes");
        assertThat(RowValues.linkedinCompany("linkedin.com/company/acwa-power"))
                .isEqualTo("https://linkedin.com/company/acwa-power");
    }
}
