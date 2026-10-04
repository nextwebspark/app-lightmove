package app.lightmove.api.outreach.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A booking link's path: the consultant's name for the reader, and a secret nobody can guess. */
class BookingSlugTest {

    @Test
    @DisplayName("the name, accents stripped, then eight random characters, and never the same twice")
    void theNameAndASecret() {
        assertThat(BookingSlug.from("Zoë Ünal", taken -> false)).matches("zoe-unal-[a-z0-9]{8}");
        assertThat(BookingSlug.from(null, taken -> false)).matches("consultant-[a-z0-9]{8}");

        Set<String> drawn = new HashSet<>();
        for (int draw = 0; draw < 200; draw++) {
            drawn.add(BookingSlug.from("Yara Haddad", taken -> false));
        }
        assertThat(drawn).hasSize(200);
        assertThat(drawn).allMatch(slug -> BookingSlug.SHAPE.matcher(slug).matches());
    }

    @Test
    @DisplayName("a taken draw is drawn again, and a slug is never reused")
    void aTakenDrawIsDrawnAgain() {
        List<String> asked = new ArrayList<>();
        String slug = BookingSlug.from("Yara Haddad", candidate -> asked.add(candidate) && asked.size() < 3);
        assertThat(asked).hasSize(3);
        assertThat(slug).isEqualTo(asked.get(2)).isNotIn(asked.subList(0, 2));

        assertThatThrownBy(() -> BookingSlug.from("Yara Haddad", candidate -> true))
                .isInstanceOf(IllegalStateException.class);
    }
}
