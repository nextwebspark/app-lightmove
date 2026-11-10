package app.lightmove.api.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #927: the audit trail is append-only, so a person's name or address written there outlives their erasure. Every
 * literal detail key that sounds like one must be reviewed here, for the classes it was reviewed in — so a key that
 * is fine for a company (a {@code name}) cannot quietly be reused for a person in another class.
 */
class AuditDetailKeysTest {

    private static final Pattern DETAIL_KEY = Pattern.compile("\\.detail(?:IfPresent)?\\(\\s*\"([^\"]+)\"");

    private static final Pattern SOUNDS_PERSONAL = Pattern.compile(
            "(?i)email|name|phone|linkedin|slug|address|file|title|label|domain|url|contact|recipient|sender");

    /** Each key, the classes it was reviewed in, and why it names nobody. */
    private static final Map<String, Set<String>> REVIEWED = Map.ofEntries(
            // An OAuth client's own name for itself, as its registration sent it.
            Map.entry("clientName", Set.of("OAuthRegisteredClients")),
            // A company's name: a business, not a person.
            Map.entry("companyName", Set.of("TriageCompanyService")),
            // A number of emails a day.
            Map.entry("dailyEmailCap", Set.of("AvailabilityService")),
            // The domain of a work address alone, which names the firm and nobody in it.
            Map.entry("domain", Set.of("AuthenticationService", "OAuth2LoginSuccessHandler", "OnboardingService")),
            // A boolean.
            Map.entry("emailVerifiedByReset", Set.of("PasswordResetService")),
            // A position description's and an imported spreadsheet's file name — never a CV's, whose name is its
            // person's (PersonDocumentService records the document's id).
            Map.entry("fileName", Set.of("PositionDocumentService", "ProjectImportService")),
            // Counts.
            Map.entry("filed", Set.of("ExecutiveSourcingWorker")),
            Map.entry("files", Set.of("WorkspacePurge")),
            // A client's, a workspace's or a saved search's name: the firm's records, not people.
            Map.entry("name", Set.of("ClientRegistrar", "ClientService", "OnboardingService", "StrategySearchService",
                    "WorkspaceSettingsService")),
            // "true" when a catalog entry was renamed; never the label.
            Map.entry("renamed", Set.of("CandidateTagService", "TaskPresetService")),
            // A workspace's slug.
            Map.entry("slug", Set.of("OnboardingService")),
            // A pipeline status's label, the workspace's own vocabulary.
            Map.entry("statusLabel", Set.of("CandidateService")));

    @Test
    @DisplayName("every audit detail key that sounds personal has been reviewed for the class that writes it")
    void personalSoundingKeysAreReviewed() throws IOException {
        Map<String, Set<String>> unreviewed = new TreeMap<>();
        Map<String, Set<String>> written = detailKeysByClass();
        written.forEach((key, classes) -> {
            if (!SOUNDS_PERSONAL.matcher(key).find()) {
                return;
            }
            Set<String> allowed = REVIEWED.getOrDefault(key, Set.of());
            classes.stream().filter(owner -> !allowed.contains(owner))
                    .forEach(owner -> unreviewed.computeIfAbsent(key, ignored -> new TreeSet<>()).add(owner));
        });

        assertThat(written).as("the scan found the detail keys it looks for").containsKey("candidateId");
        assertThat(unreviewed)
                .as("audit detail keys that may hold a person's name or contact: record an id instead, or add the "
                        + "key and class to REVIEWED with why it names nobody")
                .isEmpty();
    }

    private static Map<String, Set<String>> detailKeysByClass() throws IOException {
        Map<String, Set<String>> keys = new TreeMap<>();
        try (Stream<Path> sources = Files.walk(mainSources())) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String owner = source.getFileName().toString().replace(".java", "");
                Matcher found = DETAIL_KEY.matcher(Files.readString(source));
                while (found.find()) {
                    keys.computeIfAbsent(found.group(1), ignored -> new TreeSet<>()).add(owner);
                }
            }
        }
        return keys;
    }

    private static Path mainSources() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path sources = dir.resolve(Path.of("apps", "api", "src", "main", "java"));
            if (Files.isDirectory(sources)) {
                return sources;
            }
        }
        throw new IllegalStateException("No apps/api/src/main/java above " + Path.of("").toAbsolutePath());
    }
}
