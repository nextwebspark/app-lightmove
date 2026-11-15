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
import app.lightmove.api.common.location.model.InternationalPhone;
import app.lightmove.api.common.location.service.PhoneNumbers;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A mapped executive as the drawer and the grid read it — a client seat included. */
@Component
@RequiredArgsConstructor
class CandidateResponseMapper {

    private final PersonEmployerResolver employers;
    private final WorkspaceSettingsService workspaces;

    CandidateResponse toDto(Candidate candidate) {
        UUID workspaceId = candidate.getPerson().getWorkspaceId();
        return toDto(candidate, employers.logoOfMapping(workspaceId, candidate), workspaces.companyCountryOf(workspaceId));
    }

    /** A page of one position's rows, their logos read in one query rather than one a row. */
    List<CandidateResponse> toDtos(List<Candidate> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        UUID workspaceId = rows.getFirst().getPerson().getWorkspaceId();
        Map<UUID, String> logos = employers.logosOfMappings(workspaceId, rows);
        String workspaceCountry = workspaces.companyCountryOf(workspaceId);
        return rows.stream().map(row -> toDto(row, logos.get(row.getId()), workspaceCountry)).toList();
    }

    /** Every email and phone on the person's ledger, a local number read against the firm's country as a last guess. */
    CandidateContactsDto contactsOf(Person person) {
        return contactsOf(person, workspaces.companyCountryOf(person.getWorkspaceId()));
    }

    private CandidateResponse toDto(Candidate candidate, String companyLogoUrl, String workspaceCountry) {
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
                contactsOf(person, workspaceCountry),
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

    private static CandidateContactsDto contactsOf(Person person, String workspaceCountry) {
        List<CandidateContact> emails = person.emailContacts();
        List<CandidateContact> phones = person.phoneContacts();
        return new CandidateContactsDto(
                emails.stream().map(CandidateResponseMapper::emailOf).toList(),
                phones.stream().map(phone -> phoneOf(phone, person.getLocationCountry(), workspaceCountry)).toList(),
                person.getEmailsLookedUpAt(), person.getPhonesLookedUpAt(),
                person.getContactsLookedUpVia());
    }

    private static CandidateEmailDto emailOf(CandidateContact contact) {
        return new CandidateEmailDto(contact.getValue(), kindOf(contact), contact.isVerified(), contact.getStatus(),
                contact.getSource().value(), contact.getFoundAt(), contact.isPrimary());
    }

    private static CandidatePhoneDto phoneOf(CandidateContact contact, String personCountry, String workspaceCountry) {
        Optional<InternationalPhone> international =
                PhoneNumbers.internationalOf(contact.getValue(), personCountry, workspaceCountry);
        return new CandidatePhoneDto(contact.getValue(), kindOf(contact), contact.isVerified(), contact.getStatus(),
                contact.getSource().value(), contact.getFoundAt(), contact.isPrimary(),
                international.map(InternationalPhone::international).orElse(null),
                international.map(InternationalPhone::e164).orElse(null),
                international.map(InternationalPhone::callingCode).orElse(null),
                international.map(InternationalPhone::countrySource).orElse(null),
                international.map(InternationalPhone::lineType).orElse(null));
    }

    private static String kindOf(CandidateContact contact) {
        return contact.getKind() == null ? null : contact.getKind().value();
    }
}
