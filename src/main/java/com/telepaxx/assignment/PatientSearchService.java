package com.telepaxx.assignment;

import com.telepaxx.assignment.model.PatientRecord;
import com.telepaxx.assignment.roster.RosterLoader;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;

/**
 * Searches patient records loaded from the roster source.
 *
 * Given search criteria (patientId, lastName, or both), return all matching PatientRecords.
 */
@ApplicationScoped
public class PatientSearchService {

    @Inject
    RosterLoader rosterLoader;

    public List<PatientRecord> search(String patientId, String lastName) {
        // both filters are applied as AND, a null param means no constraint on this field
        return rosterLoader.getAll().stream()
                .filter(r -> patientId == null || r.patientId().equals(patientId))
                .filter(r -> lastName == null || r.lastName().toLowerCase(Locale.ROOT)
                        .contains(lastName.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
