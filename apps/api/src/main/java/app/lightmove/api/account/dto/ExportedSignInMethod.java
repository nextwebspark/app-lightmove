package app.lightmove.api.account.dto;

import java.time.Instant;

/** A provider the account signs in with, and the address that provider gave. */
public record ExportedSignInMethod(String provider, String email, Instant linkedAt) {}
