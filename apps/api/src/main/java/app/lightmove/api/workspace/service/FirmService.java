package app.lightmove.api.workspace.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.strategy.model.CompanyRow;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.workspace.model.FirmFacts;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceCompany;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The firm a workspace is, for a reader outside {@code workspace} — the assistant's context. */
@Service
@RequiredArgsConstructor
public class FirmService {

    private final WorkspaceRepository workspaces;
    private final ApolloCompanyQueryService universe;

    @Transactional(readOnly = true)
    public FirmFacts firmOf(UUID workspaceId) {
        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        WorkspaceCompany company = workspace.getCompany();
        if (company == null) {
            return new FirmFacts(workspace.getName(), null, null, null, null, null, workspace.getPersona());
        }
        Integer employees = universe.byAccountIds(List.of(company.apolloAccountId())).stream()
                .findFirst()
                .map(CompanyRow::numEmployees)
                .orElse(null);
        return new FirmFacts(workspace.getName(), company.industry(), company.city(), company.country(),
                company.website(), employees, workspace.getPersona());
    }
}
