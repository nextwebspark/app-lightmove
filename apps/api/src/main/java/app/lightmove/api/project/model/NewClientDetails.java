package app.lightmove.api.project.model;

/** What a new client record is made from: a universe company by its account id, or a name typed in. */
public record NewClientDetails(
        String apolloAccountId,
        String customName,
        String customDomain,
        String sector,
        String hqCountry
) {

    public boolean picksFromUniverse() {
        return apolloAccountId != null;
    }
}
