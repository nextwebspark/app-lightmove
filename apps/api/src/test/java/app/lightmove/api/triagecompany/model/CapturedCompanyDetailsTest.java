package app.lightmove.api.triagecompany.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapturedCompanyDetailsTest {

    @Test
    @DisplayName("every door files a company under the industry the universe publishes")
    void industryIsCanonicalised() {
        // This record is the one seam all five mandate-supplied doors build, so a vendor's V2 label
        // and a hand-typed capital letter have to settle here or the report counts them apart.
        assertThat(details("IT Services and IT Consulting").industry())
                .isEqualTo("information technology & services");
        assertThat(details("Financial Services").industry()).isEqualTo("financial services");
        assertThat(details("Oil and Gas").industry()).isEqualTo("oil & energy");
    }

    @Test
    @DisplayName("an industry nobody can resolve is kept as it was typed")
    void unknownIndustryIsKept() {
        assertThat(details("  regional majlis catering  ").industry()).isEqualTo("regional majlis catering");
        assertThat(details("   ").industry()).isNull();
        assertThat(details(null).industry()).isNull();
    }

    private static CapturedCompanyDetails details(String industry) {
        return new CapturedCompanyDetails("Acme", industry, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
