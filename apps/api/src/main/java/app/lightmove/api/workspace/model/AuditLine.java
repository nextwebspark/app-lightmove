package app.lightmove.api.workspace.model;

import app.lightmove.api.core.audit.constant.AuditOutcome;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import java.util.Map;
import java.util.Set;

/**
 * One audit row as a phrase may read it. {@link #text} answers only {@link #READABLE_KEYS}, so a key not listed cannot
 * reach a line, however a phrase is written: details hold ids since #927, and names come from live rows.
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
