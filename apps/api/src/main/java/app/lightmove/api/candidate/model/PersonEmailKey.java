package app.lightmove.api.candidate.model;

import java.util.UUID;

/** One address a workspace person holds, on the contact ledger's key. */
public interface PersonEmailKey {

    String getEmailKey();

    UUID getPersonId();
}
