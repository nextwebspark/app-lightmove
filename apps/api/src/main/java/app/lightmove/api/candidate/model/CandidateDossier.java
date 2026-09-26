package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.BackgroundField;
import java.util.List;
import java.util.Set;

/**
 * Everything about a candidate the model may read. An allowlist on purpose: contact details,
 * compensation, the note and custom fields never appear here, so they can never reach a prompt —
 * the last two are free text a researcher may well have typed either of the first two into.
 */
public record CandidateDossier(String fullName, String title, String companyName, String locationCity,
                               String locationCountry, String linkedinUrl, String summary,
                               List<CandidateCareerEntry> career, List<CandidateEducationEntry> education,
                               List<String> skills, List<String> languages,
                               Set<BackgroundField> missingBackground) {}
