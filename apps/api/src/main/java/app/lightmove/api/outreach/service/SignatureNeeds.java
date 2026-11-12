package app.lightmove.api.outreach.service;

import app.lightmove.api.core.storage.constant.PictureShape;
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
public record SignatureNeeds(Set<PictureShape> photoShapes, boolean logo, boolean booking, List<String> pictureKeys) {

    public SignatureNeeds {
        photoShapes = Set.copyOf(photoShapes);
        pictureKeys = List.copyOf(pictureKeys);
    }

    public static SignatureNeeds of(SignatureContent content) {
        Set<PictureShape> photo = content.layout().drawsPhoto() && content.showPhoto()
                ? Set.of(content.photoShape()) : Set.of();
        return new SignatureNeeds(photo, content.layout().drawsLogo() && content.showLogo(), content.showBookingLink(),
                pictureKeysOf(content));
    }

    public static SignatureNeeds ofAll(Collection<SignatureContent> contents) {
        List<SignatureNeeds> each = contents.stream().map(SignatureNeeds::of).toList();
        Set<PictureShape> shapes = EnumSet.noneOf(PictureShape.class);
        each.forEach(needs -> shapes.addAll(needs.photoShapes()));
        return new SignatureNeeds(shapes, each.stream().anyMatch(SignatureNeeds::logo),
                each.stream().anyMatch(SignatureNeeds::booking),
                each.stream().flatMap(needs -> needs.pictureKeys().stream()).distinct().toList());
    }

    /** The pictures in its words, then its footer's, each in the shape it is drawn in. */
    static List<String> pictureKeysOf(SignatureContent content) {
        Set<String> keys = new LinkedHashSet<>(OutreachMarkdownEmail.pictureKeysIn(content.customBody()));
        content.footerPictures().forEach(picture -> keys.add(picture.key()));
        return List.copyOf(keys);
    }
}
