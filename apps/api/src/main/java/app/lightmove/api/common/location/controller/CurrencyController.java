package app.lightmove.api.common.location.controller;

import app.lightmove.api.common.location.dto.CurrenciesResponse;
import app.lightmove.api.common.location.service.Currencies;
import app.lightmove.api.core.security.rbac.RequireWorkspacePermission;
import app.lightmove.api.core.security.rbac.WorkspaceAction;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The currency vocabulary, served so every SPA picker offers the same list the server accepts. */
@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    @GetMapping
    @RequireWorkspacePermission(WorkspaceAction.PROJECT_BROWSE)
    public CurrenciesResponse currencies() {
        return new CurrenciesResponse(Currencies.offered());
    }
}
