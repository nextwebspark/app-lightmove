package app.lightmove.api.assistant.tool;

import java.util.List;

/** The industries whose people move well into {@code industry}, spelled as the universe spells them. */
public record AdjacentIndustries(String industry, List<String> adjacent) {}
