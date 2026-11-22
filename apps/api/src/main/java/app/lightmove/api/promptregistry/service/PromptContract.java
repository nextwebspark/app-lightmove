package app.lightmove.api.promptregistry.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.chat.prompt.PromptTemplate;

/**
 * What an edit of a prompt must keep: exactly the {@code {variables}} of the shipped text, in a text that still
 * renders, because code fills them on every call. A prompt shipped without variables is sent as written, so it
 * has no contract here; what its feature sends beside it is code, which no edit reaches.
 */
final class PromptContract {

    private static final String FIELD = "body";

    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private PromptContract() {
    }

    static Set<String> variablesOf(String body) {
        Set<String> variables = new TreeSet<>();
        Matcher matcher = VARIABLE.matcher(body);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    /** Messages name only the shipped text's own variables, never one read out of the edit. */
    static void require(String shipped, String edited) {
        Set<String> required = variablesOf(shipped);
        if (required.isEmpty()) {
            return;
        }
        Set<String> present = variablesOf(edited);
        String names = required.stream().map(name -> "{" + name + "}").collect(Collectors.joining(" and "));
        for (String variable : required) {
            if (!present.contains(variable)) {
                throw broken("This prompt must keep {" + variable + "}. It is missing from the text, so the version "
                        + "was not saved.");
            }
        }
        if (!required.containsAll(present)) {
            throw broken("This prompt can only fill " + names + ". Remove the other variable, so the version can "
                    + "be saved.");
        }
        Map<String, Object> placeholders = new LinkedHashMap<>();
        required.forEach(variable -> placeholders.put(variable, variable));
        try {
            PromptTemplate.builder().template(edited).variables(placeholders).build().render();
        } catch (RuntimeException unrenderable) {
            throw broken("This text cannot be filled in. Check for a brace that is not one of " + names + ".");
        }
    }

    private static ApiException broken(String message) {
        return ApiException.withField(ErrorCode.PROMPT_CONTRACT_BROKEN, FIELD, message);
    }
}
