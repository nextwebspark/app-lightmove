package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.SignatureLayout;

/** Everything a signature says, checked and trimmed: what is saved, and what a preview draws without saving. */
public record SignatureContent(String name, SignatureLayout layout, SignatureFields fields, boolean showPhoto,
                               boolean showLogo, boolean showBookingLink, String customBody, String disclaimer) {}
