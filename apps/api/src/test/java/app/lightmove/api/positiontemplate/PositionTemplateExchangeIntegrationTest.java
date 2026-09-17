package app.lightmove.api.positiontemplate;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;

/**
 * The template file both ways: an export re-imports as nothing to do, a bad template is shown row by
 * row and stops the whole file, and the published schema names what the importer accepts.
 */
@IntegrationTest
class PositionTemplateExchangeIntegrationTest extends PositionTemplateFlowSupport {

    @Test
    @DisplayName("the library's own export re-imports unchanged — every seeded template passes the rules")
    void libraryExportRoundTrips() throws Exception {
        String admin = superAdmin();

        JsonNode preview = upload(admin, LIBRARY + "/import/preview", download(admin, LIBRARY + "/export"));

        assertThat(preview.get("committed").asBoolean()).isFalse();
        assertThat(preview.get("rows").size()).isGreaterThanOrEqualTo(17);
        for (JsonNode row : preview.get("rows")) {
            assertThat(row.get("action").asText())
                    .describedAs(row.get("code").asText() + " " + row.get("problems"))
                    .isEqualTo("UNCHANGED");
        }
    }

    @Test
    @DisplayName("a firm re-importing its own export forks nothing")
    void workspaceExportRoundTrips() throws Exception {
        Firm firm = firm("Round Trip Firm", "alok");

        JsonNode preview = upload(firm.token(), FIRM_TEMPLATES + "/import/preview",
                download(firm.token(), FIRM_TEMPLATES + "/export"));

        assertThat(actionsOf(preview)).isNotEmpty().containsOnly("UNCHANGED");
    }

    @Test
    @DisplayName("a file with one bad template is previewed row by row and refused whole")
    void invalidFileIsRefusedWhole() throws Exception {
        Firm firm = firm("Careful Firm", "alok");
        String keyword = uniqueKeyword();
        byte[] file = fileOf(
                templateEntry(titleOf(keyword), keyword, "Quills"),
                """
                {"title":"Head of Tax","discipline":"FINANCE","seniority":"N-1",
                 "body":{"department":"Tax","bonusBais":20,"employmentType":"FOREVER"}}
                """,
                """
                {"title":"Head of Duty","discipline":"FINANCE","seniority":"N-1",
                 "body":{"competencies":[{"panel":"TECHNICAL","name":"Duty","weight":90}]}}
                """).getBytes(StandardCharsets.UTF_8);

        JsonNode preview = upload(firm.token(), FIRM_TEMPLATES + "/import/preview", file);
        assertThat(actionsOf(preview)).containsExactly("CREATE", "INVALID", "INVALID");
        assertThat(fieldsOf(preview.get("rows").get(1))).contains("body.bonusBais", "body.employmentType");
        assertThat(fieldsOf(preview.get("rows").get(2))).contains("body.competencies.technical");

        expectRefused(400, "TEMPLATE_IMPORT_INVALID", uploadRaw(firm.token(), FIRM_TEMPLATES + "/import/commit", file));
        assertThat(codesIn(getJson(firm.token(), FIRM_TEMPLATES)))
                .doesNotContain(preview.get("rows").get(0).get("code").asText());
    }

    @Test
    @DisplayName("an import customises the library templates it changes and adds the ones it invents")
    void importCustomisesAndCreates() throws Exception {
        Firm firm = firm("Importing Firm", "alok");
        String keyword = uniqueKeyword();
        byte[] file = fileOf(
                edited(getJson(firm.token(), FIRM_TEMPLATES + "/chief-financial-officer"), "Finance & Strategy", false),
                templateEntry(titleOf(keyword), keyword, "Quills")).getBytes(StandardCharsets.UTF_8);

        JsonNode committed = upload(firm.token(), FIRM_TEMPLATES + "/import/commit", file);

        assertThat(committed.get("committed").asBoolean()).isTrue();
        assertThat(actionsOf(committed)).containsExactly("CUSTOMISE", "CREATE");
        JsonNode list = getJson(firm.token(), FIRM_TEMPLATES);
        assertThat(find(list, "chief-financial-officer").get("origin").asText()).isEqualTo("CUSTOMISED");
        assertThat(find(list, committed.get("rows").get(1).get("code").asText()).get("origin").asText())
                .isEqualTo("OWN");
        assertThat(draftedBrief(firm.token(), "Chief Financial Officer").at("/details/department").asText())
                .isEqualTo("Finance & Strategy");
    }

