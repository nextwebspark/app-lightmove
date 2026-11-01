package app.lightmove.api.outreach.model;

/** A provider's answer to whether it accepts an app's keys; {@code refusedWith} is its code when it does not. */
public record AppKeysVerdict(String refusedWith) {

    public static final AppKeysVerdict ACCEPTED = new AppKeysVerdict(null);

    private static final int LONGEST_CODE = 100;

    /** Entra names its rule after the OAuth code ({@code invalid_client AADSTS7000215}); the rule is the useful half. */
    public static AppKeysVerdict refused(String named) {
        String code = named == null || named.isBlank() ? "refused" : named.strip();
        int lastSpace = code.lastIndexOf(' ');
        code = lastSpace < 0 ? code : code.substring(lastSpace + 1);
        return new AppKeysVerdict(code.length() > LONGEST_CODE ? code.substring(0, LONGEST_CODE) : code);
    }

    public boolean accepted() {
        return refusedWith == null;
    }
}
