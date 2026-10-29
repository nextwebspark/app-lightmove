package app.lightmove.api.triagecompany.repository;

import java.util.UUID;

/** A company of a mandate the search box matched on its own facts, with the stage it stands at. */
public interface SearchMatchedCompany {

    UUID getId();

    String getStatus();
}
