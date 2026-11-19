package app.lightmove.api.candidate.model;

/** A career post as research described it, with the text the person wrote about it — the career itself stays on the profile. */
public record ResearchedRole(String company, String title, String period, String location, String description) {}
