package com.telepaxx.assignment;

import com.telepaxx.assignment.model.PatientRecord;
import com.telepaxx.assignment.roster.RosterLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PatientSearchService.
 *
 * No Quarkus container. RosterLoader is replaced with a handwritten stub that
 * returns a fixed fixture.
 * This keeps the tests fast and focused purely on the search predicate logic.
 */
class PatientSearchServiceTest {

    // handwritten stub: overrides getAll() to return a fixed list,
    // bypassing the @PostConstruct DICOM loading entirely
    static class StubRosterLoader extends RosterLoader {
        private final List<PatientRecord> records;

        StubRosterLoader(List<PatientRecord> records) {
            this.records = records;
        }

        @Override
        public List<PatientRecord> getAll() {
            return records;
        }
    }

    private PatientSearchService service;

    @BeforeEach
    void setUp() {
        service = new PatientSearchService();
        // package-private field — accessible because this test is in the same package
        service.rosterLoader = new StubRosterLoader(List.of(
                new PatientRecord("P1001", "Müller", "Anna", "1174.dcm"),
                new PatientRecord("P1001", "MÜLLER", "Anna", "4831.dcm"),
                new PatientRecord("P2001", "Muller", "Anna", "6650.dcm"),
                new PatientRecord("P1002", "Schäfer", "", "2489.dcm"),
                new PatientRecord("P1008", "Böhm", "", "9980.dcm")));
    }

    // patientId filter ---

    // exact match on patientId should return multiple records if they share the
    // same patientId
    @Test
    void searchByPatientId_exactMatch_returnsOnlyThatId() {
        // "P1001" matches two records
        List<PatientRecord> results = service.search("P1001", null);

        assertThat(results).hasSize(2)
                .extracting(PatientRecord::patientId)
                .containsOnly("P1001");
    }

    // wrong patientId returns empty list
    @Test
    void searchByPatientId_noMatch_returnsEmptyList() {
        // "P9999X" does not match any record
        assertThat(service.search("P9999X", null)).isEmpty();
    }

    // patientId substring must not match - considered wrong patientId
    @Test
    void searchByPatientId_isExact_doesNotMatchSubstring() {
        // "P100" is a prefix of "P1001" — must not match
        assertThat(service.search("P100", null)).isEmpty();
    }

    // lastName filter ---

    // case-insensitive match on lastName should return all records matching that name
    @Test
    void searchByLastName_caseInsensitive_matchesUpperAndMixedCase() {
        // "müller" must match both "Müller" and "MÜLLER"
        List<PatientRecord> results = service.search(null, "müller");

        assertThat(results).hasSize(2)
                .extracting(PatientRecord::lastName)
                .containsExactlyInAnyOrder("Müller", "MÜLLER");
    }

    // substring match on lastName should return all records whose lastName contains
    // the search fragment, ignoring case
    @Test
    void searchByLastName_substringMatch_findsPartialFragment() {
        // "ller" is shared by Müller, MÜLLER, and Muller
        assertThat(service.search(null, "ller")).hasSize(3);
    }

    // Unicode characters must match exactly
    @Test
    void searchByLastName_unicodeNotNormalized_mullerDoesNotMatchMuller() {
        // "Muller" (u) must not match "Müller" (ü) or "MÜLLER" (Ü)
        List<PatientRecord> results = service.search(null, "Muller");

        assertThat(results).hasSize(1)
                .extracting(PatientRecord::patientId)
                .containsOnly("P2001");
    }

    // wrong lastName returns empty list
    @Test
    void searchByLastName_noMatch_returnsEmptyList() {
        // "zzznomatch" does not match any lastName
        assertThat(service.search(null, "zzznomatch")).isEmpty();
    }

    // AND logic (both params) ---

    // both criteria match - record is returned
    @Test
    void searchByBoth_bothCriteriaMatch_returnsIntersection() {
        // P1001 + "ller" - both Müller files qualify
        List<PatientRecord> results = service.search("P1001", "ller");

        assertThat(results).hasSize(2)
                .extracting(PatientRecord::patientId)
                .containsOnly("P1001");
    }

    // id matches but name does not
    @Test
    void searchByBoth_idMatchesButNameDoesNot_returnsEmpty() {
        // P1001 has Müller — "muller" (u) does not match ü
        assertThat(service.search("P1001", "muller")).isEmpty();
    }

    // name matches but id does not
    @Test
    void searchByBoth_nameMatchesButIdDoesNot_returnsEmpty() {
        // "ller" matches multiple records, but none have patientId "P9999X"
        assertThat(service.search("P9999X", "ller")).isEmpty();
    }

    // both params null — service returns everything
    @Test
    void searchWithBothNull_returnsAll() {
        assertThat(service.search(null, null)).hasSize(5);
    }
}
