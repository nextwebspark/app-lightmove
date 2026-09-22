package app.lightmove.api.companydiscovery.controller;

import app.lightmove.api.companydiscovery.dto.DiscoverCompaniesRequest;
import app.lightmove.api.companydiscovery.dto.DiscoveryConfigResponse;
import app.lightmove.api.companydiscovery.dto.DiscoveryResponse;
import app.lightmove.api.companydiscovery.service.CompanyDiscoveryService;
import app.lightmove.api.core.security.model.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI Research, on the same path prefix as {@code CompanySearchController} and deliberately not in
 * that class: it states that nothing there is writable or scoped to a mandate, and this spends the
 * firm's money, writes an audit row, increments a counter and takes an optional mandate.
 *
 * <p>The gate is the same one the four search routes use. {@code PROJECT_BROWSE} is ADMIN and MEMBER
 * only (V6), which is what keeps a pure client representative off the market side — and it is an
 * ordinary request-thread read, so {@code @PreAuthorize} works here where it cannot on the
 * assistant's worker thread.
 */
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyDiscoveryController {

    private final CompanyDiscoveryService discovery;

    /** Read before the CTA is drawn, so an unconfigured deployment disables it rather than 503s. */
    @GetMapping("/discover/config")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<DiscoveryConfigResponse> config() {
        return ResponseEntity.ok(discovery.config());
    }

    /** A POST because it spends: this is not a read a browser may repeat on a back button. */
    @PostMapping("/discover")
    @PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")
    public ResponseEntity<DiscoveryResponse> discover(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @Valid @RequestBody DiscoverCompaniesRequest request,
                                                      HttpServletRequest httpRequest) {
        return ResponseEntity.ok(discovery.discover(principal.userId(),
                principal.requireWorkspaceId(), request, httpRequest));
    }
}
