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
import app.lightmove.api.candidate.model.Person;
import org.springframework.stereotype.Component;

/** A mapped executive as the drawer and the grid read it — a client seat included. */
@Component
class CandidateResponseMapper {

    CandidateResponse toDto(Candidate candidate) {
        Person person = candidate.getPerson();
        CandidateCompensation compensation = person.compensation();
        return new CandidateResponse(
                candidate.getId(),
                candidate.getTriageCompanyId(),
                candidate.getCompanyName(),
                person.getFullName(),
                person.getTitle(),
                person.getSeniorityLevel() == null ? null : person.getSeniorityLevel().value(),
                candidate.getStatus().value(),
                person.getLinkedinUrl(),
                person.getLocationCountry(),
                person.getLocationCity(),
                person.getNationality(),
                person.getGender() == null ? null : person.getGender().value(),
                person.getYearsExperience(),
                person.getAiInferredFields(),
                person.getSummary(),
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
                person.getProfile().career().stream()
                        .map(entry -> new CandidateCareerEntryDto(entry.company(), entry.title(), entry.period(),
                                entry.location()))
                        .toList(),
                person.getProfile().languages(),
                person.getProfile().education().stream()
                        .map(school -> new CandidateEducationEntryDto(school.school(), school.degree(),
                                school.period()))
                        .toList(),
                person.getProfile().skills(),
                candidate.getSource().value(),
                candidate.getSourceUrl(),
                candidate.getCustomFields().asMap(),
                candidate.getCreatedAt(),
                person.getProfile().enrichedAt(),
                contactsOf(person),
                person.getId());
    }

    private static CandidateContactsDto contactsOf(Person person) {
        return new CandidateContactsDto(
                person.emailContacts().stream()
                        .map(contact -> new CandidateEmailDto(contact.getValue(),
                                contact.getKind() == null ? null : contact.getKind().value(),
                                contact.isVerified(), contact.getStatus(),
                                contact.getSource().value(), contact.getFoundAt()))
                        .toList(),
                person.phoneContacts().stream()
                        .map(contact -> new CandidatePhoneDto(contact.getValue(),
                                contact.getKind() == null ? null : contact.getKind().value(),
                                contact.isVerified(), contact.getStatus(),
                                contact.getSource().value(), contact.getFoundAt()))
                        .toList(),
                person.getEmailsLookedUpAt(), person.getPhonesLookedUpAt(),
                person.getContactsLookedUpVia());
    }
}
