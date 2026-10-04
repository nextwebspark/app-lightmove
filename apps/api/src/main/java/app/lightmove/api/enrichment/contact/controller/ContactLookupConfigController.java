package app.lightmove.api.enrichment.contact.controller;

import app.lightmove.api.enrichment.contact.dto.ContactLookupConfigResponse;
import app.lightmove.api.enrichment.contact.service.ContactLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whether the drawer offers the Find buttons. No {@code @PreAuthorize}: {@code /api/v1/**} already
 * requires a verified principal, and a pure CLIENT holds no workspace action to name.
 */
@RestController
@RequiredArgsConstructor
public class ContactLookupConfigController {

    private final ContactLookupService lookups;

    @GetMapping("/api/v1/contact-lookup/config")
    public ContactLookupConfigResponse config() {
        return lookups.config();
    }
}
