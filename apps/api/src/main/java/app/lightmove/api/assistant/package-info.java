/**
 * <b>The Uncava Assistant</b> — a chat inside a project that searches the company universe, answers with
 * a card of companies the user can file into the mandate, and reads the executives the mandate mapped.
 *
 * <p>One request per question: {@code AssistantSupervisor} has a domain specialist answer it, each
 * specialist a nested Gemini call with its own tools from {@code tool/}, and {@code AssistantService}
 * saves the answer and any proposed card as a turn. See {@code docs/assistant-tools.md} for the flow
 * and how to add a tool or a specialist.
 */
package app.lightmove.api.assistant;
