package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Which mailbox host to sign in with, one of {@link MailboxResponse#providers()}. */
public record MailboxConnectRequest(@NotBlank @Size(max = 32) String provider) {}
