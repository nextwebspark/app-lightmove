package app.lightmove.api.assistant.dto;

import java.util.List;

/**
 * The empty chat's starters. {@code sectorAssumed} is true when nothing records the firm's sector and
 * the prompts fall back to retail, so the panel can say so rather than pass a guess off as knowledge.
 */
public record AssistantStartersResponse(boolean sectorAssumed, List<AssistantStarter> starters) {}
