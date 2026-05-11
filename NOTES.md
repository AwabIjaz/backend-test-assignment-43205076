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
