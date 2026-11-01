package app.lightmove.api.core.security.avatar;

import java.util.regex.Pattern;

/**
 * The link {@code app_lm_user.avatar_url} holds for a stored photo. Relative, so it is the same in every deployment and
 * every copy of the database; the SPA shares the API's origin, and the extension prefixes its own.
 */
public final class AvatarLinks {

    static final String PREFIX = "/api/v1/avatars/";

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private AvatarLinks() {
    }

    static String linkOf(String token) {
        return PREFIX + token;
    }

    /** Whether the link is one of ours, as opposed to a provider's CDN link stored before V169. */
    public static boolean isStored(String avatarUrl) {
        return avatarUrl != null && avatarUrl.startsWith(PREFIX);
    }

    static boolean isWellFormedToken(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }
}
