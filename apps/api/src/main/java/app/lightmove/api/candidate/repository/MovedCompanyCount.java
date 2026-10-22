package app.lightmove.api.candidate.repository;

/** How many companies of another stage a stage lists for someone of theirs moved there on their own. */
public interface MovedCompanyCount {

    String getTriageStatus();

    long getCompanies();
}
