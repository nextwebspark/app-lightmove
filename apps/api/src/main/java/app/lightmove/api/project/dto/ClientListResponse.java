package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ClientType;
import app.lightmove.api.project.model.Client;
import java.util.List;
import java.util.UUID;

/** One row of the Clients table. {@code type} and every count are derived, never stored. */
public record ClientListResponse(
        UUID id,
        String name,
        ClientType type,
        String sector,
        String hqCountry,
        String hqCity,
        String logoUrl,
        long activeMandates,
        long deliveredMandates,
        List<RepAvatar> contacts,
        ViewerSummary viewers
) {

    /**
     * The one place a row is assembled. Four consecutive {@code String}s in the middle of eleven
     * positional components is a transposition nothing would catch, and the newborn client a create
     * answers with must be the same row a later list read returns.
     */
    public static ClientListResponse of(Client client, ClientType type, long activeMandates,
                                        long deliveredMandates, List<RepAvatar> contacts,
                                        ViewerSummary viewers) {
        return new ClientListResponse(client.getId(), client.getName(), type, client.getSector(),
                client.getHqCountry(), client.getHqCity(), client.getLogoUrl(), activeMandates,
                deliveredMandates, contacts, viewers);
    }
}
