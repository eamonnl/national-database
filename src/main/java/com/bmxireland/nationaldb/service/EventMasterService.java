package com.bmxireland.nationaldb.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.bmxireland.nationaldb.model.BookingEntry;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.model.SqorzClub;

/**
 * Service for importing EventMaster BookingDetails reports and generating
 * Sqorz timing-system entry CSV files.
 */
@Service
public class EventMasterService {

    private static final Logger log = LoggerFactory.getLogger(EventMasterService.class);

    private static final Pattern TRANSPONDER_PATTERN = Pattern.compile("^[A-Z]{2}-\\d{5}$");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d/M/yyyy");

    /** EventMaster age-group label → Sqorz class name. */
    private static final Map<String, String> CLASS_MAP = Map.ofEntries(
            Map.entry("6 & Under (Mixed Male & Female)", "Under 6 Mixed"),
            Map.entry("Male 7-8",         "Male 7-8"),
            Map.entry("Male 9-10",        "Male 9-10"),
            Map.entry("Male 11-12",       "Male 11-12"),
            Map.entry("Male 13-14",       "Male 13-14"),
            Map.entry("Male 15+",         "Male 15-29"),
            Map.entry("Female 7-10",      "Female 7-10"),
            Map.entry("Female 11-14",     "Female 11-14"),
            Map.entry("Female 15+",       "Female 15-29"),
            Map.entry("Male 30+ Open",    "Masters 30+"),
            Map.entry("Superclass",       "SuperClass"),
            Map.entry("Female 30+ open",  "Female 30+")
    );

    /** Canonical Sqorz club list — used for Club ID, Short Name, and Country lookup. */
    static final List<SqorzClub> SQORZ_CLUBS = List.of(
            new SqorzClub("10", "Belfast City BMX",      "BCBMX",  "IRL"),
            new SqorzClub("20", "Cork BMX",              "CRKBMX", "IRL"),
            new SqorzClub("30", "Courtown BMX",          "COURTN", "IRL"),
            new SqorzClub("40", "East Coast Raiders BMX","ECRBMX", "IRL"),
            new SqorzClub("50", "Lisburn BMX",           "LISBMX", "UK"),
            new SqorzClub("60", "Lucan BMX",             "LUCBMX", "IRL"),
            new SqorzClub("90", "Other",                 "OTHER",  "IRL"),
            new SqorzClub("70", "Ratoath BMX",           "RATBMX", "IRL"),
            new SqorzClub("80", "Saint Annes BMX",       "STANBMX","IRL")
    );

    private final DatabaseService databaseService;

    public EventMasterService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    // ---- Result types ----

    public record BookingImportResult(List<Member> added, List<UpdatedMember> updated) {
        public record UpdatedMember(Member member, String updatedPlate, String updatedTransponder) {}
    }

    public record SqorzExportResult(int entriesWritten, String outputPath,
                                    List<ClassMismatchWarning> classMismatches) {
        public record ClassMismatchWarning(String licenseNumber, String name,
                                           String eventMasterClass, String dobDerivedClass) {}
    }

    // ---- Public operations ----

