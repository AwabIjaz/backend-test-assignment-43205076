# Implementation Decisions

## RosterLoader

### Eager loading at startup

All DICOM files are loaded once in a `@PostConstruct` method and held in memory for the lifetime of the application.
This means every search request filters an in-memory list with zero disk I/O rather than opening and parsing the files on each search request.
Eager loading is the right default for a bounded, stable dataset.

In a production system, DICOM files would be stored in object storage (e.g. S3) and metadata extracted at ingestion time would be persisted to PostgreSQL.
Indexed columns for PatientID and lastName for fast lookup, with full DICOM metadata stored as JSONB (with a GIN index) for flexible querying.
Search queries would hit PostgreSQL rather than an in-memory list, and results would include presigned URLs for direct file retrieval from object storage.

### Why `@PostConstruct` and not the constructor

CDI populates injected fields (like `rosterPath` from `@ConfigProperty`) after the constructor runs, not during it.
`@PostConstruct` is the hook that runs once all injection is complete, so `rosterPath` is guaranteed to have its value when `load()` executes.

### Bulk data exclusion

`DicomInputStream` is configured with `IncludeBulkData.NO` before reading the dataset.
DICOM files embed pixel data alongside header tags — for patient identity lookup only the header is needed.
On this dataset the difference is negligible, but the setting is correct regardless of file size and avoids loading image payloads that are immediately discarded.

### Non-recursive file scan

Files are scanned one level deep only. All test files sit directly in the roster folder with no subdirectories, so recursive scanning is not needed.
Non-recursive is also explicit about the expected folder structure.

### PatientName parsing

The DICOM PN (Person Name) format is `FamilyName^GivenName^MiddleName^Prefix^Suffix`. dcm4che3 returns this as a raw string, it does not split components automatically.
We split on `\^` with a limit of `-1` to preserve trailing empty components, ensuring that `parts[1]` is always safe to access even when only the family name is present in the file (e.g. `"HÜBNER^^^^"`).
Without the `-1` limit, Java's `split` would drop trailing empty strings and `parts[1]` would throw `ArrayIndexOutOfBoundsException`.
Only FamilyName and GivenName are extracted, last name is used for search, first name is included in the response as useful context.

### Immutability of the loaded roster

`getAll()` returns a `Collections.unmodifiableList` wrapper. The field itself is private, but the getter returns a reference to the underlying list.
Without the unmodifiable wrapper, any caller could call `.add()` or `.remove()` on the returned list and silently corrupt the shared in-memory roster.
`unmodifiableList` is a zero-copy wrapper, one allocation at startup, reused on every call.

### Corrupt or unreadable files

Files that cannot be parsed are skipped with a warning log rather than aborting startup.
In a medical system, one corrupt file should not make the entire roster unavailable to clinicians.

### Records with missing identity fields

Files where both PatientID and last name are blank are loaded but logged as a warning.
They will not match any search but are retained rather than discarded.
Dropping data in a medical context without explicit instructions felt like the wrong default.

---

## PatientSearchService

### Search semantics

- **PatientID: exact match.** IDs are opaque identifiers. A substring match on `"117"` returning `"1174"` would be misleading and likely wrong clinically. Exact equality is the only safe behaviour for an ID field.
- **lastName: case-insensitive substring match.** A clinician searching for a patient should not need to know the exact spelling or capitalisation. Typing a partial fragment (e.g. `"schä"` surfaces `"Schäfer"`) should surface all matching last names. This is implemented with `String.contains()` after lowercasing both sides.
- **One provided: one is null.** A null parameter means the caller did not provide that criterion, the filter passes all records for that field. This keeps the search method as a single entry point for all three search modes rather than three separate methods.
- **Both provided: AND logic.** A record must satisfy both predicates independently. Chaining two independent `.filter()` calls on the stream expresses this naturally.
- **Neither provided: not handled here.** The service returns the full roster if both arguments are `null`. Rejecting that case will be the HTTP layer's responsibility, the service stays free of HTTP concerns and is independently testable with any combination of inputs including null/null.

### `Locale.ROOT` for case folding

