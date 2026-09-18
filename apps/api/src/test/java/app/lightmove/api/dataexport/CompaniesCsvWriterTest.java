package app.lightmove.api.dataexport;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.dto.CandidateContactsDto;
import app.lightmove.api.candidate.dto.CandidateEmailDto;
import app.lightmove.api.candidate.dto.CandidatePhoneDto;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.dataexport.model.ExportRow;
import app.lightmove.api.dataexport.service.CompaniesCsvWriter;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Companies grid as a file: the columns it carries, and the ways a cell can go wrong. */
class CompaniesCsvWriterTest {

    private final CompaniesCsvWriter writer = new CompaniesCsvWriter();

    @Test
    @DisplayName("writes the grid's own headers, in the grid's own order")
    void writesTheGridsHeaders() {
        assertThat(headersOf(writer.write(List.of(), List.of())))
                .containsExactly("Company", "Website", "Company LinkedIn", "Country", "Executive",
                        "Title", "Email", "Phone", "Status", "Sector", "City", "Revenue", "Employees",
                        "Note", "Founded", "Added", "Description", "Source");
    }

    @Test
    @DisplayName("appends this mandate's own columns after the built-ins, and leaves a hidden one out")
    void appendsCustomColumns() {
        List<String> headers = headersOf(writer.write(List.of(
                new CustomColumnDto("c1", "candidate", "ethnicity", "Ethnicity", "text", 0, false),
                new CustomColumnDto("c2", "company", "tier", "Tier", "text", 1, true)), List.of()));

        assertThat(headers).endsWith("Ethnicity").doesNotContain("Tier");
    }

    @Test
    @DisplayName("reads a custom value off whichever half of the row its column belongs to")
    void readsCustomValuesOffTheRightHalf() {
        String csv = writer.write(
                List.of(new CustomColumnDto("c1", "candidate", "ethnicity", "Ethnicity", "text", 0, false),
                        new CustomColumnDto("c2", "company", "tier", "Tier", "text", 1, false)),
                List.of(row(company("ACWA Power").withCustom(Map.of("tier", "A")),
                        person("Layla Haddad").withCustom(Map.of("ethnicity", "Levantine")))));

        assertThat(cellsOf(csv, 1)).endsWith("Levantine", "A");
    }

    @Test
    @DisplayName("a company with nobody mapped keeps a line, with the executive cells empty")
    void writesACompanyWithNoExecutive() {
        List<String> cells = cellsOf(writer.write(List.of(),
                List.of(row(company("ACWA Power"), null))), 1);

        assertThat(cells.get(0)).isEqualTo("ACWA Power");
        assertThat(cells.get(4)).isEmpty();
        assertThat(cells.get(5)).isEmpty();
    }

    @Test
    @DisplayName("an executive with no company in the universe keeps their snapshotted employer name")
    void writesAnUnmappedExecutive() {
        List<String> cells = cellsOf(writer.write(List.of(),
                List.of(row(null, person("Yasmin Farouk").at("Masdar")))), 1);

        assertThat(cells.get(0)).isEqualTo("Masdar");
        assertThat(cells.get(4)).isEqualTo("Yasmin Farouk");
        assertThat(cells.get(17)).isEmpty();
    }

    @Test
    @DisplayName("the person's own location wins as a unit, so a city never borrows the employer's country")
    void takesTheLocationAsAUnit() {
        List<String> cells = cellsOf(writer.write(List.of(), List.of(row(
                company("ACWA Power").at("Saudi Arabia", "Riyadh"),
                person("Layla Haddad").in(null, "Dubai")))), 1);

        assertThat(cells.get(3)).isEmpty();
        assertThat(cells.get(10)).isEqualTo("Dubai");
    }

    @Test
    @DisplayName("falls back to the company's HQ where the person has no location at all")
    void fallsBackToTheCompanysHq() {
        List<String> cells = cellsOf(writer.write(List.of(), List.of(row(
                company("ACWA Power").at("Saudi Arabia", "Riyadh"),
                person("Layla Haddad")))), 1);

        assertThat(cells.get(3)).isEqualTo("Saudi Arabia");
        assertThat(cells.get(10)).isEqualTo("Riyadh");
    }

    @Test
    @DisplayName("lists every email and phone in one cell — the grid's +N has no meaning in a file")
    void listsEveryContact() {
        List<String> cells = cellsOf(writer.write(List.of(), List.of(row(
                company("ACWA Power"),
                person("Layla Haddad")
                        .withEmails("layla@acwapower.com", "layla.haddad@gmail.com")
                        .withPhones("+966 50 123 4567")))), 1);

        assertThat(cells.get(6)).isEqualTo("layla@acwapower.com; layla.haddad@gmail.com");
        assertThat(cells.get(7)).isEqualTo("'+966 50 123 4567");
    }

