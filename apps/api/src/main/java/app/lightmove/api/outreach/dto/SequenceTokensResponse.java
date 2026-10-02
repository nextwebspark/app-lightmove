package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.SequenceTokens;

/** The values the server fills a person's tokens with, so the review shows exactly what Start freezes. */
public record SequenceTokensResponse(String firstName, String currentTitle, String currentCompany,
                                     String positionTitle, String location, String senderFirstName) {

    public static SequenceTokensResponse of(SequenceTokens tokens) {
        return new SequenceTokensResponse(tokens.firstName(), tokens.currentTitle(), tokens.currentCompany(),
                tokens.positionTitle(), tokens.location(), tokens.senderFirstName());
    }
}
