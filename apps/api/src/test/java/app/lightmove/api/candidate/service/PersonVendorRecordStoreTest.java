package app.lightmove.api.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.VendorProfileRecord;
import app.lightmove.api.candidate.repository.PersonRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import tools.jackson.databind.json.JsonMapper;

class PersonVendorRecordStoreTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final PersonVendorRecordStore store =
            new PersonVendorRecordStore(jdbc, mock(PersonRepository.class), JsonMapper.builder().build());
    private final Person person = mock(Person.class);

    PersonVendorRecordStoreTest() {
        when(person.getId()).thenReturn(UUID.randomUUID());
        when(person.getWorkspaceId()).thenReturn(UUID.randomUUID());
    }

    @Test
    @DisplayName("a NUL character a vendor sent is dropped, since jsonb would refuse it and fail the filing")
    void aNulCharacterIsDroppedBeforeTheRecordIsKept() {
        store.keepFor(person, researchWith(VendorProfileRecord.BRIGHTDATA,
                "{\"name\":\"Sample\\u0000 Person\",\"posts\":[{\"title\":\"a\\u0000b\"}]}"));

        ArgumentCaptor<SqlParameterSource> kept = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(anyString(), kept.capture());
        String record = (String) ((MapSqlParameterSource) kept.getValue()).getValue("record");
        assertThat(record).doesNotContain("\\u0000").contains("Sample Person").contains("ab");
    }

    @Test
    @DisplayName("a record past the size limit, or from a provider nobody keeps, is passed over")
    void anOversizedOrUnknownRecordIsNotKept() {
        String oversized = "{\"about\":\"" + "x".repeat(PersonVendorRecordStore.LONGEST_RECORD) + "\"}";

        store.keepFor(person, researchWith(VendorProfileRecord.HARVESTAPI, oversized));
        store.keepFor(person, researchWith("someone-else", "{\"name\":\"Sample Person\"}"));

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    private static EnrichedProfile researchWith(String provider, String record) {
        return new EnrichedProfile(null, null, null, null, null, null, null, List.of(), List.of(), List.of(),
                List.of(), null, EnrichmentVendor.BRIGHTDATA)
                .withVendorRecord(new VendorProfileRecord(provider, record, null, null));
    }
}
