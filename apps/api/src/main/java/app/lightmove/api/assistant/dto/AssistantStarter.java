package app.lightmove.api.assistant.dto;

import app.lightmove.api.assistant.constant.StarterKind;

/** One question the empty chat offers, sent as it reads when pressed. */
public record AssistantStarter(StarterKind kind, String prompt) {}
