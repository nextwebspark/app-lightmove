package app.lightmove.api.common.persona;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.persona.model.HiringCompanyProfile;
import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.common.persona.model.PersonaSeed;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The persona follows the picked firm and keeps what the admin wrote; a business unit reads against its firm. */
class HiringPersonaTest {

    private final PersonaSeed oilCompany = company("apollo-oil", "oil & energy", "Saudi Arabia");
    private final PersonaSeed bank = company("apollo-bank", "banking", "United Arab Emirates");

    @Test
    @DisplayName("signup seeds the firm's industry, its sector and its country")
    void seedsIndustrySectorAndCountry() {
        HiringPersona persona = HiringPersona.seededFrom(oilCompany);

        assertThat(persona.sectors()).containsExactly("Oil & Energy", "Energy & Utilities");
        assertThat(persona.geographies()).containsExactly("Saudi Arabia");
    }

    @Test
    @DisplayName("a re-pick swaps the previous firm's chips for the next one's and keeps what was typed")
    void repickSwapsFilledChips() {
        HiringPersona persona = new HiringPersona("Gulf search", List.of("oil & energy", "Energy & Utilities",
                "Family Offices"), List.of("Kalem"), List.of("Saudi Arabia", "GCC"), null);

        HiringPersona refiled = persona.refiledFrom(oilCompany, bank);

        assertThat(refiled.sectors()).containsExactly("Banking", "Financial Services", "Family Offices");
        assertThat(refiled.geographies()).containsExactly("United Arab Emirates", "GCC");
        assertThat(refiled.summary()).isEqualTo("Gulf search");
        assertThat(refiled.competitors()).containsExactly("Kalem");
    }

    @Test
    @DisplayName("saving the same firm again leaves the persona as the admin left it")
    void sameCompanyLeavesPersona() {
        HiringPersona persona = new HiringPersona(null, List.of("Family Offices"), List.of(), List.of(), null);

        assertThat(persona.refiledFrom(oilCompany, oilCompany)).isSameAs(persona);
    }

    @Test
    @DisplayName("typing a name by hand takes the previous firm's chips away and adds none")
    void typedNameOnlyRemoves() {
        HiringPersona persona = HiringPersona.seededFrom(oilCompany);

        HiringPersona refiled = persona.refiledFrom(oilCompany, null);

        assertThat(refiled.sectors()).isEmpty();
        assertThat(refiled.geographies()).isEmpty();
    }

    @Test
    @DisplayName("a full list keeps the firm's chips in front and stays within the request's cap")
    void fullListStaysWithinCap() {
        List<String> typed = IntStream.range(0, HiringPersona.MAX_LIST_ITEMS).mapToObj(index -> "Sector " + index).toList();
        HiringPersona persona = new HiringPersona(null, typed, List.of(), List.of(), null);

        HiringPersona refiled = persona.refiledFrom(null, bank);

        assertThat(refiled.sectors()).hasSize(HiringPersona.MAX_LIST_ITEMS)
                .startsWith("Banking", "Financial Services");
    }

    @Test
    @DisplayName("a persona with nothing written in it is empty, and one with only notes is not")
    void knowsWhenNothingIsRecorded() {
        assertThat(HiringPersona.empty().hasNothingRecorded()).isTrue();
        assertThat(new HiringPersona(" ", List.of(" "), null, List.of(), "").hasNothingRecorded()).isTrue();
        HiringPersona notesOnly = new HiringPersona(null, List.of(), List.of(), List.of(), "Prefers GCC nationals");
        assertThat(notesOnly.hasNothingRecorded()).isFalse();
    }

    @Test
    @DisplayName("a business unit takes each list it left empty from its firm, and never the firm's summary or notes")
    void unitFallsBackListByList() {
        HiringPersona firm = new HiringPersona("Gulf retail group", List.of("Retail"), List.of("Landmark Group"),
                List.of("UAE", "KSA"), "Promotes from within");
        HiringPersona unit = new HiringPersona(null, List.of("Hospitals"), List.of(), List.of(), null);

        HiringPersona read = unit.fallingBackTo(firm);

        assertThat(read.sectors()).containsExactly("Hospitals");
        assertThat(read.competitors()).containsExactly("Landmark Group");
        assertThat(read.geographies()).containsExactly("UAE", "KSA");
        assertThat(read.summary()).isNull();
        assertThat(read.notes()).isNull();
        assertThat(unit.fallingBackTo(null)).isSameAs(unit);
    }

    @Test
    @DisplayName("a business unit takes the firm's industry and place where it has none, never site or headcount")
    void unitProfileFallsBackToTheFirm() {
        HiringCompanyProfile firm = new HiringCompanyProfile("Kalem Group", "retail", "Dubai",
                "United Arab Emirates", "https://kalem.example", 12_000,
                new HiringPersona(null, List.of("Retail"), List.of(), List.of(), null));

        HiringCompanyProfile bare = new HiringCompanyProfile("Kalem Healthcare", null, null, null, null, null,
                HiringPersona.empty()).within(firm);
        HiringCompanyProfile placed = new HiringCompanyProfile("Kalem KSA", null, null, "Saudi Arabia", null, null,
                null).within(firm);

        assertThat(bare).isEqualTo(new HiringCompanyProfile("Kalem Healthcare", "retail", "Dubai",
                "United Arab Emirates", null, null, firm.persona()));
        assertThat(placed.city()).isNull();
        assertThat(placed.country()).isEqualTo("Saudi Arabia");
    }

    @Test
    @DisplayName("a business unit naming its own sectors does not take the firm's industry")
    void unitWithOwnSectorsKeepsItsOwnMarket() {
        HiringCompanyProfile firm = new HiringCompanyProfile("Kalem Group", "retail", "Dubai",
                "United Arab Emirates", null, null, HiringPersona.empty());

        HiringCompanyProfile unit = new HiringCompanyProfile("Kalem Healthcare", null, null, null, null, null,
                new HiringPersona(null, List.of("Hospitals"), List.of(), List.of(), null)).within(firm);

        assertThat(unit.industry()).isNull();
        assertThat(unit.persona().sectors()).containsExactly("Hospitals");
    }

    private static PersonaSeed company(String id, String industry, String country) {
        return new PersonaSeed(id, industry, country);
    }
}
