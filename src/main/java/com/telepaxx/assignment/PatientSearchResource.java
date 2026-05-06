package com.telepaxx.assignment;

import jakarta.ws.rs.Path;

/**
 * HTTP endpoint for patient search.
 *
 * TODO: Design and implement the search endpoint.
 *
 * Requirements:
 *   - Accept search criteria: PatientID, last name, or both
 *   - Return a list of matching DICOM files with metadata you consider relevant
 *   - Handle the case where no results are found
 *
 * Important implementation decisions should be documented in NOTES.md.
 *
 * This bootstrap path can be kept or changed if justified.
 */
@Path("/search")
public class PatientSearchResource {

    // TODO: inject PatientSearchService and implement the endpoint

}
