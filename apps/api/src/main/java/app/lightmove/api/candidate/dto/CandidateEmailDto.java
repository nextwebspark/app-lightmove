package app.lightmove.api.candidate.dto;

/**
 * One address a contact lookup found. {@code kind} is {@code "work"} or {@code "personal"} — null
 * where the provider did not say — and {@code status} its own word for the address, such as
 * {@code "Verified"}.
 */
public record CandidateEmailDto(String address, String kind, String status) {}