`toLowerCase()` without a locale argument uses the JVM's system locale, which is non-deterministic, the same query could behave differently on a German or Turkish server.
Example: in Turkish locale, `"EMILIA".toLowerCase()` gives `"emılıa"` rather than `"emilia"` — the uppercase `I` becomes dotless `ı`, so a search for `"emilia"` would not match `"EMILIA"` on a Turkish-locale server.

### Known limitation: `Weiß` vs `WEISS` in name search

The test data contains `P1004` stored as `Weiß` in one file and `WEISS` in another.
This reflects a real-world DICOM data quality problem: older scanners and hospital systems that could not encode `ß` substituted `SS` when writing the tag, or normalised names to all-caps.
As a result, searching `lastName=weiß` finds only the `Weiß` file, and `lastName=weiss` finds only `WEISS`, the two are not linked by name alone.

Implementing `ß to ss` unicode normalization would fix this case but would break another: `P1001` (`Müller`) and `P2001` (`Muller`) are genuinely different patients.
Normalizing `ü to u` would silently conflate them, which is a more dangerous error in a medical context than missing a match.

The correct approach for retrieving all records of a patient is to search by `patientId`, not by name.
`patientId=P1004` correctly returns both the `Weiß` and `WEISS` files regardless of name encoding.

### Field injection over constructor injection

`@Inject` field injection is used for simplicity and is idiomatic in Quarkus quickstarts.
Constructor injection (with a `private final` field) would be the stricter choice, it makes dependencies explicit and keeps fields immutable.
In a production codebase, constructor injection would be preferred.

The `rosterLoader` field is package-private (no `private` modifier) specifically to allow unit tests in the same package to inject a stub directly without running a CDI container.

---

## PatientSearchResource

### HTTP method: GET

Search is a read operation with no side effects. Query parameters are short enough for a URL.
GET is the correct choice, it is idiomatic REST and allows the endpoint to be called directly from a browser or curl without a request body.

### No-results response: 200 with empty array

When a search matches nothing, the endpoint returns `200 OK` with `[]`.
HTTP 404 means "this resource does not exist", but the search endpoint always exists; it just has zero results.
An empty collection is a valid and meaningful response.

### Missing parameters: 400 Bad Request

If neither `patientId` nor `lastName` is provided, the endpoint returns `400 Bad Request`.
An unconstrained search returning the full roster is bad API design regardless of dataset size, it gives no signal to the caller that they forgot to pass a parameter.
The 400 makes the contract explicit.

### Blank string handling

Query parameters containing only whitespace are treated as missing, `"   "` is equivalent to not providing the parameter.
The value is trimmed before passing to the service so the search predicate never sees whitespace-only strings.

### Error response body

The 400 response returns a JSON object `{"error": "..."}` rather than plain text, consistent with the 200 response being JSON.
In production this would use a typed `ErrorResponse` record shared across all endpoints.

### `Optional<String>` query parameters

Each query parameter is typed as `Optional<String>` rather than a plain nullable `String`.
This makes the absent-vs-present distinction explicit in the method signature and avoids ambiguity between a missing parameter and an empty string value.

### `@RestQuery` over `@QueryParam`

`@RestQuery` is the RESTEasy Reactive annotation and is idiomatic for `quarkus-rest-jackson`.
It infers the query parameter name from the Java variable name (enabled by the `-parameters` compiler flag already set in `build.gradle`).
This avoids the need to repeat the name as a string literal, `@RestQuery String patientId` instead of `@QueryParam("patientId") String patientId`, reducing the risk of typos and keeping refactoring safe.

### Pagination

Search results are returned as a flat array without pagination. For the assignment's dataset this is appropriate.
In a production system returning potentially thousands of matching studies, pagination would be essential.
A `page` and `pageSize` parameter with a response envelope containing `totalResults`, `totalPages`, and `data` would be the natural extension.
The current flat array response shape is forward-compatible with adding a pagination envelope later without breaking the core search contract.

### OpenAPI annotations

`@Tag`, `@Operation`, and `@APIResponse` are included because `quarkus-smallrye-openapi` is on the classpath and Swagger UI is always enabled via `application.properties`.
They have no effect on request handling but populate the Swagger UI at `/q/swagger-ui`, making the endpoint explorable without reading source code.
