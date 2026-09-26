package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.LongTermIncentiveType;
import app.lightmove.api.candidate.dto.AllowanceLineDto;
import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateContactsDto;
import app.lightmove.api.candidate.dto.CandidateEducationEntryDto;
import app.lightmove.api.candidate.dto.CandidateEmailDto;
import app.lightmove.api.candidate.dto.CandidatePhoneDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCompensation;
import org.springframework.stereotype.Component;

/** A mapped executive as the drawer and the grid read it — a client seat included. */
@Component
class CandidateResponseMapper {

    CandidateResponse toDto(Candidate candidate) {
        CandidateCompensation compensation = candidate.compensation();
        return new CandidateResponse(
                candidate.getId(),
                candidate.getTriageCompanyId(),
                candidate.getCompanyName(),
                candidate.getFullName(),
                candidate.getTitle(),
                candidate.getSeniorityLevel() == null ? null : candidate.getSeniorityLevel().value(),
                candidate.getStatus().value(),
                candidate.getLinkedinUrl(),
                candidate.getLocationCountry(),
                candidate.getLocationCity(),
                candidate.getNationality(),
                candidate.getGender() == null ? null : candidate.getGender().value(),
                candidate.getYearsExperience(),
                candidate.getAiInferredFields(),
                candidate.getSummary(),
                candidate.getNote(),
                new CandidateCompensationDto(compensation.currency(), compensation.baseSalary(),
                        compensation.bonus(), compensation.allowances(),
                        compensation.longTermIncentive(), compensation.noticePeriod(),
                        compensation.breakdown().allowanceLines().stream()
                                .map(line -> new AllowanceLineDto(line.label(), line.amount()))
                                .toList(),
                        compensation.breakdown().longTermIncentiveTypes().stream()
                                .map(LongTermIncentiveType::value)
                                .toList()),
                candidate.getProfile().career().stream()
                        .map(entry -> new CandidateCareerEntryDto(entry.company(), entry.title(), entry.period()))
                        .toList(),
                candidate.getProfile().languages(),
                candidate.getProfile().education().stream()
                        .map(school -> new CandidateEducationEntryDto(school.school(), school.degree(),
                                school.period()))
                        .toList(),
                candidate.getProfile().skills(),
                candidate.getSource().value(),
                candidate.getSourceUrl(),
                candidate.getCustomFields().asMap(),
                candidate.getCreatedAt(),
                candidate.getProfile().enrichedAt(),
                contactsOf(candidate));
    }

    private static CandidateContactsDto contactsOf(Candidate candidate) {
        return new CandidateContactsDto(
                candidate.emailContacts().stream()
                        .map(contact -> new CandidateEmailDto(contact.getValue(),
                                contact.getKind() == null ? null : contact.getKind().value(),
                                contact.isVerified(), contact.getStatus(),
                                contact.getSource().value(), contact.getFoundAt()))
                        .toList(),
                candidate.phoneContacts().stream()
                        .map(contact -> new CandidatePhoneDto(contact.getValue(),
                                contact.getKind() == null ? null : contact.getKind().value(),
                                contact.isVerified(), contact.getStatus(),
                                contact.getSource().value(), contact.getFoundAt()))
                        .toList(),
                candidate.getEmailsLookedUpAt(), candidate.getPhonesLookedUpAt(),
                candidate.getContactsLookedUpVia());
    }
}
