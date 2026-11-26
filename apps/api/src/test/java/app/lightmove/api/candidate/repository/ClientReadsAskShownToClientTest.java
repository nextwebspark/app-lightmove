package app.lightmove.api.candidate.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

/**
 * A client read filters on {@code shown_to_client} (V246, #1488), which also hides someone restricted or refusing to be
 * shared; staff's own {@code client_visible} switch alone would let them back in.
 */
class ClientReadsAskShownToClientTest {

    private static final Pattern THE_SWITCH = Pattern.compile("\\b(client_visible|clientVisible)\\b");

    @Test
    @DisplayName("no candidate query filters a client's read on staff's switch alone")
    void noQueryReadsTheSwitchAlone() {
        List<String> offenders = Arrays.stream(CandidateRepository.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Query.class))
                .filter(method -> readsTheSwitch(method.getAnnotation(Query.class).value()))
                .map(Method::getName)
                .toList();
        List<String> derived = Arrays.stream(CandidateRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.contains("ClientVisibleTrue") || name.contains("ClientVisiblePerson"))
                .toList();

        assertThat(offenders).isEmpty();
        assertThat(derived).isEmpty();
    }

    private static boolean readsTheSwitch(String query) {
        return THE_SWITCH.matcher(query).find();
    }
}
