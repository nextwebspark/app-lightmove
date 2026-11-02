package app.lightmove.api.core.security.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Who may supply a person's picture — the one place the precedence lives. */
class UserAvatarRulesTest {

    @Test
    @DisplayName("anyone fills an empty picture; afterwards only its own source may replace it")
    void ownership() {
        User user = user();
        assertThat(user.mayTakeAvatarFrom("GOOGLE")).isTrue();

        user.showAvatar("LINKEDIN", "/api/v1/avatars/a");

        assertThat(user.mayTakeAvatarFrom("LINKEDIN")).isTrue();
        assertThat(user.mayTakeAvatarFrom("GOOGLE")).isFalse();
    }

    @Test
    @DisplayName("a picture older than its source column is claimed by the first provider to sign in")
    void legacyPictureIsClaimable() {
        User user = user();
        user.showAvatar(null, "https://cdn.example/legacy.jpg");

        assertThat(user.mayTakeAvatarFrom("GOOGLE")).isTrue();
    }

    @Test
    @DisplayName("the person's own upload, and their removal, are never replaced by a provider")
    void thePersonsChoiceStands() {
        User uploaded = user();
        uploaded.showAvatar(User.UPLOADED_AVATAR_SOURCE, "/api/v1/avatars/mine");
        assertThat(uploaded.mayTakeAvatarFrom("GOOGLE")).isFalse();

        User removed = user();
        removed.showAvatar("GOOGLE", "/api/v1/avatars/theirs");
        removed.removeAvatar();
        assertThat(removed.getAvatarUrl()).isNull();
        assertThat(removed.mayTakeAvatarFrom("GOOGLE")).isFalse();
        assertThat(removed.mayTakeAvatarFrom("LINKEDIN")).isFalse();
    }

    @Test
    @DisplayName("no source may call itself the person, and a missing one supplies nothing")
    void theReservedSource() {
        User user = user();

        assertThat(user.mayTakeAvatarFrom(User.UPLOADED_AVATAR_SOURCE)).isFalse();
        assertThat(user.mayTakeAvatarFrom(null)).isFalse();
    }

    private static User user() {
        return User.registerFederated("alok@firm.example", "Alok Kumar", Instant.now(), "2026-07-01");
    }
}
