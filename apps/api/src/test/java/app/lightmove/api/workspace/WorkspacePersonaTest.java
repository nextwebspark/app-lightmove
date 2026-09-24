package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.workspace.model.WorkspaceCompany;
import app.lightmove.api.workspace.model.WorkspacePersona;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The persona's sectors and country follow the picked firm; what the admin wrote stays. */
class WorkspacePersonaTest {

    private final WorkspaceCompany oilCompany = company("apollo-oil", "oil & energy", "Saudi Arabia");
    private final WorkspaceCompany bank = company("apollo-bank", "banking", "United Arab Emirates");

    @Test
    @DisplayName("signup seeds the firm's industry, its sector and its country")
    void seedsIndustrySectorAndCountry() {
        WorkspacePersona persona = WorkspacePersona.seededFrom(oilCompany);

        assertThat(persona.sectors()).containsExactly("Oil & Energy", "Energy & Utilities");
        assertThat(persona.geographies()).containsExactly("Saudi Arabia");
    }

    @Test
    @DisplayName("a re-pick swaps the previous firm's chips for the next one's and keeps what was typed")
    void repickSwapsFilledChips() {
        WorkspacePersona persona = new WorkspacePersona("Gulf search", List.of("oil & energy", "Energy & Utilities",
                "Family Offices"), List.of("Kalem"), List.of("Saudi Arabia", "GCC"), null);

        WorkspacePersona refiled = persona.refiledFrom(oilCompany, bank);

        assertThat(refiled.sectors()).containsExactly("Banking", "Financial Services", "Family Offices");
        assertThat(refiled.geographies()).containsExactly("United Arab Emirates", "GCC");
        assertThat(refiled.summary()).isEqualTo("Gulf search");
        assertThat(refiled.competitors()).containsExactly("Kalem");
    }

    @Test
    @DisplayName("saving the same firm again leaves the persona as the admin left it")
    void sameCompanyLeavesPersona() {
        WorkspacePersona persona = new WorkspacePersona(null, List.of("Family Offices"), List.of(), List.of(), null);

        assertThat(persona.refiledFrom(oilCompany, oilCompany)).isSameAs(persona);
    }

    @Test
    @DisplayName("typing a name by hand takes the previous firm's chips away and adds none")
    void typedNameOnlyRemoves() {
        WorkspacePersona persona = WorkspacePersona.seededFrom(oilCompany);

        WorkspacePersona refiled = persona.refiledFrom(oilCompany, null);

        assertThat(refiled.sectors()).isEmpty();
        assertThat(refiled.geographies()).isEmpty();
    }

    @Test
    @DisplayName("a full list keeps the firm's chips in front and stays within the request's cap")
    void fullListStaysWithinCap() {
        List<String> typed = IntStream.range(0, WorkspacePersona.MAX_LIST_ITEMS).mapToObj(index -> "Sector " + index).toList();
        WorkspacePersona persona = new WorkspacePersona(null, typed, List.of(), List.of(), null);

        WorkspacePersona refiled = persona.refiledFrom(null, bank);

        assertThat(refiled.sectors()).hasSize(WorkspacePersona.MAX_LIST_ITEMS)
                .startsWith("Banking", "Financial Services");
    }

    private static WorkspaceCompany company(String id, String industry, String country) {
        return new WorkspaceCompany(id, industry, null, country, null, null, null);
    }
}
