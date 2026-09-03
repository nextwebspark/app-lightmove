package app.lightmove.api.core.text.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The gate every billed lookup passes through. A slug coming back means "this is a LinkedIn page
 * naming somebody, and here is the key the providers store them under"; anything else costs nothing.
 */
class LinkedInUrlsTest {

    @Test
    @DisplayName("a profile URL yields its slug, lowercased for the datasets that key on it")
    void aProfileUrlYieldsItsSlug() {
        assertThat(LinkedInUrls.profileSlugOrNull("https://www.linkedin.com/in/john-smith/"))
                .isEqualTo("john-smith");
        // LinkedIn treats the path case-insensitively; the dataset filters do not.
        assertThat(LinkedInUrls.profileSlugOrNull("https://www.linkedin.com/in/John-Smith-1a2b"))
                .isEqualTo("john-smith-1a2b");
        // Regional subdomains and tracking parameters are the same page.
        assertThat(LinkedInUrls.profileSlugOrNull("https://ae.linkedin.com/in/hakanalac?trk=public"))
                .isEqualTo("hakanalac");
        assertThat(LinkedInUrls.profileSlugOrNull("HTTPS://WWW.LINKEDIN.COM/in/x"))
                .isEqualTo("x");
    }

    @Test
    @DisplayName("a company URL yields its slug the same way")
    void aCompanyUrlYieldsItsSlug() {
        assertThat(LinkedInUrls.companySlugOrNull("https://www.linkedin.com/company/FINEOS/"))
                .isEqualTo("fineos");
        assertThat(LinkedInUrls.companySlugOrNull("https://ie.linkedin.com/company/fineos?trk=x"))
                .isEqualTo("fineos");
    }

    @Test
    @DisplayName("anything that is not a LinkedIn page naming somebody is refused")
    void anythingElseIsRefused() {
        // The one that mattered: a mis-scraped URL that merely contains the path shape used to buy a
        // vendor lookup, because the parser never checked whose site it was.
        assertThat(LinkedInUrls.companySlugOrNull("https://example.com/company/about")).isNull();
        assertThat(LinkedInUrls.profileSlugOrNull("https://example.com/in/someone")).isNull();
        // Not linkedin.com, however much it looks like it.
        assertThat(LinkedInUrls.profileSlugOrNull("https://linkedin.com.evil.test/in/x")).isNull();
        // A page with no slug is nobody to research.
        assertThat(LinkedInUrls.profileSlugOrNull("https://www.linkedin.com/in/")).isNull();
        assertThat(LinkedInUrls.profileSlugOrNull("https://www.linkedin.com/feed/")).isNull();
        assertThat(LinkedInUrls.profileSlugOrNull("not a url at all")).isNull();
        assertThat(LinkedInUrls.profileSlugOrNull(null)).isNull();
        // A company page is not a profile and vice versa.
        assertThat(LinkedInUrls.profileSlugOrNull("https://www.linkedin.com/company/fineos")).isNull();
    }
}
