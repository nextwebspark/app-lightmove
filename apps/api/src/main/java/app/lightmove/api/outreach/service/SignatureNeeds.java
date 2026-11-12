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
        List<SignatureNeeds> each = contents.stream().map(SignatureNeeds::of).toList();
        return new SignatureNeeds(each.stream().anyMatch(SignatureNeeds::photo),
                each.stream().anyMatch(SignatureNeeds::logo), each.stream().anyMatch(SignatureNeeds::booking),
                each.stream().flatMap(needs -> needs.pictureTokens().stream()).distinct().toList());
    }

    private static List<String> pictureTokensOf(SignatureContent content) {
        return content.layout() == SignatureLayout.CUSTOM
                ? OutreachMarkdownEmail.pictureTokensIn(content.customBody()) : List.of();
    }
}
