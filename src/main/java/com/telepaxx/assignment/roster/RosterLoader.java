package com.telepaxx.assignment.roster;

import com.telepaxx.assignment.model.PatientRecord;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.io.FileUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Loads patient-related data from a folder of DICOM files.
 *
 * Read *.dcm files from the configured roster folder.
 * For each relevant file, extract at minimum:
 *   - PatientID  (DICOM tag: Tag.PatientID)
 *   - PatientName (DICOM tag: Tag.PatientName) — stored as "FamilyName^GivenName^..."
 *
 * Expose data needed by PatientSearchService.
 */
@ApplicationScoped
public class RosterLoader {

    private static final Logger LOG = Logger.getLogger(RosterLoader.class);

    @ConfigProperty(name = "assignment.roster.path")
    String rosterPath;

    private List<PatientRecord> roster = Collections.emptyList();

    @PostConstruct
    void load() {
        File rosterDir = new File(rosterPath);
        if (!rosterDir.isDirectory()) {
            LOG.errorf("Roster path is not a directory: %s", rosterPath);
            return; // roster stays empty list
        }
        Collection<File> dcmFiles = FileUtils.listFiles(rosterDir, new String[] { "dcm" }, false);
        List<PatientRecord> records = new ArrayList<>(dcmFiles.size());
        for (File file : dcmFiles) {
            try (DicomInputStream dis = new DicomInputStream(file)) {
                dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
                Attributes attrs = dis.readDataset();
                String patientId = attrs.getString(Tag.PatientID, "");
                String rawName = attrs.getString(Tag.PatientName, "");
                // limit -1 preserves trailing empty components so indices stay stable
                String[] parts = rawName.split("\\^", -1);
                String lastName = parts.length > 0 ? parts[0] : "";
                String firstName = parts.length > 1 ? parts[1] : "";
                // logging if both filters are empty but still adding record as partial data can be useful
                if (patientId.isEmpty() && lastName.isEmpty()) {
                    LOG.warnf("DICOM file has no PatientID or PatientName, " +
                            "will not match any search criteria: %s", file.getName());
                }
                records.add(new PatientRecord(patientId, lastName, firstName, file.getName()));
            } catch (IOException e) {
                // skip corrupt/unreadable files without aborting startup
                LOG.warnf("Skipping unreadable DICOM file: %s", file.getName(), e);
            }
        }
        this.roster = Collections.unmodifiableList(records);
        LOG.infof("RosterLoader: loaded %d records from %s", roster.size(), rosterPath);
    }

    public List<PatientRecord> getAll() {
        return roster;
    }
}
