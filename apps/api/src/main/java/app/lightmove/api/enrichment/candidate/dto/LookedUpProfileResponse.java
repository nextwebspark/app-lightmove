package app.lightmove.api.enrichment.candidate.dto;

import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import java.util.List;
import java.util.UUID;

/**
 * What research read off a profile, in the Add form's fields. The photo travels inline as a {@code data:} URL: it is
 * not stored until the save, so there is no address to serve it from. {@code employerTriageCompanyId} is the company
 * row this position already holds for the employer, null when the save would file it into the universe.
 */
public record LookedUpProfileResponse(String linkedinUrl, String fullName, String title, String summary,
                                      String employerName, String employerLinkedinUrl, String employerLogoUrl,
                                      UUID employerTriageCompanyId,
                                      String locationCity, String locationCountry,
                                      List<CandidateCareerEntryDto> career, List<String> languages,
                                      String photoDataUrl) {}
