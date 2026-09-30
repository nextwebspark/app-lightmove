package app.lightmove.api.strategy.dto;

import java.util.List;

/**
 * The People sidebar's closed vocabularies: ContactOut's accepted values, read verbatim from its sheet.
 * Industries are its LinkedIn names, the V2 spellings and a few V1 ones alike, sent exactly as listed.
 */
public record PeopleFacetsResponse(List<VocabularyOption> seniorities, List<VocabularyOption> jobFunctions,
                                   List<VocabularyOption> companySizes, List<VocabularyOption> yearsOfExperience,
                                   List<VocabularyOption> yearsInCurrentRole,
                                   List<VocabularyOption> languageProficiencies, List<String> industries) {}
