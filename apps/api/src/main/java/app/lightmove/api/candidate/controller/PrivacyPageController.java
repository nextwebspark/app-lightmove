package app.lightmove.api.candidate.controller;

import app.lightmove.api.candidate.dto.PrivacyPageRequest;
import app.lightmove.api.candidate.dto.PrivacyPageResponse;
import app.lightmove.api.candidate.service.PrivacyPageService;
import app.lightmove.api.core.config.DataProtectionSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The public page a privacy notice links to (#923): no session, the token in the path is the credential. */
@RestController
@RequestMapping("/api/v1/privacy/links/{token}")
@RequiredArgsConstructor
@ConditionalOnProperty(name = DataProtectionSettings.SWITCH, havingValue = "true")
public class PrivacyPageController {

    private final PrivacyPageService pages;

    @GetMapping
    public PrivacyPageResponse open(@PathVariable String token, HttpServletRequest request) {
        return pages.open(token, request);
    }

    @PostMapping("/objection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void object(@PathVariable String token, HttpServletRequest request) {
        pages.object(token, request);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@PathVariable String token, @Valid @RequestBody PrivacyPageRequest body,
                        HttpServletRequest request) {
        pages.request(token, body.kind(), request);
    }
}