    /**
     * Reads an EventMaster BookingDetails xlsx file and returns its rows as
     * {@link BookingEntry} objects. Columns are located by header name so
     * the column order does not need to be fixed.
     *
     * @param path path to the EventMaster xlsx file
     * @return list of booking entries (one per data row)
     * @throws IOException if the file cannot be read
     */
    public List<BookingEntry> loadBookingFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new FileNotFoundException("Booking file not found: " + file.getAbsolutePath());
        }

        log.info("Loading EventMaster booking file from: {}", file.getAbsolutePath());

        List<BookingEntry> entries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Booking file has no header row");
            }

            // Build column-name → index map from the header row
            Map<String, Integer> col = new HashMap<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String header = databaseService.getCellStringValue(headerRow.getCell(c));
                if (header != null) col.put(header.trim(), c);
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String licenseNumber = get(row, col, "CI Licence Number");
                if (StringUtils.isBlank(licenseNumber)) continue;

                entries.add(new BookingEntry(
                        get(row, col, "First Name"),
                        get(row, col, "Last Name"),
                        get(row, col, "Gender"),
                        parseDate(get(row, col, "Date of Birth")),
                        licenseNumber.trim(),
                        get(row, col, "CI mid"),
                        get(row, col, "CI Club"),
                        get(row, col, "CI Rider Category"),
                        get(row, col, "Age Group Youth"),
                        get(row, col, "Age Group Adult"),
                        get(row, col, "BMX Race Number"),
                        get(row, col, "Transponder Number"),
                        get(row, col, "Emergency Contact Name"),
                        get(row, col, "Emergency Contact Phone"),
                        get(row, col, "Email"),
                        get(row, col, "Mobile")
                ));
            }
        }

        log.info("Loaded {} booking entries", entries.size());
        return entries;
    }

    /**
     * Phase 1: merges booking entries into the member list.
     * <ul>
     *   <li>Entries whose licence number is not in the database are added as new members.</li>
     *   <li>For matched members the database is the source of truth, but a missing Plate 20
     *       or Transponder 20 is backfilled from the booking if the booking value is valid.</li>
     * </ul>
     *
     * @param members the in-memory member list (mutated in place)
     * @param entries booking entries to merge
     * @return result listing added and updated members
     */
    public BookingImportResult importBookings(List<Member> members, List<BookingEntry> entries) {
        List<Member> added = new ArrayList<>();
        List<BookingImportResult.UpdatedMember> updated = new ArrayList<>();

        Map<String, Member> byLicense = buildLicenseIndex(members);

        for (BookingEntry entry : entries) {
            String key = entry.licenseNumber().trim().toUpperCase();
            Member member = byLicense.get(key);

            if (member == null) {
                Member newMember = buildMemberFromBooking(entry, members.size() + added.size());
                added.add(newMember);
                byLicense.put(key, newMember);
                log.info("New member added from booking: {} {} [{}]",
                        newMember.getGivenName(), newMember.getFamilyName(), newMember.getLicenseNumber());
            } else {
                String updatedPlate       = null;
                String updatedTransponder = null;

                if (StringUtils.isBlank(member.getPlate20()) && isValidPlate(entry.bmxRaceNumber())) {
                    updatedPlate = entry.bmxRaceNumber().trim();
                    member.setPlate20(updatedPlate);
                }
                if (StringUtils.isBlank(member.getTransponder20()) && isValidTransponder(entry.transponderNumber())) {
                    updatedTransponder = entry.transponderNumber().trim();
                    member.setTransponder20(updatedTransponder);
                }

                if (updatedPlate != null || updatedTransponder != null) {
                    updated.add(new BookingImportResult.UpdatedMember(member, updatedPlate, updatedTransponder));
                    log.info("Backfilled data for {} [{}]: plate={}, transponder={}",
                            member.getFamilyName(), member.getLicenseNumber(), updatedPlate, updatedTransponder);
                }
            }
        }

        members.addAll(added);
        return new BookingImportResult(added, updated);
    }

    /**
     * Phase 2: generates a Sqorz entry CSV file from the booking entries, using the
     * in-memory member database as the authoritative data source.
     *
     * <p>The output file header rows are read verbatim from the bundled
     * {@code SqorzEntries v1.1.csv} template. One data row is written per booking entry.
     * If the EventMaster age group does not match the DOB-derived class the DOB-derived
     * class is used and the discrepancy is recorded as a warning.</p>
     *
     * @param members the in-memory member list (after phase 1 merge)
     * @param entries booking entries (determines output row order)
     * @return result with output path, row count, and any class-mismatch warnings
     * @throws IOException if the output file cannot be written or the template is missing
     */
    public SqorzExportResult generateSqorzCsv(List<Member> members, List<BookingEntry> entries)
            throws IOException {

        Map<String, Member> byLicense = buildLicenseIndex(members);
        List<SqorzExportResult.ClassMismatchWarning> warnings = new ArrayList<>();

        List<String> headerLines = readTemplateHeader();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String csvPath = "SqorzEntries_" + timestamp + ".csv";

        int written = 0;
        // Sqorz reads its CSV files using Mac Roman encoding, so the output must
        // match — writing UTF-8 causes multi-byte sequences (e.g. á = C3 A1) to be
        // mis-interpreted as Mac Roman glyphs (√°), corrupting accented characters.
        try (PrintWriter out = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(csvPath), Charset.forName("x-MacRoman")))) {

            for (String line : headerLines) out.println(line);

            for (BookingEntry entry : entries) {
                Member member = byLicense.get(entry.licenseNumber().trim().toUpperCase());
                if (member == null) {
                    log.warn("No member found for licence '{}' during Sqorz export — skipping",
                            entry.licenseNumber());
                    continue;
                }

                // Club lookup
                SqorzClub club = resolveSqorzClub(member.getClubName());

                // Plate: prefer member DB, fall back to booking if valid and DB blank
                String plate = member.getPlate20();
                if (StringUtils.isBlank(plate) && isValidPlate(entry.bmxRaceNumber())) {
                    plate = entry.bmxRaceNumber().trim();
                }

                // Transponder 20: same priority
                String transponder20 = member.getTransponder20();
                if (StringUtils.isBlank(transponder20) && isValidTransponder(entry.transponderNumber())) {
                    transponder20 = entry.transponderNumber().trim();
                }

                // Class name derivation
                String emClass    = StringUtils.isNotBlank(entry.ageGroupYouth())
                        ? entry.ageGroupYouth() : entry.ageGroupAdult();
                String sqorzClass = mapToSqorzClass(emClass);

                if (sqorzClass != null && !"SuperClass".equals(sqorzClass)) {
                    String dobDerived = deriveClassFromDob(member.getGender(), member.getBirthDate());
                    if (dobDerived != null && !dobDerived.equals(sqorzClass)) {
                        String memberName = member.getGivenName() + " " + member.getFamilyName();
                        warnings.add(new SqorzExportResult.ClassMismatchWarning(
                                member.getLicenseNumber(), memberName, sqorzClass, dobDerived));
                        log.warn("Class mismatch for {} [{}]: EventMaster='{}', DOB-derived='{}'",
                                memberName, member.getLicenseNumber(), sqorzClass, dobDerived);
                        sqorzClass = dobDerived;
                    }
                }

                out.println(toCsvRow(
                        member.getLicenseNumber(),
                        member.getLicenseClass(),
                        member.getLicenseExpiry(),
                        member.getLicenseCountryCode(),
                        MemberService.formatFamilyNameForOutput(member.getFamilyName()),
                        member.getGivenName(),
                        member.getBirthDate(),
                        member.getGender(),
                        plate,
                        transponder20,
                        member.getTransponder24(),
                        club != null ? club.groupId()   : "",
                        club != null ? club.groupName() : "",
                        club != null ? club.shortName() : "",
                        "",               // Club State — not populated
                        club != null ? club.country()   : "",
                        sqorzClass,
                        plate,            // Class Plate 1 = same as Plate
                        transponder20,    // Class Transponder 1
                        "",               // Class Name 2
                        "",               // Class Plate 2
                        "",               // Class Transponder 2
                        "",               // Class Hire Transponder 1
                        ""                // Class Hire Transponder 2
                ));
                written++;
            }
        }

        log.info("Sqorz CSV written to: {} ({} rows)", csvPath, written);
        return new SqorzExportResult(written, csvPath, warnings);
    }

    // ---- Package-private helpers (used directly in tests) ----

    /**
     * Maps an EventMaster age-group label to the corresponding Sqorz class name.
     * Returns null if the label is not recognised.
     */
    static String mapToSqorzClass(String eventMasterClass) {
        if (eventMasterClass == null || eventMasterClass.isBlank()) return null;
        return CLASS_MAP.get(eventMasterClass.trim());
    }

    /**
     * Derives the Sqorz class name from gender and date of birth using the
     * calendar-year age rule (age = currentYear − birthYear).
     * Returns null if gender or DOB is missing/unparseable.
     * SuperClass cannot be derived and is never returned.
     */
    static String deriveClassFromDob(String gender, String dobIso) {
        if (dobIso == null || dobIso.isBlank() || gender == null || gender.isBlank()) return null;
        try {
            int age = LocalDate.now().getYear() - LocalDate.parse(dobIso.trim()).getYear();
            boolean female = "F".equalsIgnoreCase(gender.trim());

            if (age <= 6)  return "Under 6 Mixed";
            if (female) {
                if (age <= 10) return "Female 7-10";
                if (age <= 14) return "Female 11-14";
                if (age <= 29) return "Female 15-29";
                return "Female 30+";
            } else {
                if (age <= 8)  return "Male 7-8";
                if (age <= 10) return "Male 9-10";
                if (age <= 12) return "Male 11-12";
                if (age <= 14) return "Male 13-14";
                if (age <= 29) return "Male 15-29";
                return "Masters 30+";
            }
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    // ---- Private helpers ----

    private Member buildMemberFromBooking(BookingEntry entry, int rowIndex) {
        Member m = new Member();
        m.setRowIndex(rowIndex);
        m.setLicenseNumber(entry.licenseNumber().trim());
        m.setGivenName(entry.firstName());
        m.setFamilyName(MemberService.capitalizeName(MemberService.normalizeSeparators(entry.lastName())));
        m.setGender(normalizeGender(entry.gender()));
        m.setBirthDate(entry.dateOfBirth());
        m.setActive("Yes");

        String licenseClass = MemberService.mapLicenseClass(entry.riderCategory());
        if (licenseClass == null) licenseClass = MemberService.licenseClassFromDob(entry.dateOfBirth());
        m.setLicenseClass(licenseClass);

        m.setClubName(databaseService.resolveClubName(entry.clubName()));

        if (isValidPlate(entry.bmxRaceNumber())) {
            m.setPlate20(entry.bmxRaceNumber().trim());
        }
        if (isValidTransponder(entry.transponderNumber())) {
            m.setTransponder20(entry.transponderNumber().trim());
        }

        m.setEmail(entry.email());
        m.setEmergencyContactPerson(entry.emergencyContactName());
        m.setEmergencyContactNumber(databaseService.normalizePhoneNumber(entry.emergencyContactPhone()));
        return m;
    }

    private SqorzClub resolveSqorzClub(String clubName) {
        if (clubName == null || clubName.isBlank()) {
            return findSqorzClub("Other");
        }
        String lower = clubName.trim().toLowerCase();
        // 1. Exact match
        for (SqorzClub club : SQORZ_CLUBS) {
            if (club.groupName().equalsIgnoreCase(clubName.trim())) return club;
        }
        // 2. Containment match
        for (SqorzClub club : SQORZ_CLUBS) {
            String groupLower = club.groupName().toLowerCase();
            if (lower.contains(groupLower) || groupLower.contains(lower)) return club;
        }
        return findSqorzClub("Other");
    }

    private SqorzClub findSqorzClub(String name) {
        return SQORZ_CLUBS.stream()
                .filter(c -> c.groupName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private List<String> readTemplateHeader() throws IOException {
        List<String> lines = new ArrayList<>();
        try (var is = new ClassPathResource("SqorzEntries v1.1.csv").getInputStream();
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null && lineNum < 7) {
                lines.add(line);
                lineNum++;
            }
        }
        return lines;
    }

    private Map<String, Member> buildLicenseIndex(List<Member> members) {
        Map<String, Member> index = new HashMap<>();
        for (Member m : members) {
            if (m.getLicenseNumber() != null) {
                index.put(m.getLicenseNumber().trim().toUpperCase(), m);
            }
        }
        return index;
    }

    /**
     * Converts a raw date string (either YYYY-MM-DD or d/M/yyyy) to YYYY-MM-DD.
     * Returns null if the value is blank; returns the raw value if unparseable.
     */
    private String parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) return value;
        try {
            return LocalDate.parse(value.trim(), DMY).toString();
        } catch (DateTimeParseException ignored) {
            return value;
        }
    }

    private boolean isValidPlate(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            int num = Integer.parseInt(value.trim());
            return num >= 1 && num <= 999;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isValidTransponder(String value) {
        if (value == null || value.isBlank()) return false;
        return TRANSPONDER_PATTERN.matcher(value.trim()).matches();
    }

    private String normalizeGender(String gender) {
        if (gender == null) return null;
        return switch (gender.trim().toUpperCase()) {
            case "M", "MALE"   -> "M";
            case "F", "FEMALE" -> "F";
            default            -> gender.trim();
        };
    }

    private String get(Row row, Map<String, Integer> colIndex, String colName) {
        Integer idx = colIndex.get(colName);
        if (idx == null) return null;
        return databaseService.getCellStringValue(row.getCell(idx));
    }

    private String toCsvRow(String... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(values[i]));
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
