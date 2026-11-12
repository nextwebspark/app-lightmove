package app.lightmove.api.outreach.service;

import app.lightmove.api.core.storage.constant.PhotoShape;
import app.lightmove.api.outreach.model.OutreachMarkdownEmail;
import app.lightmove.api.outreach.model.SignatureContent;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of the costly parts a drawing needs — each is fetched, copied or claimed only when one is shown: the photo in
 * each shape asked for, the logo, the booking link, and the tokens of the signer's own pictures it names.
 */
public record SignatureNeeds(Set<PhotoShape> photoShapes, boolean logo, boolean booking, List<String> pictureTokens) {

    public SignatureNeeds {
        photoShapes = photoShapes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(photoShapes));
        pictureTokens = List.copyOf(pictureTokens);
    }

    public static SignatureNeeds of(SignatureContent content) {
        Set<PhotoShape> photo = content.layout().drawsPhoto() && content.showPhoto()
                ? Set.of(content.photoShape()) : Set.of();
        return new SignatureNeeds(photo, content.layout().drawsLogo() && content.showLogo(), content.showBookingLink(),
                pictureTokensOf(content));
    }

    public static SignatureNeeds ofAll(Collection<SignatureContent> contents) {
        List<SignatureNeeds> each = contents.stream().map(SignatureNeeds::of).toList();
        Set<PhotoShape> shapes = EnumSet.noneOf(PhotoShape.class);
        each.forEach(needs -> shapes.addAll(needs.photoShapes()));
        return new SignatureNeeds(shapes, each.stream().anyMatch(SignatureNeeds::logo),
                each.stream().anyMatch(SignatureNeeds::booking),
                each.stream().flatMap(needs -> needs.pictureTokens().stream()).distinct().toList());
    }

    /** The pictures in its words, then its footer's. */
    static List<String> pictureTokensOf(SignatureContent content) {
        Set<String> tokens = new LinkedHashSet<>(OutreachMarkdownEmail.pictureTokensIn(content.customBody()));
        tokens.addAll(content.footerPictures());
        return List.copyOf(tokens);
    }
}
