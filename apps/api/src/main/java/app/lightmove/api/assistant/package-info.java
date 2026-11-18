/**
 * <b>The Uncava Assistant</b> — a project chat that searches the universe, suggests companies to file and reads the
 * mandate's executives: one Gemini call per question ({@code AssistantAgent}), saved as a turn. See
 * {@code docs/assistant-tools.md}.
 */
@ApplicationModule(allowedDependencies = {
        "account :: api",
        "billing :: api",
        "candidate :: api",
        "enrichment :: api",
        "position :: api",
        "project :: api",
        "strategy :: api",
        "triagecompany :: api",
        "universe :: api",
        "workspace :: api"
})
package app.lightmove.api.assistant;

import org.springframework.modulith.ApplicationModule;
