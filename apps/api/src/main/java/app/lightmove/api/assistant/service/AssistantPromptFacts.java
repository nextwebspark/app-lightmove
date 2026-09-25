package app.lightmove.api.assistant.service;

/** What the system prompt says about the firm asking and the position it is hiring for, rendered. */
record AssistantPromptFacts(String firm, String position) {}
