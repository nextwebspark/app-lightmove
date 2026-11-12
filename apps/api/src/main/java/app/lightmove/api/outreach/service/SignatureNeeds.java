package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.constant.SignatureLayout;
import app.lightmove.api.outreach.model.OutreachMarkdownEmail;
import app.lightmove.api.outreach.model.SignatureContent;
import java.util.Collection;
import java.util.List;

/**
 * Which of the costly parts a drawing needs — each is fetched, copied or claimed only when one is shown — and the tokens
 * of the Custom signature pictures it names.
 */
public record SignatureNeeds(boolean photo, boolean logo, boolean booking, List<String> pictureTokens) {

    public SignatureNeeds {
        pictureTokens = List.copyOf(pictureTokens);
    }

    public static SignatureNeeds of(SignatureContent content) {
        return new SignatureNeeds(content.layout().drawsPhoto() && content.showPhoto(),
                content.layout().drawsLogo() && content.showLogo(), content.showBookingLink(), pictureTokensOf(content));
    }

    public static SignatureNeeds ofAll(Collection<SignatureContent> contents) {
        return new SignatureNeeds(contents.stream().anyMatch(c -> of(c).photo()),
                contents.stream().anyMatch(c -> of(c).logo()), contents.stream().anyMatch(c -> of(c).booking()),
                contents.stream().flatMap(c -> pictureTokensOf(c).stream()).distinct().toList());
    }

    private static List<String> pictureTokensOf(SignatureContent content) {
        return content.layout() == SignatureLayout.CUSTOM
                ? OutreachMarkdownEmail.pictureTokensIn(content.customBody()) : List.of();
    }
}
