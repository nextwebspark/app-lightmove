package app.lightmove.api.outreach.model;

/**
 * What one signature says instead of the profile or the workspace. A null field is not overridden: it follows its source
 * when the signature is drawn, so a profile edit reaches every signature. {@code address} and {@code mobile} have no
 * source and are the signature's alone.
 */
public record SignatureFields(String name, String title, String phone, String linkedinUrl, String company,
                              String website, String address, String mobile) {

    public static final SignatureFields NONE = new SignatureFields(null, null, null, null, null, null, null, null);

    public SignatureFields(String name, String title, String phone, String linkedinUrl, String company, String website,
                           String address) {
        this(name, title, phone, linkedinUrl, company, website, address, null);
    }
}