    @Test
    @DisplayName("figures stay bare, so a spreadsheet can sum the column")
    void leavesFiguresUnformatted() {
        List<String> cells = cellsOf(writer.write(List.of(), List.of(row(
                company("ACWA Power").sized(3000, 2_400_000_000L, 2004), null))), 1);

        assertThat(cells.get(11)).isEqualTo("2400000000");
        assertThat(cells.get(12)).isEqualTo("3000");
        assertThat(cells.get(14)).isEqualTo("2004");
    }

    @Test
    @DisplayName("prints the badges' labels rather than the wire tokens nobody wants in a file")
    void printsHumanLabels() {
        List<String> cells = cellsOf(writer.write(List.of(), List.of(row(
                company("ACWA Power").from("extension"),
                person("Layla Haddad").standing("notInterested")))), 1);

        assertThat(cells.get(8)).isEqualTo("Not interested");
        assertThat(cells.get(17)).isEqualTo("Plugin");
    }

    @Test
    @DisplayName("dates are ISO, so a spreadsheet reads them as dates whatever locale opens the file")
    void writesIsoDates() {
        List<String> cells = cellsOf(writer.write(List.of(), List.of(row(
                company("ACWA Power").added(Instant.parse("2026-09-16T22:30:00Z")), null))), 1);

        assertThat(cells.get(15)).isEqualTo("2026-09-16");
    }

    @Test
    @DisplayName("quotes a value holding a comma, so Excel does not split it into two columns")
    void quotesWhereItMust() {
        String csv = writer.write(List.of(), List.of(row(company("ACWA Power, Ltd"), null)));

        assertThat(csv).contains("\"ACWA Power, Ltd\"");
        assertThat(cellsOf(csv, 1).get(0)).isEqualTo("ACWA Power, Ltd");
    }

    @Test
    @DisplayName("defuses a cell Excel would run as a formula")
    void defusesAFormula() {
        // A note is free text a user typed. Opened in Excel, a leading `=` is executed — this file
        // leaves the product and is opened by clients, so the cell has to reach them as text.
        List<String> cells = cellsOf(writer.write(List.of(), List.of(row(
                company("ACWA Power").noted("=HYPERLINK(\"http://evil.example\",\"Click\")"), null))), 1);

        assertThat(cells.get(13)).startsWith("'=HYPERLINK");
    }

    @Test
    @DisplayName("opens with a BOM, or Excel on Windows renders every accented name as mojibake")
    void writesAByteOrderMark() {
        assertThat(writer.write(List.of(), List.of())).startsWith("﻿");
    }

    @Test
    @DisplayName("ends every line with CRLF, as RFC 4180 asks")
    void writesCrlf() {
        assertThat(writer.write(List.of(), List.of(row(company("ACWA Power"), null))))
                .endsWith("\r\n")
                .containsPattern("ACWA Power[^\\n]*\\r\\n");
    }

    /** An {@link ExportRow} off the two builders below, either side of which may be absent. */
    private static ExportRow row(CompanyBuilder company, PersonBuilder person) {
        return new ExportRow(company == null ? null : company.response(),
                person == null ? null : person.response());
    }

    private static CompanyBuilder company(String name) {
        return new CompanyBuilder(new TriageCompanyResponse(UUID.randomUUID(), null, "manual",
                "inUniverse", null, false, name, null, null, null, null, null, null, null, null, null,
                null, null, Map.of(), null));
    }

    private static PersonBuilder person(String name) {
        return new PersonBuilder(new CandidateResponse(UUID.randomUUID(), null, null, name, null, null,
                "identified", null, null, null, null, null, null, null, null, null, List.of(), List.of(),
                List.of(), List.of(), "manual", null, Map.of(), null, null,
                new CandidateContactsDto(List.of(), List.of(), null, null, null)));
    }

    /**
     * Wrappers rather than a raw record literal per case: these two DTOs carry twenty and twenty-six
     * components, and a test that has to count commas to change a city tests nothing well. Each
     * builder funnels through one {@code with} so a new component on either record is one edit here,
     * not one per case.
     */
    private record CompanyBuilder(TriageCompanyResponse response) {

        CompanyBuilder at(String country, String city) {
            return with(country, city, response.numEmployees(), response.annualRevenue(),
                    response.foundedYear(), response.source(), response.note(), response.addedAt(),
                    response.customFields());
        }

