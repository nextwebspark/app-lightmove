package app.uncava.api.workspace.service;

import app.uncava.api.common.persona.model.HiringCompanyProfile;
import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;
import app.uncava.api.strategy.model.CompanyRow;
import app.uncava.api.strategy.service.ApolloCompanyQueryService;
import app.uncava.api.workspace.model.Firm;
import app.uncava.api.workspace.model.Workspace;
import app.uncava.api.workspace.model.WorkspaceCompany;
import app.uncava.api.workspace.repository.WorkspaceRepository;
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
    public Firm firmOf(UUID workspaceId) {
        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return new Firm(workspace.getMode(), profileOf(workspace));
    }

    private HiringCompanyProfile profileOf(Workspace workspace) {
        WorkspaceCompany company = workspace.getCompany();
        if (company == null) {
            return new HiringCompanyProfile(workspace.getName(), null, null, null, null, null,
                    workspace.getPersona());
        }
        Integer employees = universe.byAccountIds(List.of(company.apolloAccountId())).stream()
                .findFirst()
                .map(CompanyRow::numEmployees)
                .orElse(null);
        return new HiringCompanyProfile(workspace.getName(), company.industry(), company.city(),
                company.country(), company.website(), employees, workspace.getPersona());
    }
}
