package app.lightmove.api.candidate.model;

import java.util.List;
import org.springframework.modulith.NamedInterface;

/**
 * Everything the team has filed on a person beyond their profile: every note the position reads, newest first,
 * and the latest file of every document. Staff-only, like both — read only by the AI runs on an executive.
 */
@NamedInterface(name = "api", propagate = false)
public record CandidateMaterial(List<NoteOnFile> notes, List<DocumentOnFile> documents) {

    public static final CandidateMaterial NONE = new CandidateMaterial(List.of(), List.of());
}
