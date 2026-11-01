package app.lightmove.api.outreach.dto;

/**
 * What a signature's fields follow where they override nothing, so the editor can say so: the caller's profile, their
 * workspace and the mailbox they send from. {@code photoUrl} is set only for a photo they uploaded.
 */
public record SignatureSourcesResponse(
        String name,
        String title,
        String phone,
        String linkedinUrl,
        String email,
        String company,
        String website,
        String photoUrl,
        boolean logoAvailable,
        boolean bookingLinkOffered) {}
