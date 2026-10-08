/**
 * <b>The Uncava Assistant</b> — a chat inside a project that searches the company universe, suggests
 * companies the user can file into the mandate, and reads the executives the mandate mapped.
 *
 * <p>One request per question: {@code AssistantAgent} makes one Gemini call over a short core prompt,
 * the playbooks in {@code resources/assistant/skills} (loaded on demand through the {@code Skill} tool)
 * and the tools in {@code tool/}, and {@code AssistantService} saves the answer and any suggested
 * companies as a turn. See {@code docs/assistant-tools.md} for the flow and how to add a tool or a
 * playbook.
 */
package app.lightmove.api.assistant;
