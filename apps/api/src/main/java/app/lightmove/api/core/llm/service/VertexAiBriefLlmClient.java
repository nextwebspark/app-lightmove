package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.model.BriefExtraction;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Calls Gemini on Vertex AI with a JSON response schema, so the model returns a parseable
 * {@link BriefExtraction} directly rather than free text. Authenticates via Application Default
 * Credentials — the same {@code gcloud auth application-default login} identity already used for
 * Cloud SQL, so a developer who has that set up needs no second credential to try the real provider.
 *
 * <p>No request timeout is set on the underlying client here; verify {@code com.google.genai}'s
 * {@code HttpOptions} surface against the SDK version pinned in {@code pom.xml} before relying on this
 * in production — a hung Vertex AI call would otherwise hold the upload request thread indefinitely.
 */
@Slf4j
public class VertexAiBriefLlmClient implements BriefLlmClient {

    private static final Schema RESPONSE_SCHEMA = buildSchema();

    private final Client client;
    private final String model;
    private final ObjectMapper json;

    public VertexAiBriefLlmClient(String projectId, String location, String model, ObjectMapper json) {
        this.client = Client.builder().vertexAI(true).project(projectId).location(location).build();
        this.model = model;
        this.json = json;
    }

    @Override
    public BriefExtraction extractBrief(String documentText, String positionTitle) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .build();

        log.info("Calling Vertex AI model {} to extract a brief for '{}' from {} characters of document text",
                model, positionTitle, documentText.length());

        GenerateContentResponse response;
        try {
            response = client.models.generateContent(model, prompt(documentText, positionTitle), config);
        } catch (RuntimeException e) {
            log.warn("Vertex AI call failed extracting a brief for '{}'", positionTitle, e);
            throw new ApiException(ErrorCode.BRIEF_EXTRACTION_FAILED, "Vertex AI call failed");
        }

        try {
            BriefExtraction extraction = json.readValue(response.text(), BriefExtraction.class);
            log.info("Vertex AI returned a brief for '{}': {} criteria, {} technical and {} behavioural "
                            + "competencies extracted",
                    positionTitle, extraction.criteria().size(), extraction.technical().size(),
                    extraction.behavioural().size());
            return extraction;
        } catch (RuntimeException e) {
            log.warn("Could not parse Vertex AI's response as a BriefExtraction for '{}'", positionTitle, e);
            throw new ApiException(ErrorCode.BRIEF_EXTRACTION_FAILED, "Unparseable LLM response");
        }
    }

    private static String prompt(String documentText, String positionTitle) {
        return """
                You are reading a position description document for an executive search mandate titled
                "%s". Extract every field you can find into the JSON schema you have been given.
                Leave a field null (or an empty array) when the document does not say — never guess or
                invent a value. Weight the technical and behavioural competency lists so each list's
                weights sum to 100 if the document gives enough signal to do so.

                DOCUMENT:
                %s
                """.formatted(positionTitle, documentText);
    }

    private static Schema buildSchema() {
        Schema criterion = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "text", Schema.builder().type(Type.Known.STRING).build(),
                        "mode", Schema.builder().type(Type.Known.STRING)
                                .enum_(List.of("REQUIRED", "PREFERRED")).build()))
                .build();

        Schema competency = Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.of(
                        "name", Schema.builder().type(Type.Known.STRING).build(),
                        "weight", Schema.builder().type(Type.Known.INTEGER).build()))
                .build();

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(Map.ofEntries(
                        Map.entry("mandateReason", Schema.builder().type(Type.Known.STRING)
                                .enum_(List.of("NEW_ROLE", "BACKFILL", "SUCCESSION", "RESTRUCTURING")).build()),
                        Map.entry("internalContext", Schema.builder().type(Type.Known.STRING).build()),
                        Map.entry("narrative", Schema.builder().type(Type.Known.STRING).build()),
                        Map.entry("reportsTo", Schema.builder().type(Type.Known.STRING).build()),
                        Map.entry("directReports", Schema.builder().type(Type.Known.INTEGER).build()),
                        Map.entry("teamSize", Schema.builder().type(Type.Known.INTEGER).build()),
                        Map.entry("location", Schema.builder().type(Type.Known.STRING).build()),
                        Map.entry("employmentType", Schema.builder().type(Type.Known.STRING)
                                .enum_(List.of("FULL_TIME_PERMANENT", "FIXED_TERM_CONTRACT", "PART_TIME",
                                        "INTERIM", "RETAINED_ADVISORY")).build()),
                        Map.entry("salaryMin", Schema.builder().type(Type.Known.INTEGER).build()),
                        Map.entry("salaryMax", Schema.builder().type(Type.Known.INTEGER).build()),
                        Map.entry("currency", Schema.builder().type(Type.Known.STRING).build()),
                        Map.entry("noticeValue", Schema.builder().type(Type.Known.INTEGER).build()),
                        Map.entry("noticeUnit", Schema.builder().type(Type.Known.STRING)
                                .enum_(List.of("WEEKS", "MONTHS")).build()),
                        Map.entry("bonusTargetPct", Schema.builder().type(Type.Known.INTEGER).build()),
                        Map.entry("ltip", Schema.builder().type(Type.Known.STRING).build()),
                        Map.entry("benefits", Schema.builder().type(Type.Known.ARRAY)
                                .items(Schema.builder().type(Type.Known.STRING).build()).build()),
                        Map.entry("criteria", Schema.builder().type(Type.Known.ARRAY).items(criterion).build()),
                        Map.entry("technical", Schema.builder().type(Type.Known.ARRAY).items(competency).build()),
                        Map.entry("behavioural", Schema.builder().type(Type.Known.ARRAY).items(competency).build())))
                .build();
    }
}
