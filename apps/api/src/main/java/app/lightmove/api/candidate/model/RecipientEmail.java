package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.ContactKind;

/** One address on a person's contact ledger, as outreach may offer it for the To line. */
public record RecipientEmail(String address, ContactKind kind, boolean verified) {}
