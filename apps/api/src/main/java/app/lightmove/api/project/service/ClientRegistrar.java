package app.lightmove.api.project.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.text.service.WebsiteDomain;
import app.lightmove.api.project.model.Client;
import app.lightmove.api.project.model.NewClientDetails;
import app.lightmove.api.project.repository.ClientRepository;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Makes a client record, from the registry's form or with the position that names it, refusing a name or a universe
 * company the workspace already holds. Saving and recording are apart because the audit line commits on its own: a
 * position made with its client records the client only once nothing is left to refuse the position.
 */
@Component
@RequiredArgsConstructor
public class ClientRegistrar {

    private final ClientRepository clients;
    private final ApolloCompanyQueryService companies;
    private final AuditService audit;

    public Client register(UUID userId, UUID workspaceId, NewClientDetails details) {
        Client client = details.picksFromUniverse()
                ? fromUniverse(userId, workspaceId, details)
                : fromCustom(userId, workspaceId, details);
        refuseNameHeld(workspaceId, client.getName());
        return clients.save(client);
    }

    public void recordCreated(UUID userId, UUID workspaceId, Client client, HttpServletRequest httpRequest) {
        audit.event(ProjectEventType.CLIENT_CREATED)
                .actor(userId).workspace(workspaceId).target("client", client.getId()).from(httpRequest)
                .detail("name", client.getName())
                .detail("source", client.getCompanySource() == null ? "custom" : "universe")
                .record();
    }

    /** A live holder is named so the caller can file under it; an archived one is named apart, to be restored. */
    public void refuseNameHeld(UUID workspaceId, String name) {
        clients.findByWorkspaceIdAndNameIgnoreCase(workspaceId, name).ifPresent(holder -> {
            if (holder.isArchived()) {
                throw ApiException.of(ErrorCode.CLIENT_NAME_ARCHIVED);
            }
            throw ApiException.withProperty(ErrorCode.CLIENT_ALREADY_EXISTS, "clientId", holder.getId());
        });
    }

    private Client fromUniverse(UUID userId, UUID workspaceId, NewClientDetails details) {
        String accountId = details.apolloAccountId();
        refuseCompanyHeld(workspaceId, accountId);
        CompanyRow row = companies.byAccountIds(List.of(accountId)).stream().findFirst()
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "That company is no longer in the database"));
        // Name, domain, city and logo are the universe's, not the request's; the domain is derived from
        // the website the universe publishes.
        String hqCountry = details.hqCountry() != null ? details.hqCountry() : row.companyCountry();
        return Client.fromUniverse(workspaceId, accountId, row.companyName(), details.sector(),
                hqCountry, row.companyCity(), WebsiteDomain.of(row.website()), row.logoUrl(), userId);
    }

    private Client fromCustom(UUID userId, UUID workspaceId, NewClientDetails details) {
        if (details.customName() == null || details.customName().isBlank()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Enter the company name");
        }
        return Client.custom(workspaceId, details.customName(), details.sector(), details.hqCountry(),
                details.customDomain(), userId);
    }

    /**
     * A record renamed since it was picked no longer carries the company's name, so the name check alone filed that
     * company a second time. Older workspaces may already hold two: any live one answers.
     */
    private void refuseCompanyHeld(UUID workspaceId, String accountId) {
        List<Client> holders = clients.findByWorkspaceIdAndCompanySourceAndCompanySourceId(
                workspaceId, Client.UNIVERSE_SOURCE, accountId);
        holders.stream().filter(holder -> !holder.isArchived()).findFirst().ifPresent(holder -> {
            throw ApiException.withProperty(ErrorCode.CLIENT_ALREADY_EXISTS, "clientId", holder.getId());
        });
        if (!holders.isEmpty()) {
            throw ApiException.of(ErrorCode.CLIENT_COMPANY_ARCHIVED);
        }
    }
}