        CompanyBuilder sized(Integer employees, Long revenue, Integer founded) {
            return with(response.companyCountry(), response.companyCity(), employees, revenue, founded,
                    response.source(), response.note(), response.addedAt(), response.customFields());
        }

        CompanyBuilder from(String source) {
            return with(response.companyCountry(), response.companyCity(), response.numEmployees(),
                    response.annualRevenue(), response.foundedYear(), source, response.note(),
                    response.addedAt(), response.customFields());
        }

        CompanyBuilder noted(String note) {
            return with(response.companyCountry(), response.companyCity(), response.numEmployees(),
                    response.annualRevenue(), response.foundedYear(), response.source(), note,
                    response.addedAt(), response.customFields());
        }

        CompanyBuilder added(Instant addedAt) {
            return with(response.companyCountry(), response.companyCity(), response.numEmployees(),
                    response.annualRevenue(), response.foundedYear(), response.source(),
                    response.note(), addedAt, response.customFields());
        }

        CompanyBuilder withCustom(Map<String, String> customFields) {
            return with(response.companyCountry(), response.companyCity(), response.numEmployees(),
                    response.annualRevenue(), response.foundedYear(), response.source(),
                    response.note(), response.addedAt(), customFields);
        }

        private CompanyBuilder with(String country, String city, Integer employees, Long revenue,
                                    Integer founded, String source, String note, Instant addedAt,
                                    Map<String, String> customFields) {
            return new CompanyBuilder(new TriageCompanyResponse(response.id(), response.apolloAccountId(),
                    source, response.status(), note, response.noExecutiveFound(), response.companyName(),
                    response.industry(), country, city, employees, revenue, response.website(),
                    response.companyLinkedinUrl(), founded, response.shortDescription(),
                    response.sourceUrl(), response.logoUrl(), customFields, addedAt));
        }
    }

    private record PersonBuilder(CandidateResponse response) {

        PersonBuilder at(String companyName) {
            return with(companyName, response.status(), response.locationCountry(),
                    response.locationCity(), response.customFields(), response.contacts());
        }

        PersonBuilder in(String country, String city) {
            return with(response.companyName(), response.status(), country, city,
                    response.customFields(), response.contacts());
        }

        PersonBuilder standing(String status) {
            return with(response.companyName(), status, response.locationCountry(),
                    response.locationCity(), response.customFields(), response.contacts());
        }

        PersonBuilder withCustom(Map<String, String> customFields) {
            return with(response.companyName(), response.status(), response.locationCountry(),
                    response.locationCity(), customFields, response.contacts());
        }

        PersonBuilder withEmails(String... addresses) {
            List<CandidateEmailDto> emails = Arrays.stream(addresses)
                    .map(address -> new CandidateEmailDto(address, null, false, null, "manual", null))
                    .toList();
            return with(response.companyName(), response.status(), response.locationCountry(),
                    response.locationCity(), response.customFields(),
                    new CandidateContactsDto(emails, response.contacts().phones(), null, null, null));
        }

        PersonBuilder withPhones(String... numbers) {
            List<CandidatePhoneDto> phones = Arrays.stream(numbers)
                    .map(number -> new CandidatePhoneDto(number, null, false, null, "manual", null))
                    .toList();
            return with(response.companyName(), response.status(), response.locationCountry(),
                    response.locationCity(), response.customFields(),
                    new CandidateContactsDto(response.contacts().emails(), phones, null, null, null));
        }

        private PersonBuilder with(String companyName, String status, String country, String city,
                                   Map<String, String> customFields, CandidateContactsDto contacts) {
            return new PersonBuilder(new CandidateResponse(response.id(), response.triageCompanyId(),
                    companyName, response.fullName(), response.title(), response.seniority(), status,
                    response.linkedinUrl(), country, city, response.nationality(), response.gender(),
                    response.yearsExperience(), response.summary(), response.note(),
                    response.compensation(), response.career(), response.languages(),
                    response.education(), response.skills(), response.source(), response.sourceUrl(),
                    customFields, response.addedAt(), response.enrichedAt(), contacts));
        }
    }

    private static List<String> headersOf(String csv) {
        return cellsOf(csv, 0);
    }

    /** Splits one line the way a CSV reader would, honouring the quoting and dropping the BOM. */
    private static List<String> cellsOf(String csv, int line) {
        String row = csv.replace("﻿", "").lines().toList().get(line);
        return Arrays.stream(row.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1))
                .map(value -> value.startsWith("\"") && value.endsWith("\"") && value.length() > 1
                        ? value.substring(1, value.length() - 1).replace("\"\"", "\"")
                        : value)
                .toList();
    }
}
