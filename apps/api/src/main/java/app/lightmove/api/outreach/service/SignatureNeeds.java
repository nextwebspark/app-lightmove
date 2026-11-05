package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.SignatureContent;
import java.util.Collection;

/** Which of the costly parts a drawing needs — each is fetched, copied or claimed only when one is shown. */
public record SignatureNeeds(boolean photo, boolean logo, boolean booking) {

    public static SignatureNeeds of(SignatureContent content) {
        return new SignatureNeeds(content.layout().drawsPhoto() && content.showPhoto(),
                content.layout().drawsLogo() && content.showLogo(), content.showBookingLink());
    }

    public static SignatureNeeds ofAll(Collection<SignatureContent> contents) {
        return new SignatureNeeds(contents.stream().anyMatch(c -> of(c).photo()),
                contents.stream().anyMatch(c -> of(c).logo()), contents.stream().anyMatch(c -> of(c).booking()));
    }
}
