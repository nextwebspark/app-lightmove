package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.Map;

/**
 * The fixed explanation a subject access export opens with (GDPR Art. 15(1)(a)–(h)): why the data is held, who
 * reads it, how long it is kept, where it came from, the person's rights, and what a reader should know about the
 * file itself.
 */
public record SubjectAccessAbout(String controller, String purposes, List<String> recipients, String retention,
                                 Map<String, String> sources, String rights, String deletedNotes,
                                 String otherPeople, String automatedDecisions) {}
