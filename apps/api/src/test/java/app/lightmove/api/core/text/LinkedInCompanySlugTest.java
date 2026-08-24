package app.lightmove.api.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.text.service.LinkedInCompanySlug;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The identity a captured LinkedIn page is matched against the Apollo universe by.
 *
 * <p>The slug rather than the URL, because the same company page is written half a dozen ways — and
 * every one of those spellings has to resolve to the same company or the extension files duplicates.
 */
class LinkedInCompanySlugTest {

    @Test
    @DisplayName("the same company page, however it was spelled")
    void normalisesEverySpellingToOneSlug() {
        assertThat(LinkedInCompanySlug.of("https://www.linkedin.com/company/al-rawabi/")).isEqualTo("al-rawabi");
        assertThat(LinkedInCompanySlug.of("https://linkedin.com/company/al-rawabi")).isEqualTo("al-rawabi");
        assertThat(LinkedInCompanySlug.of("linkedin.com/company/al-rawabi")).isEqualTo("al-rawabi");
        assertThat(LinkedInCompanySlug.of("  https://www.linkedin.com/company/AL-RAWABI  ")).isEqualTo("al-rawabi");
    }

    @Test
    @DisplayName("a country subdomain is the same company — sa.linkedin.com is LinkedIn")
    void acceptsACountrySubdomain() {
        assertThat(LinkedInCompanySlug.of("https://sa.linkedin.com/company/al-rawabi")).isEqualTo("al-rawabi");
    }

    @Test
    @DisplayName("a sub-page and a query string are not part of the identity")
    void dropsEverythingPastTheSlug() {
        assertThat(LinkedInCompanySlug.of("https://www.linkedin.com/company/al-rawabi/about/"))
                .isEqualTo("al-rawabi");
        assertThat(LinkedInCompanySlug.of("https://www.linkedin.com/company/al-rawabi/people?tab=x"))
                .isEqualTo("al-rawabi");
    }

    @Test
    @DisplayName("a personal profile is not a company, however company-shaped the request that carried it")
    void refusesAnythingThatIsNotACompanyUrl() {
        assertThat(LinkedInCompanySlug.of("https://www.linkedin.com/in/someone")).isNull();
        assertThat(LinkedInCompanySlug.of("https://www.linkedin.com/feed/")).isNull();
        assertThat(LinkedInCompanySlug.of("https://www.linkedin.com/company/")).isNull();
    }

    @Test
    @DisplayName("a host that merely ends in something like linkedin.com is not LinkedIn")
    void refusesAnotherHost() {
        assertThat(LinkedInCompanySlug.of("https://notlinkedin.com/company/al-rawabi")).isNull();
        assertThat(LinkedInCompanySlug.of("https://alrawabidairy.ae/company/al-rawabi")).isNull();
    }

    @Test
    @DisplayName("nothing in, nothing out")
    void answersNullForNothing() {
        assertThat(LinkedInCompanySlug.of(null)).isNull();
        assertThat(LinkedInCompanySlug.of("   ")).isNull();
        assertThat(LinkedInCompanySlug.of("not a url")).isNull();
    }
}
