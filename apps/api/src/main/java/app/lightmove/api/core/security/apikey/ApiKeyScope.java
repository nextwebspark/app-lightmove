package app.lightmove.api.core.security.apikey;

import app.lightmove.api.common.constant.ApiValueEnum;
import com.fasterxml.jackson.annotation.JsonValue;

/** What a key may read. V114's CHECK lists the same five tokens. All read-only: no key writes anything. */
public enum ApiKeyScope implements ApiValueEnum {

    PROJECTS_READ("projects:read"),
    COMPANIES_READ("companies:read"),
    CANDIDATES_READ("candidates:read"),

    /** Executives' emails and phone numbers — personal data, so never implied by {@link #CANDIDATES_READ}. */
    CANDIDATE_CONTACTS_READ("candidates.contacts:read"),

    /** Executives' packages — personal data, so never implied by {@link #CANDIDATES_READ}. */
    CANDIDATE_COMPENSATION_READ("candidates.compensation:read");

    private final String value;

    ApiKeyScope(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }
}
