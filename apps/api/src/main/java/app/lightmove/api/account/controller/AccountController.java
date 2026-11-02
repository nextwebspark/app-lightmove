package app.lightmove.api.account.controller;

import app.lightmove.api.account.dto.AccountDeletionPreviewResponse;
import app.lightmove.api.account.dto.AccountExportResponse;
import app.lightmove.api.account.dto.DeleteAccountRequest;
import app.lightmove.api.account.service.AccountDeletionService;
import app.lightmove.api.account.service.AccountExportService;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.security.model.AuthPrincipal;
import app.lightmove.api.core.security.token.RefreshCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in person's own account: its copy as a file, and its deletion. Under {@code /auth}, so the refresh cookie
 * is on the request — an account with no password proves itself by when that session was signed in, and the deletion
 * expires it. Any signed-in person may use these, a client representative included: the account is theirs.
 */
@RestController
@RequestMapping("/api/v1/auth/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountDeletionService deletion;
    private final AccountExportService export;
    private final RefreshCookieFactory refreshCookie;
    private final RateLimitGuard rateLimit;

    @GetMapping("/deletion-preview")
    public AccountDeletionPreviewResponse deletionPreview(
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken) {
        return deletion.preview(principal.userId(), refreshToken);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        // The only brake on guessing the password here, as on a password change: neither feeds the login lockout.
        rateLimit.checkPasswordChange(principal.email(), httpRequest);
        deletion.delete(principal.userId(), request, refreshToken, httpRequest);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.expire().toString())
                .build();
    }

    @GetMapping("/export")
    public ResponseEntity<AccountExportResponse> export(
            @AuthenticationPrincipal AuthPrincipal principal,
            @CookieValue(name = "${lightmove.auth.cookie.name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        String fileName = "uncava-account-" + LocalDate.now(ZoneOffset.UTC) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(export.export(principal.userId(), refreshToken, httpRequest));
    }
}
