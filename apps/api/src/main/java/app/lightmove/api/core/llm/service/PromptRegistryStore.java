package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.AiProvider;
import app.lightmove.api.core.llm.constant.FinishKind;
import app.lightmove.api.core.llm.constant.PromptOrigin;
import app.lightmove.api.core.llm.model.BundledPrompt;
import app.lightmove.api.core.llm.model.GenerationSettings;
import app.lightmove.api.core.llm.model.LivePrompt;
import app.lightmove.api.core.llm.model.ProductionVersion;
import app.lightmove.api.core.llm.model.PromptCallCount;
import app.lightmove.api.core.llm.model.PromptVersion;
import app.lightmove.api.core.llm.model.RecordedBundledPrompt;
import app.lightmove.api.core.llm.model.ServedPrompt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** The prompt registry's tables (V235): versions, the production label and its moves, and the daily call counts. */
@Repository
@RequiredArgsConstructor
public class PromptRegistryStore {

    public static final String PRODUCTION = "production";

    private static final String SYNC_LOCK = "prompt-registry-sync";

    private static final String VERSION_COLUMNS = """
            SELECT v.prompt_id, v.version, v.origin, v.provider, v.model_id, v.settings::text AS settings,
                   v.change_note, v.created_at, author.full_name AS author_name
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    /**
     * The production versions that override a build's own file: one edited in the console, or a shipped one a person
     * put live (a rollback to an older release's text). A shipped version a release's sync moved to stays the
     * build's to serve, since every revision running moves the one label.
     */
    public List<ServedPrompt> overridingProductionVersions() {
        return jdbc.sql("""
                        SELECT v.prompt_id, v.version, v.body, v.provider, v.model_id, v.settings::text AS settings
                        FROM app_lm_platform_prompt_label l
                        JOIN app_lm_platform_prompt_version v ON v.prompt_id = l.prompt_id AND v.version = l.version
                        WHERE l.label = :label AND (v.origin = 'EDITED' OR l.moved_by IS NOT NULL)
                        """)
                .param("label", PRODUCTION)
                .query(this::servedPrompt)
                .list();
    }

    public List<RecordedBundledPrompt> recordedBundledPrompts() {
        return jdbc.sql("""
                        SELECT prompt_id, bundled_sha256, version FROM app_lm_platform_prompt_version
                        WHERE origin = 'BUNDLED'
                        """)
                .query((row, index) -> new RecordedBundledPrompt(row.getString("prompt_id"),
                        row.getString("bundled_sha256"), row.getInt("version")))
                .list();
    }

    public Optional<ProductionVersion> productionOf(String promptId) {
        return jdbc.sql("""
                        SELECT v.version, v.origin
                        FROM app_lm_platform_prompt_label l
                        JOIN app_lm_platform_prompt_version v ON v.prompt_id = l.prompt_id AND v.version = l.version
                        WHERE l.prompt_id = :promptId AND l.label = :label
                        """)
                .param("promptId", promptId)
                .param("label", PRODUCTION)
                .query((row, index) -> new ProductionVersion(row.getInt("version"),
                        PromptOrigin.valueOf(row.getString("origin"))))
                .optional();
    }

    /** Held to the end of the transaction, so instances booting together record a release's prompts once. */
    void lockForSync() {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", SYNC_LOCK)
                .query(row -> { });
    }

    Optional<Integer> bundledVersion(BundledPrompt prompt) {
        return jdbc.sql("""
                        SELECT version FROM app_lm_platform_prompt_version
                        WHERE prompt_id = :promptId AND bundled_sha256 = :sha256
                        """)
                .param("promptId", prompt.promptId())
                .param("sha256", prompt.sha256())
                .query(Integer.class)
                .optional();
    }

    int insertBundled(BundledPrompt prompt) {
        return jdbc.sql("""
                        INSERT INTO app_lm_platform_prompt_version (prompt_id, version, body, origin, bundled_sha256)
                        SELECT :promptId, coalesce(max(version), 0) + 1, :body, 'BUNDLED', :sha256
                        FROM app_lm_platform_prompt_version WHERE prompt_id = :promptId
                        RETURNING version
                        """)
                .param("promptId", prompt.promptId())
                .param("body", prompt.body())
                .param("sha256", prompt.sha256())
                .query(Integer.class)
                .single();
    }

    /** Points production at {@code toVersion} and records the move. {@code movedBy} null is a release's own sync. */
    public void moveProduction(String promptId, Integer fromVersion, int toVersion, UUID movedBy, String reason) {
        jdbc.sql("""
                        INSERT INTO app_lm_platform_prompt_label (prompt_id, label, version, moved_by)
                        VALUES (:promptId, :label, :version, :movedBy)
                        ON CONFLICT (prompt_id, label)
                        DO UPDATE SET version = EXCLUDED.version, moved_by = EXCLUDED.moved_by, moved_at = now()
                        """)
                .param("promptId", promptId)
                .param("label", PRODUCTION)
                .param("version", toVersion)
                .param("movedBy", movedBy)
                .update();
        jdbc.sql("""
                        INSERT INTO app_lm_platform_prompt_label_move
                            (prompt_id, label, from_version, to_version, reason, moved_by)
                        VALUES (:promptId, :label, :fromVersion, :toVersion, :reason, :movedBy)
                        """)
                .param("promptId", promptId)
                .param("label", PRODUCTION)
                .param("fromVersion", fromVersion)
                .param("toVersion", toVersion)
                .param("reason", reason)
                .param("movedBy", movedBy)
                .update();
    }

    void add(PromptCallCount count) {
        jdbc.sql("""
                        INSERT INTO app_lm_platform_prompt_call_daily AS daily
                            (prompt_id, version, provider, model_id, day, calls, input_tokens, output_tokens,
                             finish_stop, finish_max_tokens, finish_safety, finish_other)
                        VALUES (:promptId, :version, :provider, :modelId, (now() AT TIME ZONE 'UTC')::date, 1,
                                :inputTokens, :outputTokens, :stop, :maxTokens, :safety, :other)
                        ON CONFLICT (prompt_id, version, provider, model_id, day) DO UPDATE SET
                            calls = daily.calls + 1,
                            input_tokens = daily.input_tokens + EXCLUDED.input_tokens,
                            output_tokens = daily.output_tokens + EXCLUDED.output_tokens,
                            finish_stop = daily.finish_stop + EXCLUDED.finish_stop,
                            finish_max_tokens = daily.finish_max_tokens + EXCLUDED.finish_max_tokens,
                            finish_safety = daily.finish_safety + EXCLUDED.finish_safety,
                            finish_other = daily.finish_other + EXCLUDED.finish_other
                        """)
                .param("promptId", count.promptId())
                .param("version", count.version())
                .param("provider", count.provider().name())
                .param("modelId", count.modelId())
                .param("inputTokens", count.inputTokens())
                .param("outputTokens", count.outputTokens())
                .param("stop", oneIf(count, FinishKind.STOP))
                .param("maxTokens", oneIf(count, FinishKind.MAX_TOKENS))
                .param("safety", oneIf(count, FinishKind.SAFETY))
                .param("other", oneIf(count, FinishKind.OTHER))
                .update();
    }

    /** Every prompt's production version, with the newest version a release shipped for it. */
    public List<LivePrompt> livePrompts() {
        return jdbc.sql(VERSION_COLUMNS + """
                        , (SELECT coalesce(max(b.version), 0) FROM app_lm_platform_prompt_version b
                           WHERE b.prompt_id = v.prompt_id AND b.origin = 'BUNDLED') AS newest_bundled
                        FROM app_lm_platform_prompt_label l
                        JOIN app_lm_platform_prompt_version v ON v.prompt_id = l.prompt_id AND v.version = l.version
                        LEFT JOIN app_lm_user author ON author.id = v.created_by
                        WHERE l.label = :label
                        """)
                .param("label", PRODUCTION)
                .query((row, index) -> new LivePrompt(promptVersion(row, index), row.getInt("newest_bundled")))
                .list();
    }

    public List<PromptVersion> versionsOf(String promptId) {
        return jdbc.sql(VERSION_COLUMNS + """
                        FROM app_lm_platform_prompt_version v
                        LEFT JOIN app_lm_user author ON author.id = v.created_by
                        WHERE v.prompt_id = :promptId
                        ORDER BY v.version DESC
                        """)
                .param("promptId", promptId)
                .query(this::promptVersion)
                .list();
    }

    /** One version as a call would be served it: its text, model and settings. */
    public Optional<ServedPrompt> version(String promptId, int version) {
        return jdbc.sql("""
                        SELECT v.prompt_id, v.version, v.body, v.provider, v.model_id, v.settings::text AS settings
                        FROM app_lm_platform_prompt_version v
                        WHERE v.prompt_id = :promptId AND v.version = :version
                        """)
                .param("promptId", promptId)
                .param("version", version)
                .query(this::servedPrompt)
                .optional();
    }

    /** Held to the end of the transaction, so two saves or two moves of one prompt happen one after the other. */
    public void lockPrompt(String promptId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "prompt:" + promptId)
                .query(row -> { });
    }

    public int insertEdited(String promptId, String body, AiProvider provider, String modelId,
                            GenerationSettings settings, String changeNote, UUID createdBy) {
        return jdbc.sql("""
                        INSERT INTO app_lm_platform_prompt_version
                            (prompt_id, version, body, provider, model_id, settings, origin, change_note, created_by)
                        SELECT :promptId, coalesce(max(version), 0) + 1, :body, :provider, :modelId,
                               CAST(:settings AS jsonb), 'EDITED', :changeNote, :createdBy
                        FROM app_lm_platform_prompt_version WHERE prompt_id = :promptId
                        RETURNING version
                        """)
                .param("promptId", promptId)
                .param("body", body)
                .param("provider", provider == null ? null : provider.name())
                .param("modelId", modelId)
                .param("settings", json.writeValueAsString(settings))
                .param("changeNote", changeNote)
                .param("createdBy", createdBy)
                .query(Integer.class)
                .single();
    }

    private PromptVersion promptVersion(ResultSet row, int index) throws SQLException {
        String provider = row.getString("provider");
        return new PromptVersion(
                row.getString("prompt_id"),
                row.getInt("version"),
                PromptOrigin.valueOf(row.getString("origin")),
                provider == null ? null : AiProvider.valueOf(provider),
                row.getString("model_id"),
                settingsOf(row.getString("settings")),
                row.getString("change_note"),
                row.getTimestamp("created_at").toInstant(),
                row.getString("author_name"));
    }

    private GenerationSettings settingsOf(String settings) {
        return settings == null ? GenerationSettings.UNSET : json.readValue(settings, GenerationSettings.class);
    }

    private static int oneIf(PromptCallCount count, FinishKind kind) {
        return count.finish() == kind ? 1 : 0;
    }

    private ServedPrompt servedPrompt(ResultSet row, int index) throws SQLException {
        String provider = row.getString("provider");
        return new ServedPrompt(
                row.getString("prompt_id"),
                row.getString("body"),
                row.getInt("version"),
                provider == null ? null : AiProvider.valueOf(provider),
                row.getString("model_id"),
                settingsOf(row.getString("settings")));
    }
}
