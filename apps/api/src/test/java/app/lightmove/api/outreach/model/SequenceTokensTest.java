package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SequenceTokensTest {

    private final SequenceTokens tokens = new SequenceTokens("Priya", "CFO", "Target Group", "Group CFO", "Dubai",
            "Yara", "Your move into treasury stood out.", "https://beta.uncava.com/book/yara-haddad");

    @Test
    void fillsEveryTokenItOffers() {
        assertThat(tokens.render("Hi {{firstName}}, {{ opener }} {{currentTitle}} at {{currentCompany}}, "
                + "{{positionTitle}} in {{location}}. {{senderFirstName}}"))
                .isEqualTo("Hi Priya, Your move into treasury stood out. CFO at Target Group, Group CFO in Dubai. Yara");
    }

    @Test
    void fillsTheBookingLinkAndSaysWhoUsesIt() {
        assertThat(tokens.render("Pick a time: {{ bookingLink }}"))
                .isEqualTo("Pick a time: https://beta.uncava.com/book/yara-haddad");
        assertThat(SequenceTokens.uses("Pick a time: {{ bookingLink }}", SequenceTokens.BOOKING_LINK)).isTrue();
        assertThat(SequenceTokens.uses("Hi {{firstName}}", SequenceTokens.BOOKING_LINK)).isFalse();
        assertThat(SequenceTokens.uses(null, SequenceTokens.BOOKING_LINK)).isFalse();
    }

    @Test
    void leavesAnUnknownTokenAsTyped() {
        assertThat(tokens.render("Hi {{fristName}}")).isEqualTo("Hi {{fristName}}");
    }

    @Test
    void leavesTheSignatureForTheEmailOrSignsOffWithTheFirstName() {
        assertThat(tokens.render("Thanks,\n{{ signature }}")).isEqualTo("Thanks,\n{{ signature }}");
        assertThat(SequenceTokens.uses("Thanks,\n{{ signature }}", SequenceTokens.SIGNATURE)).isTrue();
        assertThat(SequenceTokens.signedOff("Thanks,\n{{signature}}", "<table/>", "Yara"))
                .isEqualTo("Thanks,\n{{signature}}");
        assertThat(SequenceTokens.signedOff("Thanks,\n{{signature}}", null, "Yara$")).isEqualTo("Thanks,\nYara$");
        assertThat(SequenceTokens.signedOff("Thanks,\n{{signature}}", null, null)).isEqualTo("Thanks,\n");
    }

    @Test
    void aValueCanNeverPlaceTheSignature() {
        SequenceTokens planted = new SequenceTokens("Priya", "{{signature}} CFO", null, null, null, "Yara",
                "Ask {{ signature }} now.", null);

        assertThat(planted.render("{{currentTitle}}. {{opener}}")).isEqualTo("CFO. Ask  now.");
    }

    @Test
    void aMissingValueRendersEmptyAndADollarSignSurvives() {
        SequenceTokens sparse = new SequenceTokens(null, null, null, null, null, null, "Grew revenue to $40m", null);
        assertThat(sparse.render("[{{firstName}}] {{opener}}")).isEqualTo("[] Grew revenue to $40m");
    }

    @Test
    void theFirstNameIsTheFirstWord() {
        assertThat(SequenceTokens.firstNameOf("  Priya   Raman ")).isEqualTo("Priya");
        assertThat(SequenceTokens.firstNameOf(" ")).isNull();
    }
}
