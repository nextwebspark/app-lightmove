package app.lightmove.api.dataimport.service;

import static app.lightmove.api.dataimport.constant.ImportTargetField.*;

import app.lightmove.api.candidate.dto.CandidateCompensationDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.dto.SaveCandidateRequest;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.triagecompany.dto.CaptureCompanyRequest;
import app.lightmove.api.triagecompany.dto.EditTriageCompanyRequest;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Builds the Companies drawer's own requests from one spreadsheet row, laid over what is stored. */
@Component
class ImportRequestBuilder {

    CaptureCompanyRequest captureRequestFor(String companyName, RowFields fields) {
        return new CaptureCompanyRequest(
                RowValues.text(companyName, 200),
                // The import is its own door, recorded so the grid's Source badge can say a figure
                // came out of somebody's spreadsheet rather than out of the market.
                "csv",
                "inUniverse",
                RowValues.text(fields.field(COMPANY_INDUSTRY), 200),
                RowValues.text(fields.field(COMPANY_COUNTRY), 100),
                RowValues.text(fields.field(COMPANY_CITY), 100),
                RowValues.integer(fields.field(COMPANY_EMPLOYEES)),
                RowValues.number(fields.field(COMPANY_REVENUE)),
                foundedYearOf(fields),
                RowValues.text(fields.field(COMPANY_WEBSITE), 500),
                RowValues.text(fields.field(COMPANY_LINKEDIN), 500),
                RowValues.text(fields.field(COMPANY_DESCRIPTION), 2000),
                null,
                RowValues.text(fields.field(COMPANY_NOTE), 2000),
                fields.customValues(CustomColumnTarget.COMPANY));
    }

    /**
     * The stored row with the file's non-blank cells laid over it — an edit replaces a company whole,
     * so anything the file does not carry has to be restated or it is lost.
     */
    EditTriageCompanyRequest editRequestFor(TriageCompanyResponse held, String companyName, RowFields fields) {
        return new EditTriageCompanyRequest(
                firstOf(RowValues.text(companyName, 200), held.companyName()),
                firstOf(RowValues.text(fields.field(COMPANY_INDUSTRY), 200), held.industry()),
                firstOf(RowValues.text(fields.field(COMPANY_COUNTRY), 100), held.companyCountry()),
                firstOf(RowValues.text(fields.field(COMPANY_CITY), 100), held.companyCity()),
                firstOf(RowValues.integer(fields.field(COMPANY_EMPLOYEES)), held.numEmployees()),
                firstOf(RowValues.number(fields.field(COMPANY_REVENUE)), held.annualRevenue()),
                firstOf(foundedYearOf(fields), held.foundedYear()),
                firstOf(RowValues.text(fields.field(COMPANY_WEBSITE), 500), held.website()),
                firstOf(RowValues.text(fields.field(COMPANY_LINKEDIN), 500), held.companyLinkedinUrl()),
                firstOf(RowValues.text(fields.field(COMPANY_DESCRIPTION), 2000), held.shortDescription()),
                fields.customValues(CustomColumnTarget.COMPANY));
    }

