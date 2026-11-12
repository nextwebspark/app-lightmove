package app.lightmove.api.candidate.model;

import java.util.Map;

/**
 * What the ids in a subject access export's rows name: colleagues by name (as the author or actor of what they did,
 * never their own data), the positions with who they are for, pipeline statuses and each position's own columns.
 */
public record SubjectAccessReferences(Map<String, String> colleagues, Map<String, SubjectAccessPosition> positions,
                                      Map<String, String> statuses, Map<String, Map<String, String>> customColumns) {}
