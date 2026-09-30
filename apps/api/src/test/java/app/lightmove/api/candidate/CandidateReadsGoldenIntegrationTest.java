package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubGeocoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The three whole-mandate reads — talent map, export and report — on one seeded mandate, against
 * answers recorded on V91's schema. The final cleanup migration drops V91's frozen copies from the
 * mandate row; it changes what is stored, never what is read, so these files must not move with it.
 *
 * <p>Ids and times differ on every run, so a response is compared after naming each id the fixture
 * created, blanking the rest, and sorting object keys. Run with {@code -Dgolden.record=true} to
 * rewrite the files after a deliberate change to one of the reads.
 */
@IntegrationTest
class CandidateReadsGoldenIntegrationTest extends FlowTestSupport {

    /** The source tree, not target/: a recording is written back where it is committed from. */
    private static final Path GOLDEN = moduleRoot().resolve("src/test/resources/golden/candidate-reads");
    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT_TEXT =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?");
    private static final Pattern DATE_TEXT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String CITY = "Goldenvale";

    @Autowired StubGeocoder geocoder;

    private final Map<String, String> aliases = new LinkedHashMap<>();

    @Test
    @DisplayName("the talent map, the export and the report read as recorded")
    void wholeMandateReadsAreUnchanged() throws Exception {
        geocoder.placeCity(CITY, 24.7136, 46.6753);
        String admin = adminOf("Golden Contract Firm");
        String cfo = mandate(admin, "Chief Financial Officer");
        String treasury = mandate(admin, "Head of Treasury");
        alias(cfo, "project:cfo");
        alias(treasury, "project:treasury");

        String ethnicity = fieldKeyOf(admin, cfo, defineColumn(admin, cfo, "Ethnicity"));
        String almarai = alias(capture(admin, cfo, "Golden Contract Foods", "food & beverages", null), "company:foods");
        String panda = alias(capture(admin, cfo, "Golden Contract Retail", "retail", "shortlisted"), "company:retail");
        alias(capture(admin, cfo, "Golden Contract Declined", "retail", "declined"), "company:declined");

        JsonNode yasmin = candidate(admin, cfo, """
                {"triageCompanyId":"%s","fullName":"Yasmin El-Sayed","title":"CFO","seniority":"C-Suite",
                 "status":"interested","locationCity":"%s","locationCountry":"Saudi Arabia",
                 "linkedinUrl":"https://www.linkedin.com/in/golden-yasmin","email":"yasmin@golden.example",
                 "phone":"+966 50 111 2222","nationality":"saudi arabian","gender":"female","yearsExperience":18,
                 "note":"Board-ready.","customFields":{"%s":"Arab"},
                 "compensation":{"currency":"USD","baseSalary":300000,"allowances":20000,"bonus":50000}}"""
                .formatted(almarai, CITY, ethnicity));
        alias(yasmin.get("id").asText(), "candidate:yasmin@cfo");
        alias(yasmin.get("personId").asText(), "person:yasmin");
        JsonNode omar = candidate(admin, cfo, """
                {"triageCompanyId":"%s","fullName":"Omar Haddad","title":"Finance Director","seniority":"N-1",
                 "locationCity":"Dubai","locationCountry":"UAE","nationality":"Egypt","gender":"male",
                 "compensation":{"currency":"AED","baseSalary":400000}}""".formatted(panda));
        alias(omar.get("id").asText(), "candidate:omar@cfo");
        alias(omar.get("personId").asText(), "person:omar");
        JsonNode lina = candidate(admin, cfo, """
                {"fullName":"Lina Said","employerName":"Somewhere Untriaged","nationality":"Emirati",
                 "locationCountry":"Oman","status":"contacted"}""");
        alias(lina.get("id").asText(), "candidate:lina@cfo");
        alias(lina.get("personId").asText(), "person:lina");

        // The same person on a second mandate, with that mandate's own decision about her.
        JsonNode yasminOnTreasury = candidate(admin, treasury, """
                {"fullName":"Yasmin El-Sayed","linkedinUrl":"https://linkedin.com/in/Golden-Yasmin/",
                 "status":"notInterested","note":"Not for treasury."}""");
        alias(yasminOnTreasury.get("id").asText(), "candidate:yasmin@treasury");
        assertThat(yasminOnTreasury.get("personId").asText()).isEqualTo(yasmin.get("personId").asText());

        mvc.perform(put("/api/v1/projects/" + cfo + "/position/compensation")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD","salaryMin":20000,"salaryMax":25000,"baseSalaryMode":"MONTHLY",
                                 "bonusValue":20,"bonusBasis":"PERCENT_OF_BASE","incentiveType":"LTIP_CASH",
                                 "incentiveAmount":100000,"benefits":[]}"""))
                .andExpect(status().isOk());

        assertGolden("talent-map.json", canonicalJson(read(admin, "/api/v1/projects/" + cfo + "/talent-map")));
        assertGolden("report.json", canonicalJson(read(admin, "/api/v1/projects/" + cfo + "/report")));
        assertGolden("export-universe.csv", scrub(read(admin, "/api/v1/projects/" + cfo + "/export/companies")));
        assertGolden("export-shortlisted.csv",
                scrub(read(admin, "/api/v1/projects/" + cfo + "/export/companies?status=shortlisted")));
    }

    /** apps/api, found from target/test-classes so an IDE started anywhere reads the same files. */
    private static Path moduleRoot() {
        try {
            return Path.of(CandidateReadsGoldenIntegrationTest.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).getParent().getParent();
        } catch (java.net.URISyntaxException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

        private String alias(String id, String name) {
        aliases.put(id, "<" + name + ">");
        return id;
    }

    private String canonicalJson(String body) throws Exception {
        JsonNode sorted = sortKeys(json.readTree(scrub(body)));
        return json.writer().with(SerializationFeature.INDENT_OUTPUT).writeValueAsString(sorted) + "\n";
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            Map<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(field -> fields.put(field.getKey(), sortKeys(field.getValue())));
            ObjectNode sorted = JsonNodeFactory.instance.objectNode();
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = JsonNodeFactory.instance.arrayNode();
            node.forEach(element -> sorted.add(sortKeys(element)));
            return sorted;
        }
        return node;
    }

    /** Names the fixture's ids, blanks every other id and every time, and takes the run's domain out. */
    private String scrub(String text) {
        Matcher ids = UUID_TEXT.matcher(text.replace("﻿", ""));
        StringBuilder named = new StringBuilder();
        while (ids.find()) {
            ids.appendReplacement(named, Matcher.quoteReplacement(aliases.getOrDefault(ids.group(), "<id>")));
        }
        ids.appendTail(named);
        String timeless = INSTANT_TEXT.matcher(named).replaceAll("<instant>");
        return DATE_TEXT.matcher(timeless).replaceAll("<date>").replace(domain, "<domain>");
    }

    private static void assertGolden(String file, String actual) throws IOException {
        Path golden = GOLDEN.resolve(file);
        if (Boolean.getBoolean("golden.record")) {
            Files.createDirectories(GOLDEN);
            Files.writeString(golden, actual, StandardCharsets.UTF_8);
            return;
        }
        assertThat(golden).as("no recording for %s; run with -Dgolden.record=true", file).exists();
        assertThat(actual).as("%s drifted from its recording", file)
                .isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
    }

    private String read(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String adminOf(String firmName) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), firmName);
        return login(address);
    }

    private String mandate(String token, String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s Unit"}""".formatted(positionTitle)))
                .andReturn()).get("id").asText();
        alias(clientId, "client:" + positionTitle);
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String capture(String token, String projectId, String name, String industry, String stage)
            throws Exception {
        String stageField = stage == null ? "" : ",\"status\":\"" + stage + "\"";
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s","industry":"%s","companyCity":"%s","companyCountry":"Saudi Arabia"%s}"""
                                .formatted(name, industry, CITY, stageField)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode candidate(String token, String projectId, String body) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String defineColumn(String token, String projectId, String label) throws Exception {
        String id = body(mvc.perform(post("/api/v1/projects/" + projectId + "/custom-columns")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"candidate","label":"%s","dataType":"text"}""".formatted(label)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        alias(id, "column:" + label);
        return id;
    }

    private String fieldKeyOf(String token, String projectId, String columnId) throws Exception {
        JsonNode columns = body(mvc.perform(get("/api/v1/projects/" + projectId + "/custom-columns")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()).get("columns");
        for (JsonNode column : columns) {
            if (column.get("id").asText().equals(columnId)) {
                return column.get("fieldKey").asText();
            }
        }
        throw new AssertionError("no column " + columnId);
    }
}
