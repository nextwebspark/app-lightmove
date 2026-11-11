package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void aFallbackStandsInOnlyWhereThePersonHasNoValue() {
        SequenceTokens sparse = new SequenceTokens(null, "CFO", null, null, null, "Yara", null, null);
        assertThat(sparse.render("Hi {{firstName|there}}, as {{ currentTitle | our lead }}"))
                .isEqualTo("Hi there, as CFO");
        assertThat(sparse.render("Hi {{firstName|  dear   reader }}")).isEqualTo("Hi dear reader");
        assertThat(tokens.render("Hi {{firstName|there}}")).isEqualTo("Hi Priya");
    }

    @Test
    void anEscapedPipeIsAFallbackAsWell() {
        SequenceTokens sparse = new SequenceTokens(null, null, null, null, null, null, null, null);
        assertThat(sparse.render("Hi {{firstName\\|there}}")).isEqualTo("Hi there");
        assertThat(SequenceTokens.usesIn("{{firstName\\|there}}"))
                .containsExactly(new TokenUse("firstName", "there"));
    }

    @Test
    void aFallbackInMarkdownIsEscapedAsAValueIs() {
        SequenceTokens sparse = new SequenceTokens(null, null, null, null, null, null, null, null);
        assertThat(sparse.renderMarkdown("Hi {{firstName|*friend*}}")).isEqualTo("Hi \\*friend\\*");
        assertThat(new SequenceTokens("[Priya](x)", null, null, null, null, null, null, null)
                .renderMarkdown("Hi {{firstName|there}}")).isEqualTo("Hi \\[Priya\\](x)");
    }

    @Test
    void aFallbackSavedFromTheEditorLosesItsMarkdownEscapes() {
        SequenceTokens sparse = new SequenceTokens(null, null, null, null, null, null, null, null);
        String saved = "Dear {{currentTitle\\|a \\*senior\\* leader}}";
        assertThat(sparse.render(saved)).isEqualTo("Dear a *senior* leader");
        assertThat(sparse.renderMarkdown(saved)).isEqualTo("Dear a \\*senior\\* leader");
        assertThat(SequenceTokens.usesIn(saved)).containsExactly(new TokenUse("currentTitle", "a *senior* leader"));
    }

    @Test
    void aBookingLinksFallbackStandsInWhereThereIsNoLink() {
        SequenceTokens noLink = new SequenceTokens("Priya", null, null, null, null, null, null, null);
        assertThat(noLink.render("Book: {{bookingLink|reply to this email}}")).isEqualTo("Book: reply to this email");
        assertThat(SequenceTokens.emailVocabulary(false)).doesNotContain(SequenceTokens.BOOKING_LINK);
        assertThat(SequenceTokens.emailVocabulary(true)).containsAll(SequenceTokens.PROFILE_TOKENS)
                .contains(SequenceTokens.BOOKING_LINK).doesNotContain(SequenceTokens.OPENER, SequenceTokens.SIGNATURE);
    }

    @Test
    void aLineKeepsEveryValueOnOneLine() {
        SequenceTokens multiline = new SequenceTokens("Priya", "Chief\nFinancial   Officer", null, null, null, null,
                null, null);
        assertThat(multiline.renderLine("{{firstName}}: {{currentTitle}} at {{currentCompany|your firm}}"))
                .isEqualTo("Priya: Chief Financial Officer at your firm");
    }

    @Test
    void reportsUnknownAndUnfilledTokensInOrderOfFirstUse() {
        String text = "Hi {{firstName}} {{fristName}} {{location}} {{location|here}} {{opener}} {{fristName}}";
        assertThat(SequenceTokens.unknownIn(text, SequenceTokens.PROFILE_TOKENS)).containsExactly("fristName", "opener");
        SequenceTokens sparse = new SequenceTokens("Priya", null, null, null, null, null, null, null);
        assertThat(sparse.unfilledIn(text)).containsExactly("location", "opener");
        assertThat(sparse.unfilledIn("{{location|here}}")).isEqualTo(List.of());
    }

    @Test
    void aBraceOrAPipeInsideAFallbackIsNoToken() {
        assertThat(tokens.render("{{firstName|a|b}} {{firstName|{x}}}")).isEqualTo("{{firstName|a|b}} {{firstName|{x}}}");
    }
}
