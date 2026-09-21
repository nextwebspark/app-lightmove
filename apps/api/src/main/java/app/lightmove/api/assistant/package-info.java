/**
 * <b>The Uncava Assistant — a conversation that can search the mandate's market and act on it.</b>
 *
 * <p><b>A composer, like {@code talentmap} and {@code report}.</b> It owns its own rows — the thread,
 * its turns, and the event log a running turn writes — and reaches everything else through other
 * features' public service methods. Nothing depends back on it, which is what lets one feature see
 * the whole product without the dependency rule bending for it.
 *
 * <p><b>It is global, not a Strategy panel.</b> A thread belongs to a (workspace, user) pair and
 * carries a project only as the context it was asked in, so authorisation cannot come from the page
 * the question was typed on: every tool call is checked against its own arguments.
 *
 * <p>This package currently holds the seam and nothing else. The port is at the <b>turn</b> level
 * rather than at "one call to a model" — a turn is the unit that has a question, an answer, a bill
 * and a status, and abstracting below it would force every implementation down to the intersection
 * of what all of them can do, losing the provider-specific controls (caching, thinking depth,
 * effort) that are most of the reason to choose one.
 *
 * <p><b>Do not inject Spring AI's {@code ChatMemory}.</b> A bean of it is already in the context —
 * {@code spring-ai-starter-model-google-genai} pulls {@code spring-ai-autoconfigure-model-chat-memory}
 * transitively, and that autoconfiguration registers a {@code MessageWindowChatMemory} over an
 * {@code InMemoryChatMemoryRepository}. Nothing injects it, which is the only reason it is harmless:
 * it is per-instance and in-heap, so on Cloud Run two instances hold two different conversations,
 * and it is keyed on one opaque string with no workspace scoping at all.
 *
 * <p>Conversation state belongs to {@code app_lm_assistant_turn} (V65), which carries the status,
 * actor, audit and token columns a message list cannot. Adopting the native repository was costed
 * and rejected: {@code MessageChatMemoryAdvisor} writes to memory in {@code before()}, i.e. before
 * the model is called, so an adapter over our tables would have to lie about
 * {@code saveAll} — and because our RUNNING turn row already holds the question at accept time, the
 * advisor would send that question to the model twice.
 */
package app.lightmove.api.assistant;
