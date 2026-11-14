package app.lightmove.api.candidate.model;

/**
 * The fixed block one outreach email ends with (#924), and the one-click address its {@code List-Unsubscribe} header
 * names. {@code givesNotice} says the firm's privacy details are set up, so the email told the person what a privacy
 * notice would have.
 */
public record OutreachPrivacyFooter(String html, String oneClickUrl, boolean givesNotice) {}
