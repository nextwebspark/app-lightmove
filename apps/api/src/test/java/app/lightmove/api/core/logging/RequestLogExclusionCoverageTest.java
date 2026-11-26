package app.lightmove.api.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cloud Run's request log stores every URL with its query string for 30 days, out of the masker's reach (#1494, F10).
 * {@code ops/gcp/bootstrap.sh} excludes the requests whose query names a free-text parameter. A new text parameter
 * fails here until it is either added to that exclusion or named below as never carrying a person's details.
 */
class RequestLogExclusionCoverageTest {

    /** Text a person types or pastes: a name, an address, a profile link. The Candidates page's q and name too. */
    private static final Set<String> FREE_TEXT = Set.of("q", "search", "executiveQuery", "name", "linkedinUrl");

    /** Picked from a fixed list, or a token whose own route keeps it out of a log some other way. */
    private static final Set<String> NEVER_PERSONAL = Set.of(
            "category", "challenge", "client_id", "code", "cursor", "direction", "error", "executiveStatuses",
            "filter", "group", "outcome", "pipelineStage", "redirect_uri", "scope", "sort", "stage", "state",
            "status", "t", "title", "token", "triageStatus", "tz", "view", "who");

    private static final Pattern EXCLUDED_PARAMETERS = Pattern.compile("\\[\\?&]\\(([A-Za-z_|]+)\\)=");

    @Test
    @DisplayName("every text query parameter is excluded from the request log or known never to carry a person")
    void everyTextParameterIsDecided() throws ClassNotFoundException {
        Set<String> undecided = new TreeSet<>(textQueryParameters());
        undecided.removeAll(FREE_TEXT);
        undecided.removeAll(NEVER_PERSONAL);

        assertThat(undecided).as("add each to FREE_TEXT and bootstrap.sh's search-terms exclusion, or to NEVER_PERSONAL")
                .isEmpty();
    }

    @Test
    @DisplayName("bootstrap.sh's search-terms exclusion names every free-text parameter")
    void theExclusionNamesEveryFreeTextParameter() throws IOException {
        String bootstrap = Files.readString(repositoryRoot().resolve(Path.of("ops", "gcp", "bootstrap.sh")));
        Matcher matcher = EXCLUDED_PARAMETERS.matcher(bootstrap);

        assertThat(matcher.find()).as("the search-terms filter in ops/gcp/bootstrap.sh").isTrue();
        assertThat(Set.of(matcher.group(1).split("\\|"))).isEqualTo(FREE_TEXT);
    }

    private static Set<String> textQueryParameters() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> names = new TreeSet<>();
        for (var definition : scanner.findCandidateComponents("app.lightmove.api")) {
            for (Method method : Class.forName(definition.getBeanClassName()).getDeclaredMethods()) {
                Arrays.stream(method.getParameters())
                        .filter(parameter -> parameter.isAnnotationPresent(RequestParam.class) && isText(parameter))
                        .forEach(parameter -> names.add(queryNameOf(parameter)));
            }
        }
        return names;
    }

    private static boolean isText(Parameter parameter) {
        if (parameter.getType() == String.class || parameter.getType() == String[].class) {
            return true;
        }
        Type type = parameter.getParameterizedType();
        return Collection.class.isAssignableFrom(parameter.getType()) && type instanceof ParameterizedType generic
                && generic.getActualTypeArguments()[0] == String.class;
    }

    private static String queryNameOf(Parameter parameter) {
        RequestParam annotation = parameter.getAnnotation(RequestParam.class);
        if (!annotation.name().isEmpty()) {
            return annotation.name();
        }
        return annotation.value().isEmpty() ? parameter.getName() : annotation.value();
    }

    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(Path.of("apps", "api")))) {
                return dir;
            }
        }
        throw new IllegalStateException("No apps/api above " + Path.of("").toAbsolutePath());
    }
}
