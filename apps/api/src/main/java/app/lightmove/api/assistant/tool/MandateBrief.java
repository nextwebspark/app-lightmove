package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * The mandate as the model sees it: the role, the client and why the search exists. Compensation and
 * the brief's internal notes are deliberately absent — no targeting question needs them.
 */
public record MandateBrief(String roleTitle, String seniority, String department, String locationCity,
                           String locationCountry, String clientName, String clientSector,
                           String clientHqCity, String clientHqCountry, Integer clientEmployees, List<String> responsibilities,
                           String narrative, String mandateReason, String businessDriver,
                           List<String> strategicPriorities) {
}