    /**
     * The same overlay for a person. {@code held} is null when creating, in which case every stored
     * value is simply absent and the file's own cells stand alone.
     */
    SaveCandidateRequest candidateRequestFor(CandidateResponse held, UUID triageCompanyId,
                                             String companyName, String personName, RowFields fields) {
        return new SaveCandidateRequest(
                triageCompanyId,
                overlay(RowValues.text(personName, 200), held, CandidateResponse::fullName),
                overlay(RowValues.text(fields.field(CANDIDATE_TITLE), 200), held, CandidateResponse::title),
                overlay(RowValues.seniority(fields.field(CANDIDATE_SENIORITY)), held, CandidateResponse::seniority),
                // Never from the file: a "status" column in somebody's spreadsheet is their pipeline,
                // and overwriting this mandate's own decision with it would undo a researcher's work.
                storedOf(held, CandidateResponse::status),
                overlay(RowValues.text(companyName, 200), held, CandidateResponse::companyName),
                // A cell's address or number joins the person's ledger; what they already hold is
                // never re-sent, because the ledger keeps it regardless.
                RowValues.text(fields.field(CANDIDATE_EMAIL), 320),
                RowValues.text(fields.field(CANDIDATE_PHONE), 50),
                null,
                null,
                overlay(RowValues.text(fields.field(CANDIDATE_LINKEDIN), 500), held, CandidateResponse::linkedinUrl),
                overlay(RowValues.text(fields.field(CANDIDATE_COUNTRY), 100), held, CandidateResponse::locationCountry),
                overlay(RowValues.text(fields.field(CANDIDATE_CITY), 100), held, CandidateResponse::locationCity),
                overlay(RowValues.text(fields.field(CANDIDATE_NATIONALITY), 100), held, CandidateResponse::nationality),
                overlay(RowValues.gender(fields.field(CANDIDATE_GENDER)), held, CandidateResponse::gender),
                overlay(yearsExperienceOf(fields), held, CandidateResponse::yearsExperience),
                overlay(RowValues.text(fields.field(CANDIDATE_SUMMARY), 4000), held, CandidateResponse::summary),
                overlay(RowValues.text(fields.field(CANDIDATE_NOTE), 2000), held, CandidateResponse::note),
                compensationFor(held, fields),
                storedOf(held, CandidateResponse::career),
                storedOf(held, CandidateResponse::languages),
                held == null ? "csv" : held.source(),
                storedOf(held, CandidateResponse::sourceUrl),
                fields.customValues(CustomColumnTarget.CANDIDATE),
                null);
    }

    private static CandidateCompensationDto compensationFor(CandidateResponse held, RowFields fields) {
        CandidateCompensationDto stored = storedOf(held, CandidateResponse::compensation);
        return new CandidateCompensationDto(
                overlay(RowValues.currency(fields.field(CANDIDATE_CURRENCY)), stored, CandidateCompensationDto::currency),
                overlay(RowValues.number(fields.field(CANDIDATE_BASE_SALARY)), stored, CandidateCompensationDto::baseSalary),
                overlay(RowValues.number(fields.field(CANDIDATE_BONUS)), stored, CandidateCompensationDto::bonus),
                overlay(RowValues.number(fields.field(CANDIDATE_ALLOWANCES)), stored, CandidateCompensationDto::allowances),
                overlay(RowValues.number(fields.field(CANDIDATE_LONG_TERM_INCENTIVE)), stored,
                        CandidateCompensationDto::longTermIncentive),
                overlay(RowValues.noticePeriod(fields.field(CANDIDATE_NOTICE_PERIOD)), stored,
                        CandidateCompensationDto::noticePeriod),
                storedOf(stored, CandidateCompensationDto::allowanceLines),
                storedOf(stored, CandidateCompensationDto::longTermIncentiveTypes));
    }

    /** Refused rather than truncated by the DTO's own {@code @Max}: a bad year is not a year. */
    private static Integer foundedYearOf(RowFields fields) {
        Integer year = RowValues.integer(fields.field(COMPANY_FOUNDED));
        return year == null || year < 1800 || year > 2100 ? null : year;
    }

    private static Integer yearsExperienceOf(RowFields fields) {
        Integer years = RowValues.integer(fields.field(CANDIDATE_YEARS_EXPERIENCE));
        return years == null || years < 0 || years > 70 ? null : years;
    }

    private static <T> T firstOf(T fromFile, T stored) {
        return fromFile != null ? fromFile : stored;
    }

    private static <S, T> T overlay(T fromFile, S held, Function<S, T> stored) {
        return firstOf(fromFile, storedOf(held, stored));
    }

    private static <S, T> T storedOf(S held, Function<S, T> stored) {
        return held == null ? null : stored.apply(held);
    }
}
