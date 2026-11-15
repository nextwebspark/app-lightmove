package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * The position a share-with-client consent is for (#1267 phase 3): its role title, its client's name, and whether
 * the search is confidential, in which case no email may name the client.
 */
public record ShareTarget(UUID projectId, String roleTitle, String clientName, boolean confidential) {

    /** The client as a share email or page names it: by name only when the firm chose to and the search allows. */
    public String clientNamedOrNull(boolean clientNamed) {
        return clientNamed && !confidential ? clientName : null;
    }
}
