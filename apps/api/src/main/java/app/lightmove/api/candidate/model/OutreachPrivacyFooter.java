package app.lightmove.api.candidate.model;

import org.springframework.modulith.NamedInterface;

/**
 * The fixed block one outreach email ends with, and the one-click address its {@code List-Unsubscribe} header
 * names. {@code givesNotice} says the firm's privacy details are set up, so the email told the person what a privacy
 * notice would have.
 */
@NamedInterface(name = "api", propagate = false)
public record OutreachPrivacyFooter(String html, String oneClickUrl, boolean givesNotice) {}
