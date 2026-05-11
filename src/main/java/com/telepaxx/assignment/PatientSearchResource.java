package com.telepaxx.assignment;

import com.telepaxx.assignment.model.PatientRecord;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestQuery;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP endpoint for patient search.
 *
 * Requirements:
 *   - Accept search criteria: PatientID, last name, or both
 *   - Return a list of matching DICOM files with metadata you consider relevant
 *   - Handle the case where no results are found
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("/search")
@Tag(name = "Patient Search")
public class PatientSearchResource {

    @Inject
    PatientSearchService patientSearchService;

    @GET
    @Operation(summary = "Search patients by ID and/or last name")
    @APIResponse(responseCode = "200", description = "Matching records; empty array if none found")
    @APIResponse(responseCode = "400", description = "At least one search parameter must be provided")
    public Response search(
            // @RestQuery infers the query-param name from the Java parameter name
            @Parameter(description = "Exact PatientID match") @RestQuery Optional<String> patientId,
            @Parameter(description = "Case-insensitive last name substring match") @RestQuery Optional<String> lastName) {

        boolean hasPatientId = patientId.isPresent() && !patientId.get().trim().isEmpty();
        boolean hasLastName = lastName.isPresent() && !lastName.get().trim().isEmpty();

        // require at least one criterion, unconstrained search is not supported
        if (!hasPatientId && !hasLastName) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Provide at least one of: patientId, lastName"))
                    .build();
        }

        List<PatientRecord> results = patientSearchService.search(
                hasPatientId ? patientId.get().trim() : null,
                hasLastName ? lastName.get().trim() : null);

        return Response.ok(results).build();
    }
}