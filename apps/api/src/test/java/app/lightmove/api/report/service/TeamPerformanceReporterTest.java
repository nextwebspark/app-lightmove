package app.lightmove.api.report.service;

import static app.lightmove.api.report.service.ReportFixtures.company;
import static app.lightmove.api.report.service.ReportFixtures.executive;
import static app.lightmove.api.report.service.ReportFixtures.sources;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.report.constant.ResearcherRole;
import app.lightmove.api.report.dto.ResearcherDto;
import app.lightmove.api.report.dto.TeamPerformanceDto;
import app.lightmove.api.report.model.ExecutiveRow;
import app.lightmove.api.report.model.ReportCalendar;
import app.lightmove.api.report.model.ReportRange;
import app.lightmove.api.report.model.ResearcherIdentity;
import app.lightmove.api.report.model.TeamSources;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

/** The researcher breakdown over a mandate three weeks old, read over its last week. */
class TeamPerformanceReporterTest {

    private static final ReportCalendar THREE_WEEKS =
            new ReportCalendar(LocalDate.parse("2026-07-05"), LocalDate.parse("2026-07-25"), null);
    private static final ReportRange LAST_WEEK =
            new ReportRange(LocalDate.parse("2026-07-19"), LocalDate.parse("2026-07-25"));

    private final UUID yara = UUID.randomUUID();
    private final UUID omar = UUID.randomUUID();
    private final UUID leaver = UUID.randomUUID();

    @Test
    @DisplayName("the range narrows each researcher's figures; coverage stays the mandate as it stands")
    void rangeNarrowsResearchersNotCoverage() {
        TriageCompanyResponse almarai = company("Almarai", "food & beverages");
        TriageCompanyResponse nadec = company("NADEC", "food & beverages");
        ExecutiveRow early = executive("Early", almarai, "C-Suite", null, null, at("2026-07-06"));
        ExecutiveRow late = executive("Late", almarai, "N-1", null, null, at("2026-07-20"));
        ExecutiveRow other = executive("Other", nadec, "N-1", null, null, at("2026-07-24"));
        TeamSources team = team(Map.of(early.executive().id(), omar, late.executive().id(), yara,
                other.executive().id(), yara));

        TeamPerformanceDto report = new TeamPerformanceReporter().report(
                sources(List.of(almarai, nadec), List.of(early, late, other)), THREE_WEEKS, LAST_WEEK, team);

        assertThat(report.days()).isEqualTo(7);
        assertThat(report.kpis().executivesInRange()).isEqualTo(2);
        assertThat(report.kpis().rangePerWeek()).isEqualTo(2.0);
        assertThat(report.kpis().coveredCompanies()).isEqualTo(2);

        ResearcherDto first = report.researchers().getFirst();
        assertThat(first.name()).isEqualTo("Yara Haddad");
        assertThat(first.executives()).isEqualTo(2);
        assertThat(first.sharePct()).isEqualTo(100);
        assertThat(first.daily()).containsExactly(0, 1, 0, 0, 0, 1, 0);

        ResearcherDto quiet = report.researchers().get(1);
        assertThat(quiet.name()).isEqualTo("Omar Khoury");
        assertThat(quiet.executives()).isZero();
        assertThat(quiet.quality()).isNull();
        assertThat(quiet.lastAddedAt()).isEqualTo(at("2026-07-06"));

        // Almarai went to Omar, who filed its first executive, although Yara filed there in range.
        assertThat(report.coverage()).extracting("name", "companies")
                .containsExactlyInAnyOrder(Tuple.tuple("Omar Khoury", 1),
                        Tuple.tuple("Yara Haddad", 1));
    }

    @Test
    @DisplayName("someone who filed and left is kept as a former member only while they have work in range")
    void formerMembersAppearOnlyWithWork() {
        TriageCompanyResponse almarai = company("Almarai", null);
        ExecutiveRow theirs = executive("Theirs", almarai, null, null, null, at("2026-07-21"));

        TeamPerformanceDto inRange = new TeamPerformanceReporter().report(sources(List.of(almarai), List.of(theirs)),
                THREE_WEEKS, LAST_WEEK, team(Map.of(theirs.executive().id(), leaver)));
        assertThat(inRange.researchers()).extracting(ResearcherDto::role)
                .contains(ResearcherRole.FORMER);

        ReportRange firstWeek = new ReportRange(LocalDate.parse("2026-07-05"), LocalDate.parse("2026-07-11"));
        TeamPerformanceDto outOfRange = new TeamPerformanceReporter().report(sources(List.of(almarai), List.of(theirs)),
                THREE_WEEKS, firstWeek, team(Map.of(theirs.executive().id(), leaver)));
        assertThat(outOfRange.researchers()).extracting(ResearcherDto::role)
                .doesNotContain(ResearcherRole.FORMER);
    }

    @Test
    @DisplayName("shares are rounded so the column adds up to the Total row's 100%")
    void sharesSumToHundred() {
        TriageCompanyResponse almarai = company("Almarai", null);
        ExecutiveRow first = executive("First", almarai, null, null, null, at("2026-07-20"));
        ExecutiveRow second = executive("Second", almarai, null, null, null, at("2026-07-21"));
        ExecutiveRow third = executive("Third", almarai, null, null, null, at("2026-07-22"));

        TeamPerformanceDto report = new TeamPerformanceReporter().report(
                sources(List.of(almarai), List.of(first, second, third)), THREE_WEEKS, LAST_WEEK,
                team(Map.of(first.executive().id(), yara, second.executive().id(), omar,
                        third.executive().id(), leaver)));

        assertThat(report.researchers()).extracting(ResearcherDto::sharePct).containsExactlyInAnyOrder(34, 33, 33);
    }

    @Test
    @DisplayName("a requested range is clamped into the calendar, and a backwards one answers nothing")
    void rangeIsClamped() {
        ReportRange clamped = ReportRange.within(THREE_WEEKS, LocalDate.parse("2026-01-01"), LocalDate.parse("2027-01-01"));
        assertThat(clamped).isEqualTo(new ReportRange(THREE_WEEKS.kickoff(), THREE_WEEKS.asOf()));
        assertThat(ReportRange.within(THREE_WEEKS, null, null).days()).isEqualTo(21);
        assertThat(ReportRange.within(THREE_WEEKS, LocalDate.parse("2026-07-20"), LocalDate.parse("2026-07-10"))).isNull();
    }

    private TeamSources team(Map<UUID, UUID> addedBy) {
        Map<UUID, ResearcherIdentity> researchers = new LinkedHashMap<>();
        researchers.put(omar, new ResearcherIdentity(omar, "Omar Khoury", null, ResearcherRole.RESEARCHER));
        researchers.put(yara, new ResearcherIdentity(yara, "Yara Haddad", null, ResearcherRole.LEAD));
        if (addedBy.containsValue(leaver)) {
            researchers.put(leaver, new ResearcherIdentity(leaver, "Former Colleague", null, ResearcherRole.FORMER));
        }
        return new TeamSources(addedBy, researchers);
    }

    private static Instant at(String date) {
        return Instant.parse(date + "T09:00:00Z");
    }
}
