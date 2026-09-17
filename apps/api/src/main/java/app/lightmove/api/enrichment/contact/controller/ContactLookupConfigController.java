package app.lightmove.api.enrichment.contact.controller;

import app.lightmove.api.enrichment.contact.dto.ContactLookupConfigResponse;
import app.lightmove.api.enrichment.contact.service.ContactLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whether this deployment looks contacts up, so the drawer knows whether to draw the buttons.
 *
 * <p>No {@code @PreAuthorize}, for the reason the talent map's config read carries none:
 * {@code /api/v1/**} already requires a verified principal, and a pure CLIENT holds no workspace
 * action to name here yet still has to be told what the screen offers.
 */
@RestController
@RequiredArgsConstructor
public class ContactLookupConfigController {

    private final ContactLookupService lookups;

    @GetMapping("/api/v1/contact-lookup/config")
    public ResponseEntity<ContactLookupConfigResponse> config() {
        return ResponseEntity.ok(lookups.config());
    }
}
