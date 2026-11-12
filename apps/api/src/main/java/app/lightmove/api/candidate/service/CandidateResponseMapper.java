package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.LongTermIncentiveType;
import app.lightmove.api.candidate.dto.AllowanceLineDto;
import app.lightmove.api.candidate.dto.CandidateCareerEntryDto;
import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateContactsDto;
import app.lightmove.api.candidate.dto.CandidateEducationEntryDto;
import app.lightmove.api.candidate.dto.CandidateEmailDto;
import app.lightmove.api.candidate.dto.CandidatePipelineStageDto;
import app.lightmove.api.candidate.dto.CandidatePhoneDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateStage;
import app.lightmove.api.candidate.model.CandidateStatusDefinition;
import app.lightmove.api.candidate.model.Person;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A mapped executive as the drawer and the grid read it — a client seat included. */
@Component
@RequiredArgsConstructor
class CandidateResponseMapper {

    private final PersonEmployerResolver employers;

    CandidateResponse toDto(Candidate candidate) {
        return toDto(candidate, employers.logoOfMapping(candidate.getPerson().getWorkspaceId(), candidate));
    }

    /** A page of one position's rows, their logos read in one query rather than one a row. */
    List<CandidateResponse> toDtos(List<Candidate> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> logos =
                employers.logosOfMappings(rows.getFirst().getPerson().getWorkspaceId(), rows);
        return rows.stream().map(row -> toDto(row, logos.get(row.getId()))).toList();
    }

    private CandidateResponse toDto(Candidate candidate, String companyLogoUrl) {
        Person person = candidate.getPerson();
        return new CandidateResponse(
                candidate.getId(),
                candidate.getTriageCompanyId(),
                candidate.getCompanyName(),
                person.getFullName(),
                person.getTitle(),
                person.getSeniorityLevel() == null ? null : person.getSeniorityLevel().value(),
                candidate.getStatus().getStatusKey(),
                person.getLinkedinUrl(),
                person.getLocationCountry(),
                person.getLocationCity(),
                person.getNationality(),
                person.getGender() == null ? null : person.getGender().value(),
                person.getYearsExperience(),
                person.getAiInferredFields(),
                person.getSummary(),
                compensationOf(person),
                careerOf(person),
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
                person.getId(),
                person.isLinkedinUrlLocked(),
                candidate.getStatus().getLabel(),
                candidate.getStatus().getColour().value(),
                candidate.getStatus().isOutOfProcess(),
                stageOf(candidate.getStatus()),
                candidate.getStatusChangedAt(),
                candidate.isClientVisible(),
                candidate.getTriageStatus().value(),
                companyLogoUrl);
    }

    static CandidatePipelineStageDto stageOf(CandidateStatusDefinition status) {
        CandidateStage stage = status.getStage();
        return new CandidatePipelineStageDto(stage.getStageKey(), stage.getLabel(), stage.getColour().value(),
                stage.getPosition());
    }

    static CandidateCompensationDto compensationOf(Person person) {
        CandidateCompensation compensation = person.compensation();
        return new CandidateCompensationDto(compensation.currency(), compensation.baseSalary(),
                compensation.bonus(), compensation.allowances(),
                compensation.longTermIncentive(), compensation.noticePeriod(),
                compensation.breakdown().allowanceLines().stream()
                        .map(line -> new AllowanceLineDto(line.label(), line.amount()))
                        .toList(),
                compensation.breakdown().longTermIncentiveTypes().stream()
                        .map(LongTermIncentiveType::value)
                        .toList());
    }

    static List<CandidateCareerEntryDto> careerOf(Person person) {
        return person.getProfile().career().stream()
                .map(entry -> new CandidateCareerEntryDto(entry.company(), entry.title(), entry.period(),
                        entry.location()))
                .toList();
    }

    static CandidateContactsDto contactsOf(Person person) {
        List<CandidateContact> emails = person.emailContacts();
        List<CandidateContact> phones = person.phoneContacts();
        return new CandidateContactsDto(
                emails.stream().map(CandidateResponseMapper::emailOf).toList(),
                phones.stream().map(CandidateResponseMapper::phoneOf).toList(),
                person.getEmailsLookedUpAt(), person.getPhonesLookedUpAt(),
                person.getContactsLookedUpVia());
    }

    private static CandidateEmailDto emailOf(CandidateContact contact) {
        return new CandidateEmailDto(contact.getValue(), kindOf(contact), contact.isVerified(), contact.getStatus(),
                contact.getSource().value(), contact.getFoundAt(), contact.isPrimary());
    }

    private static CandidatePhoneDto phoneOf(CandidateContact contact) {
        return new CandidatePhoneDto(contact.getValue(), kindOf(contact), contact.isVerified(), contact.getStatus(),
                contact.getSource().value(), contact.getFoundAt(), contact.isPrimary());
    }

    private static String kindOf(CandidateContact contact) {
        return contact.getKind() == null ? null : contact.getKind().value();
    }
}