    @Test
    @DisplayName("an import under an archived library code takes a tracked copy, so a restore cannot be shadowed silently")
    void importUnderAnArchivedLibraryCodeCustomises() throws Exception {
        String admin = superAdmin();
        String keyword = uniqueKeyword();
        JsonNode library = expect(201, postJson(admin, LIBRARY,
                templateRequest(titleOf(keyword), keyword, "Quills", null)));
        String code = library.get("code").asText();
        expect(200, patchJson(admin, LIBRARY + "/" + code + "/active", "{\"active\":false}"));

        Firm firm = firm("Archive Import Firm", "alok");
        JsonNode committed = upload(firm.token(), FIRM_TEMPLATES + "/import/commit",
                fileOf(templateEntry(titleOf(keyword), keyword, "Our Quills")).getBytes(StandardCharsets.UTF_8));

        assertThat(committed.get("rows").get(0).get("code").asText()).isEqualTo(code);
        assertThat(actionsOf(committed)).containsExactly("CUSTOMISE");
        expect(200, patchJson(admin, LIBRARY + "/" + code + "/active", "{\"active\":true}"));
        assertThat(find(getJson(firm.token(), FIRM_TEMPLATES), code).get("origin").asText()).isEqualTo("CUSTOMISED");
    }

    @Test
    @DisplayName("a file that is not a template file is refused before any template is read from it")
    void notATemplateFile() throws Exception {
        Firm firm = firm("Wrong File Firm", "alok");

        expectRefused(400, "TEMPLATE_FILE_UNREADABLE", uploadRaw(firm.token(), FIRM_TEMPLATES + "/import/preview",
                "title,department\nHead of Tax,Tax".getBytes(StandardCharsets.UTF_8)));
        expectRefused(400, "TEMPLATE_FILE_UNREADABLE", uploadRaw(firm.token(), FIRM_TEMPLATES + "/import/preview",
                "{\"format\":\"something-else\",\"templates\":[]}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("the published schema names exactly the vocabulary and the fields the importer accepts")
    void schemaMatchesTheCode() throws Exception {
        JsonNode schema = json.readTree(new String(
                new ClassPathResource("positiontemplate/position-templates.schema.json").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8));
        JsonNode template = schema.at("/$defs/template/properties");
        JsonNode body = schema.at("/$defs/body/properties");

        assertThat(enumOf(template.get("discipline"))).containsExactlyInAnyOrderElementsOf(names(PositionDiscipline.values()));
        assertThat(enumOf(template.get("seniority"))).containsExactlyInAnyOrderElementsOf(names(Seniority.values()));
        assertThat(enumOf(body.get("employmentType"))).containsExactlyInAnyOrderElementsOf(names(EmploymentType.values()));
        assertThat(enumOf(body.get("noticeUnit"))).containsExactlyInAnyOrderElementsOf(names(NoticeUnit.values()));
        assertThat(enumOf(body.get("baseSalaryMode"))).containsExactlyInAnyOrderElementsOf(names(BaseSalaryMode.values()));
        assertThat(enumOf(body.get("bonusBasis"))).containsExactlyInAnyOrderElementsOf(names(BonusBasis.values()));
        assertThat(enumOf(body.get("incentiveType"))).containsExactlyInAnyOrderElementsOf(names(IncentiveType.values()));
        assertThat(enumOf(body.at("/benefits/items/properties/frequency")))
                .containsExactlyInAnyOrderElementsOf(names(BenefitFrequency.values()));
        assertThat(enumOf(body.at("/criteria/items/properties/mode")))
                .containsExactlyInAnyOrderElementsOf(names(CriterionMode.values()));
        assertThat(enumOf(body.at("/competencies/items/properties/panel")))
                .containsExactlyInAnyOrderElementsOf(names(CompetencyPanel.values()));

        Map<?, ?> bodyFields = json.convertValue(body, Map.class);
        assertThat(bodyFields.keySet().stream().map(String::valueOf).toList())
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(PositionTemplateBody.class.getRecordComponents())
                        .map(RecordComponent::getName).toList());
    }

    private static List<String> actionsOf(JsonNode response) {
        List<String> actions = new ArrayList<>();
        for (JsonNode row : response.get("rows")) {
            actions.add(row.get("action").asText());
        }
        return actions;
    }

    private static List<String> fieldsOf(JsonNode row) {
        List<String> fields = new ArrayList<>();
        for (JsonNode problem : row.get("problems")) {
            fields.add(problem.get("field").asText());
        }
        return fields;
    }

    private static List<String> enumOf(JsonNode property) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : property.get("enum")) {
            if (!value.isNull()) {
                values.add(value.asText());
            }
        }
        return values;
    }

    private static List<String> names(Enum<?>[] constants) {
        return Arrays.stream(constants).map(Enum::name).toList();
    }
}
