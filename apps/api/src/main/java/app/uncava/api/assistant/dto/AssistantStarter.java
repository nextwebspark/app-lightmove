package app.uncava.api.assistant.dto;

import app.uncava.api.assistant.constant.StarterKind;

/** One question the empty chat offers, sent as it reads when pressed. */
public record AssistantStarter(StarterKind kind, String prompt) {}
