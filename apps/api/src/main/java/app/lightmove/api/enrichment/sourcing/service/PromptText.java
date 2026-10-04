package app.lightmove.api.enrichment.sourcing.service;

/** How the two sourcing prompts spell a value the brief or a record leaves empty. */
final class PromptText {

    static final String NOT_STATED = "not stated";

    private PromptText() {
    }

    static String orNotStated(String value) {
        return value == null || value.isBlank() ? NOT_STATED : value;
    }
}
