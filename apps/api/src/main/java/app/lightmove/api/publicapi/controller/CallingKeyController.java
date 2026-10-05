package app.lightmove.api.publicapi.controller;

import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import app.lightmove.api.publicapi.dto.CallingKeyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The probe a client calls first: which key it holds, for which workspace, with which scopes. */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Key", description = "The API key a request is made with")
@ApiResponse(responseCode = "401", description = "The key is missing, invalid, revoked or expired",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "429", description = "Too many requests on this key; wait for Retry-After seconds",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class CallingKeyController {

    @GetMapping("/me")
    @Operation(operationId = "getCallingKey", summary = "Describe the calling key",
            description = "Needs no scope. Useful to check a key works before reading anything with it.")
    @ApiResponse(responseCode = "200", description = "The key, its workspace and its scopes")
    public CallingKeyResponse me(@Parameter(hidden = true) @AuthenticationPrincipal ApiKeyPrincipal key) {
        return CallingKeyResponse.of(key);
    }
}
