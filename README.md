# DICOM Patient Matcher — Home Assignment

## Background

At Telepaxx, we process DICOM files — the standard format for medical imaging data.  
Each DICOM file carries patient identity information in its metadata (header tags), including PatientID and PatientName.

Your task is to build a small search API that lets callers find patients across a set of DICOM files.

## What you need to do

The project skeleton compiles as-is. Your job is to fill in the three stub classes:

| Class | What it does |
|---|---|
| `roster/RosterLoader.java` | Loads patient-related data from DICOM files |
| `PatientSearchService.java` | Implements search behavior for patient criteria |
| `PatientSearchResource.java` | Exposes the HTTP search API |

### Requirements

1. Implement patient search over the provided DICOM roster.
2. Expose a search endpoint that accepts patient search criteria and returns matching DICOM files with metadata you consider relevant.  
   The endpoint must support:
   - search by PatientID
   - search by last name
   - search by both
3. Return an appropriate response when no results are found.
4. Include automated tests that you consider appropriate for your implementation.
5. Document all important technical decisions in a `NOTES.md` file.

### Working approach (guidance)

- Create a dedicated branch for your solution.
- Keep commits small and logical, with clear commit messages that explain what changed and why.
- Open a pull request from your branch into `main`.
- After creating the PR and finishing the task, write back that the assignment is completed.

### DICOM hint

PatientName uses DICOM's PN (Person Name) Value Representation:

```
FamilyName^GivenName^MiddleName^NamePrefix^NameSuffix
```

Only the family name (first component before `^`) is relevant for name-based matching.

The dcm4che3 library (already on the classpath) provides `DicomInputStream`, `Attributes`, and `Tag` constants to read tags:

```java
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
```

## Prerequisites

Only [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) are required.  
No Java or Gradle installation needed.

## How to run

```bash
docker-compose up --build
```

Service starts on http://localhost:8080.  
OpenAPI UI: http://localhost:8080/q/swagger-ui

## Run in Docker Dev Mode

Use the Docker Compose `dev` profile to run the development container with live code reload:

```bash
docker compose --profile dev up dicom-patient-matcher-dev
```

## Developing locally (optional)

If you have Java 21 and Gradle installed, you can use Quarkus Dev Mode for hot reload:

```bash
./gradlew quarkusDev
```

## Deliverables

- Working implementation of the three stub classes
- Automated tests
- `NOTES.md` documenting important technical decisions
- Commit history that is small, logical, and readable
- Anything else you consider appropriate

**Time estimate:** 3–4 hours.
