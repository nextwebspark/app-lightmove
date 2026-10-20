package app.lightmove.api.triagecompany.repository;

import java.util.UUID;

/** How many live notes one company of a mandate carries. */
public interface CompanyNoteCount {

    UUID getTriageCompanyId();

    long getNotes();
}
