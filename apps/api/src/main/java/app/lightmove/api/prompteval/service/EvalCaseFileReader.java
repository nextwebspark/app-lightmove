package app.lightmove.api.prompteval.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.llm.service.EvalSubject;
import app.lightmove.api.prompteval.constant.EvalCaseSplit;
import app.lightmove.api.prompteval.model.EvalCaseFileRow;
import app.lightmove.api.prompteval.model.NewEvalCase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a file of cases: JSONL, one case a line, a blank line or a {@code //} line skipped. Every line is checked
 * and none is added here; a line at fault is answered with what is wrong, never refused as a whole request, so one
 * read names every row to fix.
 */
@Component
@RequiredArgsConstructor
class EvalCaseFileReader {

    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_CASES = 500;
    static final int MAX_KEY = 40;
    static final int MAX_NAME = 200;
    static final int MAX_TAG = 40;
    static final String SYNTHETIC = "SYNTHETIC";

    private static final Set<String> FIELDS = Set.of("key", "name", "tag", "split", "source", "input", "expected");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private final ObjectMapper json;

    List<EvalCaseFileRow> read(MultipartFile file, EvalSubject subject) {
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.of(ErrorCode.FILE_TOO_LARGE);
        }
        List<String> lines = textOf(file).lines().toList();
        List<EvalCaseFileRow> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            if (rows.size() == MAX_CASES) {
                throw ApiException.of(ErrorCode.IMPORT_TOO_MANY_ROWS);
            }
            rows.add(rowOf(index + 1, line, subject, keys));
        }
        return rows;
    }

    private EvalCaseFileRow rowOf(int lineNumber, String line, EvalSubject subject, Set<String> keysSoFar) {
        JsonNode row;
        try {
            row = json.readTree(line);
        } catch (JacksonException unreadable) {
            return new EvalCaseFileRow(lineNumber, null, List.of("This line is not valid JSON."));
        }
        if (row == null || !row.isObject()) {
            return new EvalCaseFileRow(lineNumber, null, List.of("This line is not a JSON object."));
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : row.properties()) {
            if (!FIELDS.contains(field.getKey())) {
                problems.add(cut(field.getKey()) + " is not a field of a case.");
            }
        }
        String key = textOf(row, "key", MAX_KEY, true, problems);
        if (key != null && !KEY.matcher(key).matches()) {
            problems.add("key may hold only letters, digits, dots, dashes and underscores.");
            key = null;
        }
        if (key != null && !keysSoFar.add(key)) {
            problems.add("key " + key + " appears earlier in this file.");
        }
        String name = textOf(row, "name", MAX_NAME, true, problems);
        String tag = textOf(row, "tag", MAX_TAG, false, problems);
        EvalCaseSplit split = splitOf(row.get("split"), problems);
        if (!isText(row.get("source")) || !SYNTHETIC.equals(row.get("source").asString())) {
            problems.add("source is not SYNTHETIC. Only synthetic cases can be imported.");
        }
        JsonNode input = row.get("input");
        JsonNode expected = isAbsent(row.get("expected")) ? null : row.get("expected");
        if (input == null || !input.isObject()) {
            problems.add("input is missing.");
        } else if (expected != null && !expected.isObject()) {
            problems.add("expected is not an object.");
        } else {
            problems.addAll(subject.problemsOf(input, expected));
        }
        return new EvalCaseFileRow(lineNumber,
                problems.isEmpty() ? new NewEvalCase(key, name, tag, split, input, expected) : null, problems);
    }

    private static String textOf(JsonNode row, String field, int max, boolean required, List<String> problems) {
        JsonNode value = row.get(field);
        if (isAbsent(value)) {
            if (required) {
                problems.add(field + " is missing.");
            }
            return null;
        }
        if (!value.isString()) {
            problems.add(field + " is not text.");
            return null;
        }
        String text = value.asString().strip();
        if (text.isEmpty()) {
            if (required) {
                problems.add(field + " is missing.");
            }
            return null;
        }
        if (text.length() > max) {
            problems.add(field + " is longer than " + max + " characters.");
            return null;
        }
        return text;
    }

    private static EvalCaseSplit splitOf(JsonNode value, List<String> problems) {
        if (isText(value)) {
            for (EvalCaseSplit split : EvalCaseSplit.values()) {
                if (split.name().equals(value.asString())) {
                    return split;
                }
            }
        }
        problems.add("split is not DEV or HOLDOUT.");
        return null;
    }

    private static boolean isAbsent(JsonNode value) {
        return value == null || value.isNull();
    }

    private static boolean isText(JsonNode value) {
        return value != null && value.isString();
    }

    private static String cut(String fieldName) {
        return fieldName.length() <= MAX_KEY ? fieldName : fieldName.substring(0, MAX_KEY) + "…";
    }

    private static String textOf(MultipartFile file) {
        try {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            return text.startsWith(BYTE_ORDER_MARK) ? text.substring(1) : text;
        } catch (IOException unreadable) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "The case file could not be read: " + unreadable);
        }
    }
}
