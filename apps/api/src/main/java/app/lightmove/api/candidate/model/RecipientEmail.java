package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ContactKind;
import org.springframework.modulith.NamedInterface;

/** One address on a person's contact ledger, as outreach may offer it for the To line. */
@NamedInterface(name = "api", propagate = false)
public record RecipientEmail(String address, ContactKind kind, boolean verified) {}
