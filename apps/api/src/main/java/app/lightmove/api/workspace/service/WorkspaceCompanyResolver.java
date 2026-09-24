package app.lightmove.api.workspace.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.strategy.service.ApolloCompanyQueryService;
import app.lightmove.api.workspace.model.WorkspaceCompany;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A picked company is filed under the universe's own name and snapshot, resolved here rather than
 * trusted from the request — the same rule a client record follows. Signup and Settings both file through it.
 */
@Component
@RequiredArgsConstructor
class WorkspaceCompanyResolver {

    private final ApolloCompanyQueryService companies;

    WorkspaceIdentity resolve(String typedName, String apolloAccountId) {
        if (apolloAccountId == null || apolloAccountId.isBlank()) {
            return new WorkspaceIdentity(typedName.trim(), null);
        }
        return companies.byAccountIds(List.of(apolloAccountId)).stream().findFirst()
                .map(row -> new WorkspaceIdentity(row.companyName().trim(), WorkspaceCompany.of(row)))
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "That company is no longer in the database"));
    }

    record WorkspaceIdentity(String name, WorkspaceCompany company) {
    }
}
