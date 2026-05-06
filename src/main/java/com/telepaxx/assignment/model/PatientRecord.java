package com.telepaxx.assignment.model;

// Represents one patient entry extracted from a DICOM file in the roster folder.
// Do not modify this class.
public record PatientRecord(
        String patientId,
        String lastName,
        String firstName,
        String fileName
) {}
