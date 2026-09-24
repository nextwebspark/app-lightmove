package app.lightmove.api.assistant.tool;

import java.util.List;

/**
 * The position a mandate is hiring for, as much as the model needs to reason about where to source
 * it. Compensation and internal context are left out: neither decides which companies to look at,
 * and both are the sensitive half of a brief. The firm itself is in the system prompt, not here.
 */
public record MandateBrief(String roleTitle, String seniority, String department, String locationCity,
                           String locationCountry, List<String> responsibilities, String narrative,
                           String mandateReason, String businessDriver, List<String> strategicPriorities) {}
