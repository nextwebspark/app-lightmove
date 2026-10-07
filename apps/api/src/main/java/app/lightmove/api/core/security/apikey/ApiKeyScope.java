package app.lightmove.api.core.security.apikey;

import app.lightmove.api.common.constant.ApiValueEnum;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;

/**
 * What a key may read, and {@link #MCP_USE}, where it may read it from. V117's CHECK lists the same tokens. All
 * read-only: no key writes anything.
 */
public enum ApiKeyScope implements ApiValueEnum {

    PROJECTS_READ("projects:read"),
    COMPANIES_READ("companies:read"),
    CANDIDATES_READ("candidates:read"),

    /** Executives' emails and phone numbers — personal data, so never implied by {@link #CANDIDATES_READ}. */
    CANDIDATE_CONTACTS_READ("candidates.contacts:read"),

    /** Executives' packages — personal data, so never implied by {@link #CANDIDATES_READ}. */
    CANDIDATE_COMPENSATION_READ("candidates.compensation:read"),

    /**
     * Opens the MCP server to a key, opt-in, and reads nothing by itself. Never an OAuth scope: an MCP client's token is
     * for the MCP server already.
     */
    MCP_USE("mcp:use");

    private static final List<ApiKeyScope> DATA_SCOPES = Arrays.stream(values())
            .filter(scope -> scope != MCP_USE)
            .toList();

    private final String value;

    ApiKeyScope(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }

    /** The scopes that read something: what an OAuth client may ask for and a consent may grant. */
    public static List<ApiKeyScope> dataScopes() {
        return DATA_SCOPES;
    }
}
