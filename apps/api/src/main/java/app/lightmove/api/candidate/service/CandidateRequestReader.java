package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.constant.ContactChannel;
import app.lightmove.api.candidate.constant.ContactKind;
import app.lightmove.api.candidate.constant.Gender;
import app.lightmove.api.candidate.constant.LongTermIncentiveType;
import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.ContactEntryDto;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.candidate.model.AllowanceLine;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateContact;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.CompensationBreakdown;
import app.lightmove.api.candidate.model.ContactEntry;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads a candidate write's request into model values, holding its contact lists to the ledger's rules. */
@Component
@RequiredArgsConstructor
class CandidateRequestReader {

    private static final int MAX_CONTACTS_PER_CHANNEL = 10;

    private final TriageCompanyService triage;

    /**
     * Where a candidate sits. A named company is resolved through {@code triagecompany}'s public seam,
     * which proves it belongs to this mandate — so one cannot be filed against another project's — and
     * clears that company's {@code noExecutiveFound} flag as a side effect of the resolution, but only
     * when {@code previousTriageCompanyId} shows this call is newly making that mapping: an executive
     * being mapped here is what disproves the flag, but a save that merely still names the company they
     * were already mapped to is an unrelated edit and must not revive it.
     */
    CandidateDetails detailsOf(UUID projectId, SaveCandidateRequest request, UUID previousTriageCompanyId) {
        CandidateDetails details = new CandidateDetails(
                request.fullName(), request.title(), resolveSeniority(request.seniority()),
                resolveStatus(request.status()), request.employerName(),
                entriesOf(ContactChannel.EMAIL, request.emails(), request.email()),
                entriesOf(ContactChannel.PHONE, request.phones(), request.phone()),
                request.linkedinUrl(), request.locationCountry(),
                request.locationCity(), request.nationality(), resolveGender(request.gender()),
                request.yearsExperience(),
                request.summary(), request.note(), compensationOf(request.compensation()),
                profileOf(request), request.sourceUrl());

        if (request.triageCompanyId() == null) {
            return details;
        }
        boolean newMapping = !request.triageCompanyId().equals(previousTriageCompanyId);
        TriageCompanyResponse company =
                triage.requireCompanyOfProject(projectId, request.triageCompanyId(), newMapping);
        return details.employedAt(company.companyName());
    }

    /**
     * What a write lists for one channel, plus the one value a cell or a capture supplies, as ledger
     * entries — the Contact section's save and the profile's own both come through here, so the rules
     * below cannot differ by endpoint. A single value that keys to nothing — a dash where a number
     * should be — is skipped rather than refused, as it always was: a spreadsheet says "unknown" a
     * dozen ways.
     */
    List<ContactEntry> entriesOf(ContactChannel channel, List<ContactEntryDto> listed, String single) {
        List<ContactEntry> entries = new ArrayList<>();
        if (listed != null) {
            listed.forEach(entry -> entries.add(entryOf(channel, entry)));
        }
        if (single != null && !CandidateContact.keyOf(channel, single).isEmpty()) {
            entries.add(ContactEntry.of(single));
        }
        return distinct(channel, entries);
    }

    /**
     * Two spellings of one address or number in the same save is a slip, and letting the second win
     * silently would hide it; ten of either is a paste error.
     */
    private static List<ContactEntry> distinct(ContactChannel channel, List<ContactEntry> entries) {
        if (entries.size() > MAX_CONTACTS_PER_CHANNEL) {
            throw ApiException.of(ErrorCode.CONTACT_LIMIT_REACHED);
        }
        Set<String> keys = new HashSet<>();
        for (ContactEntry entry : entries) {
            String key = CandidateContact.keyOf(channel, entry.value());
            if (key.isEmpty()) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "That is not " + (channel == ContactChannel.EMAIL ? "an email" : "a phone number") + " anyone could use");
            }
            if (!keys.add(key)) {
                throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "The same " + channel.value() + " is listed twice");
            }
        }
        return entries;
    }

    /** A profile write adds to the ledger and removes nothing, so the cap is checked on what it holds after. */
    void refuseOverfullChannels(Candidate candidate) {
        if (candidate.emailContacts().size() > MAX_CONTACTS_PER_CHANNEL
                || candidate.phoneContacts().size() > MAX_CONTACTS_PER_CHANNEL) {
            throw ApiException.of(ErrorCode.CONTACT_LIMIT_REACHED);
        }
    }

    private static ContactEntry entryOf(ContactChannel channel, ContactEntryDto listed) {
        String value = listed.value() == null ? null : listed.value().trim();
        if (channel == ContactChannel.EMAIL && value != null && !looksLikeAnEmail(value)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That doesn't look like a valid email");
        }
        return new ContactEntry(value, ContactKind.fromValue(listed.kind()), Boolean.TRUE.equals(listed.verified()));
    }

    private static boolean looksLikeAnEmail(String value) {
        int at = value.indexOf('@');
        return at > 0 && at < value.length() - 1 && value.indexOf('@', at + 1) < 0
                && !value.contains(" ");
    }

    private static CandidateCompensation compensationOf(CandidateCompensationDto supplied) {
        if (supplied == null) {
            return CandidateCompensation.unknown();
        }
        return new CandidateCompensation(supplied.currency(), supplied.baseSalary(), supplied.bonus(),
                supplied.allowances(), supplied.longTermIncentive(), supplied.noticePeriod(),
                breakdownOf(supplied));
    }

    private static CompensationBreakdown breakdownOf(CandidateCompensationDto supplied) {
        List<AllowanceLine> lines = supplied.allowanceLines() == null ? List.of()
                : supplied.allowanceLines().stream()
                        .filter(Objects::nonNull)
                        .map(line -> new AllowanceLine(line.label(), line.amount()))
                        .toList();
        List<LongTermIncentiveType> types = supplied.longTermIncentiveTypes() == null ? List.of()
                : supplied.longTermIncentiveTypes().stream().map(CandidateRequestReader::resolveIncentiveType).toList();
        return new CompensationBreakdown(lines, types);
    }

    private static LongTermIncentiveType resolveIncentiveType(String token) {
        return ApiValueEnum.require(LongTermIncentiveType.class, token, "long-term incentive type");
    }

    private static CandidateProfile profileOf(SaveCandidateRequest request) {
        List<CandidateCareerEntry> career = request.career() == null ? List.of()
                : request.career().stream()
                        .map(entry -> new CandidateCareerEntry(entry.company(), entry.title(), entry.period()))
                        .toList();
        return new CandidateProfile(career, request.languages(), null, null, null);
    }

    /** Omitted means identified — where every profile starts, and the only honest default. */
    CandidateStatus resolveStatus(String token) {
        return ApiValueEnum.parse(CandidateStatus.class, token, CandidateStatus.IDENTIFIED, "candidate status");
    }

    /** Null when nobody named a level, which is not the same as naming an unknown one. */
    private static Seniority resolveSeniority(String token) {
        return ApiValueEnum.parse(Seniority.class, token, null, "seniority level");
    }

    /** Null when nobody recorded it. Absent is not {@code OTHER}, and the report counts them apart. */
    private static Gender resolveGender(String token) {
        return ApiValueEnum.parse(Gender.class, token, null, "gender");
    }

    CandidateSource resolveSource(String token) {
        return ApiValueEnum.parse(CandidateSource.class, token, CandidateSource.MANUAL, "candidate source");
    }
}
