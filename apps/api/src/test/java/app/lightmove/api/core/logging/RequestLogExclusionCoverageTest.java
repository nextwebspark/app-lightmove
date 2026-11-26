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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cloud Run's request log keeps every URL with its query string for 30 days, out of the masker's reach (#1494, F10).
 * {@code ops/gcp/bootstrap.sh} drops the requests whose query names {@link #FREE_TEXT} or {@link #SECRET_IN_QUERY};
 * a new text parameter fails here until it is put in one of those or in {@link #SAFE_TO_LOG}.
 */
class RequestLogExclusionCoverageTest {

    /** Text a person types, pastes or captures: a name, an address, a profile link. */
    private static final Set<String> FREE_TEXT = Set.of("q", "search", "executiveQuery", "name", "linkedinUrl");

    /**
     * A raw token: an invitation's, and the verification and reset links' (the SPA page load carries them), and the
     * Recall live webhook's shared secret. Tokens are never stored raw, the request log included.
     */
    private static final Set<String> SECRET_IN_QUERY = Set.of("token", "t");

    /**
     * Picked from a fixed list, an id, or a value public by design. {@code code} is an OAuth authorization code: single
     * use, redeemed within seconds and useless without the client's secret or PKCE verifier.
     */
    private static final Set<String> SAFE_TO_LOG = Set.of(
            "activity", "added", "category", "challenge", "client_id", "code", "company", "contact", "country",
            "cursor", "direction", "doNotContact", "error", "executiveStatuses", "experience", "filter", "group",
            "linkedin", "outcome", "owner", "pipelineStage", "position", "redirect_uri", "scope", "seniority", "sort",
            "source", "stage", "state", "status", "tag", "tagMatch", "title", "triageStatus", "tz", "view", "who");

    /**
     * A handler that takes its query as a map reads the keys itself, so the keys are read off the class that does it.
     * Each map-taking handler must be listed here, by its controller's simple name.
     */
    private static final Map<String, Path> RAW_QUERY_READERS = Map.of(
            "CandidatePoolController", sourceOf("candidate/model/PoolCriteria.java"),
            "CandidatePoolExportController", sourceOf("candidate/model/PoolCriteria.java"));

    private static final Pattern KEY_READ = Pattern.compile("params\\.(?:get|getFirst)\\(\"([A-Za-z_]+)\"\\)");
    private static final Pattern EXCLUDED_PARAMETERS = Pattern.compile("\\[\\?&]\\(([A-Za-z_|]+)\\)=");

    @Test
    @DisplayName("every text query parameter is excluded from the request log or known safe to log")
    void everyTextParameterIsDecided() throws ClassNotFoundException, IOException {
        Set<String> undecided = new TreeSet<>(textQueryParameters());
        undecided.removeAll(FREE_TEXT);
        undecided.removeAll(SECRET_IN_QUERY);
        undecided.removeAll(SAFE_TO_LOG);

        assertThat(undecided)
                .as("add each to FREE_TEXT or SECRET_IN_QUERY and bootstrap.sh's exclusion, or to SAFE_TO_LOG")
                .isEmpty();
    }

    @Test
    @DisplayName("bootstrap.sh's exclusion names every free-text and secret parameter, and nothing else")
    void theExclusionNamesEveryExcludedParameter() throws IOException {
        String bootstrap = Files.readString(repositoryRoot().resolve(Path.of("ops", "gcp", "bootstrap.sh")));
        Matcher matcher = EXCLUDED_PARAMETERS.matcher(bootstrap);

        assertThat(matcher.find()).as("the query-terms filter in ops/gcp/bootstrap.sh").isTrue();
        assertThat(Set.of(matcher.group(1).split("\\|")))
                .isEqualTo(Stream.concat(FREE_TEXT.stream(), SECRET_IN_QUERY.stream()).collect(Collectors.toSet()));
    }

    @Test
    @DisplayName("the Candidates page's raw query reader is seen, so its keys are checked too")
    void rawQueryKeysAreRead() throws IOException {
        assertThat(keysReadBy(sourceOf("candidate/model/PoolCriteria.java"))).contains("q", "name", "country");
    }

    private static Set<String> textQueryParameters() throws ClassNotFoundException, IOException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Set<String> names = new TreeSet<>();
        for (var definition : scanner.findCandidateComponents("app.lightmove.api")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            for (Method method : controller.getMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotationPresent(RequestParam.class)) {
                        continue;
                    }
                    if (Map.class.isAssignableFrom(parameter.getType())) {
                        Path reader = RAW_QUERY_READERS.get(controller.getSimpleName());
                        assertThat(reader).as("%s.%s reads its query as a map: add it to RAW_QUERY_READERS",
                                controller.getSimpleName(), method.getName()).isNotNull();
                        names.addAll(keysReadBy(reader));
                    } else if (isText(parameter)) {
                        names.add(queryNameOf(parameter));
                    }
                }
            }
        }
        return names;
    }

    private static Set<String> keysReadBy(Path source) throws IOException {
        Matcher matcher = KEY_READ.matcher(Files.readString(source));
        Set<String> keys = new TreeSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    private static boolean isText(Parameter parameter) {
        Class<?> type = parameter.getType();
        if (type == String.class || type == String[].class) {
            return true;
        }
        boolean wrapper = Collection.class.isAssignableFrom(type) || type == Optional.class;
        Type generic = parameter.getParameterizedType();
        return wrapper && generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] == String.class;
    }

    private static String queryNameOf(Parameter parameter) {
        RequestParam annotation = parameter.getAnnotation(RequestParam.class);
        if (!annotation.name().isEmpty()) {
            return annotation.name();
        }
        return annotation.value().isEmpty() ? parameter.getName() : annotation.value();
    }

    private static Path sourceOf(String pathUnderApi) {
        return repositoryRoot().resolve(Path.of("apps", "api", "src", "main", "java", "app", "lightmove", "api"))
                .resolve(pathUnderApi);
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
