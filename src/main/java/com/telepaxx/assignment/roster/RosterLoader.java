package com.telepaxx.assignment.roster;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Loads patient-related data from a folder of DICOM files.
 *
 * TODO: Implement this class.
 *
 * Read *.dcm files from the configured roster folder.
 * For each relevant file, extract at minimum:
 *   - PatientID  (DICOM tag: Tag.PatientID)
 *   - PatientName (DICOM tag: Tag.PatientName) — stored as "FamilyName^GivenName^..."
 *
 * Expose data needed by PatientSearchService.
 *
 * Important implementation decisions should be documented in NOTES.md.
 *
 * Hint: use DicomInputStream from the dcm4che3 library (already on the classpath).
 */
@ApplicationScoped
public class RosterLoader {

    @ConfigProperty(name = "assignment.roster.path")
    String rosterPath;

    // TODO: expose roster access needed by PatientSearchService

}
