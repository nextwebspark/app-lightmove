package app.lightmove.api.workspace.model;

import app.lightmove.api.core.audit.constant.AuditOutcome;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import java.util.Map;
import java.util.Set;

/**
 * One audit row as the audit log may phrase it: its type, outcome, the name its target resolves to on a live row,
 * and its metadata behind {@link #text}, which answers only the keys on {@link #READABLE_KEYS}. Metadata holds emails
 * and names on some rows (#927); a key nobody listed here cannot reach a line, however a phrase is written.
 */
public record AuditLine(String code, AuditOutcome outcome, Map<String, Object> metadata, String targetName,
                        WorkspaceMode mode) {

    public static final Set<String> READABLE_KEYS = Set.of(
            "reason", "secondFactor", "provider", "role", "type", "from", "to", "addedRole", "action", "visible",
            "stage", "rows", "people", "selected", "preview", "section", "mode", "kind", "plan", "seats",
            "previousSeats", "status", "credits", "pack", "tool", "credentialKind", "sessions", "left",
            "group");

    public String text(String key) {
        if (!READABLE_KEYS.contains(key)) {
            throw new IllegalArgumentException("The audit log does not read metadata key " + key);
        }
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }

    public boolean is(String key, String expected) {
        return expected.equals(text(key));
    }

    public boolean failed() {
        return outcome == AuditOutcome.FAILURE;
    }

    public boolean agency() {
        return mode == WorkspaceMode.AGENCY;
    }
}
