package app.lightmove.api.outreach.model;

/**
 * What a consultant's signatures follow where they override nothing: their profile, their workspace, the mailbox they
 * send from and their booking link. {@code photoUrl}, {@code logo} and {@code bookingLink} are null where there is none;
 * a photo or logo is always one Uncava serves, never another host's.
 */
public record SignatureSender(String name, String title, String phone, String linkedinUrl, String email,
                              String company, String website, String photoUrl, SignatureLogo logo, String bookingLink) {}
