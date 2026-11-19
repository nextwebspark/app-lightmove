package app.lightmove.api.candidate.model;

/** One certification, publication, project, award, membership, course, patent, recommendation or post, in whichever terms it came. */
public record ResearchedProfileItem(String title, String subtitle, String period, String url, String description) {}
