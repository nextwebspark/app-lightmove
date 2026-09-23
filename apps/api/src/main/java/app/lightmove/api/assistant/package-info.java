/**
 * <b>The Uncava Assistant</b> — a chat inside a project that searches the company universe and
 * answers with a card of companies the user can file into the mandate.
 *
 * <p>One request per question: {@code AssistantService.ask} calls Gemini with the tools in
 * {@code tool/}, and saves the answer and any proposed card as a turn. See
 * {@code docs/assistant-tools.md} for the flow and how to add a tool.
 */
package app.lightmove.api.assistant;
