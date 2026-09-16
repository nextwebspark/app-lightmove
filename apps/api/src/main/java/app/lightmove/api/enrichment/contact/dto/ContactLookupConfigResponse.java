package app.lightmove.api.enrichment.contact.dto;

/** Whether this deployment can look contacts up at all, and so whether the drawer offers the buttons. */
public record ContactLookupConfigResponse(boolean enabled) {}
