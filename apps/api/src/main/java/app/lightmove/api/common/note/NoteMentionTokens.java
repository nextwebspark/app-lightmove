package app.lightmove.api.common.note;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mentions a note's body carries, as the composer writes them: {@code @[Full Name](user:<uuid>)} for one
 * teammate, {@code @[team](team)} for the position's whole team. Only the id is trusted; the name in brackets
 * is what the writer saw, kept so the body still reads where the token is not drawn.
 */
public record NoteMentionTokens(Set<UUID> userIds, boolean team) {

    private static final Pattern TOKEN = Pattern.compile(
            "@\\[([^\\]\\n]{1,120})]\\((?:user:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})|(team))\\)");

    public static NoteMentionTokens of(String body) {
        Set<UUID> ids = new LinkedHashSet<>();
        boolean team = false;
        if (body != null) {
            Matcher matcher = TOKEN.matcher(body);
            while (matcher.find()) {
                if (matcher.group(3) != null) {
                    team = true;
                } else {
                    ids.add(UUID.fromString(matcher.group(2)));
                }
            }
        }
        return new NoteMentionTokens(Set.copyOf(ids), team);
    }

    /** The body as plain words, each token read as {@code @Name}. */
    public static String plainTextOf(String body) {
        return body == null ? null : TOKEN.matcher(body).replaceAll(match -> Matcher.quoteReplacement(
                "@" + match.group(1)));
    }

    public boolean isEmpty() {
        return userIds.isEmpty() && !team;
    }
}
