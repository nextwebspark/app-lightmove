package app.lightmove.api.core.security.avatar;

import java.util.regex.Pattern;

/** A stored photo's link: relative, so the same in every deployment; the extension prefixes its own origin. */
public final class AvatarLinks {

    static final String PREFIX = "/api/v1/avatars/";

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private AvatarLinks() {
    }

    static String linkOf(String token) {
        return PREFIX + token;
    }

    static boolean isWellFormedToken(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }
}
