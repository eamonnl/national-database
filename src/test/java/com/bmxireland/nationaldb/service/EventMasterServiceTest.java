package com.bmxireland.nationaldb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bmxireland.nationaldb.model.BookingEntry;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.EventMasterService.BookingImportResult;

class EventMasterServiceTest {

    private DatabaseService databaseService;
    private EventMasterService service;

    @BeforeEach
    void setUp() {
        databaseService = mock(DatabaseService.class);
        when(databaseService.resolveClubName(anyString())).thenAnswer(i -> i.getArgument(0));
        when(databaseService.normalizePhoneNumber(anyString())).thenAnswer(i -> i.getArgument(0));
        service = new EventMasterService(databaseService);
    }

    // ---- Helpers ----

    private BookingEntry entry(String licenceNumber, String firstName, String lastName,
                               String gender, String dob, String clubName,
                               String riderCategory, String ageGroupYouth, String ageGroupAdult,
                               String raceNumber, String transponder) {
        return new BookingEntry(firstName, lastName, gender, dob, licenceNumber, null, clubName,
                riderCategory, ageGroupYouth, ageGroupAdult, raceNumber, transponder,
                null, null, null, null);
    }

    /** Returns a DOB string for a rider who turns the given age in the current calendar year. */
    private String dobForAge(int age) {
        return (java.time.LocalDate.now().getYear() - age) + "-06-15";
    }

    // ---- mapToSqorzClass ----

    @Test void mapClass_6Under()    { assertEquals("Under 6 Mixed", EventMasterService.mapToSqorzClass("6 & Under (Mixed Male & Female)")); }
    @Test void mapClass_male78()    { assertEquals("Male 7-8",      EventMasterService.mapToSqorzClass("Male 7-8")); }
    @Test void mapClass_male910()   { assertEquals("Male 9-10",     EventMasterService.mapToSqorzClass("Male 9-10")); }
    @Test void mapClass_male1112()  { assertEquals("Male 11-12",    EventMasterService.mapToSqorzClass("Male 11-12")); }
    @Test void mapClass_male1314()  { assertEquals("Male 13-14",    EventMasterService.mapToSqorzClass("Male 13-14")); }
    @Test void mapClass_male15plus(){ assertEquals("Male 15-29",    EventMasterService.mapToSqorzClass("Male 15+")); }
    @Test void mapClass_female710() { assertEquals("Female 7-10",   EventMasterService.mapToSqorzClass("Female 7-10")); }
    @Test void mapClass_female1114(){ assertEquals("Female 11-14",  EventMasterService.mapToSqorzClass("Female 11-14")); }
    @Test void mapClass_female15p() { assertEquals("Female 15-29",  EventMasterService.mapToSqorzClass("Female 15+")); }
    @Test void mapClass_masters30() { assertEquals("Masters 30+",   EventMasterService.mapToSqorzClass("Male 30+ Open")); }
    @Test void mapClass_superclass(){ assertEquals("SuperClass",     EventMasterService.mapToSqorzClass("Superclass")); }
    @Test void mapClass_female30()  { assertEquals("Female 30+",     EventMasterService.mapToSqorzClass("Female 30+ open")); }
    @Test void mapClass_unknown()   { assertNull(EventMasterService.mapToSqorzClass("Unknown Class")); }
    @Test void mapClass_null()      { assertNull(EventMasterService.mapToSqorzClass(null)); }
    @Test void mapClass_blank()     { assertNull(EventMasterService.mapToSqorzClass("  ")); }

    // ---- deriveClassFromDob ----

    @Test void derive_male_age6()  { assertEquals("Under 6 Mixed", EventMasterService.deriveClassFromDob("M", dobForAge(6))); }
    @Test void derive_female_age6(){ assertEquals("Under 6 Mixed", EventMasterService.deriveClassFromDob("F", dobForAge(6))); }
    @Test void derive_male_age7()  { assertEquals("Male 7-8",      EventMasterService.deriveClassFromDob("M", dobForAge(7))); }
    @Test void derive_male_age8()  { assertEquals("Male 7-8",      EventMasterService.deriveClassFromDob("M", dobForAge(8))); }
    @Test void derive_male_age9()  { assertEquals("Male 9-10",     EventMasterService.deriveClassFromDob("M", dobForAge(9))); }
    @Test void derive_male_age10() { assertEquals("Male 9-10",     EventMasterService.deriveClassFromDob("M", dobForAge(10))); }
    @Test void derive_male_age11() { assertEquals("Male 11-12",    EventMasterService.deriveClassFromDob("M", dobForAge(11))); }
    @Test void derive_male_age12() { assertEquals("Male 11-12",    EventMasterService.deriveClassFromDob("M", dobForAge(12))); }
    @Test void derive_male_age13() { assertEquals("Male 13-14",    EventMasterService.deriveClassFromDob("M", dobForAge(13))); }
    @Test void derive_male_age14() { assertEquals("Male 13-14",    EventMasterService.deriveClassFromDob("M", dobForAge(14))); }
    @Test void derive_male_age15() { assertEquals("Male 15-29",    EventMasterService.deriveClassFromDob("M", dobForAge(15))); }
    @Test void derive_male_age29() { assertEquals("Male 15-29",    EventMasterService.deriveClassFromDob("M", dobForAge(29))); }
    @Test void derive_male_age30() { assertEquals("Masters 30+",   EventMasterService.deriveClassFromDob("M", dobForAge(30))); }
    @Test void derive_female_age7() { assertEquals("Female 7-10",  EventMasterService.deriveClassFromDob("F", dobForAge(7))); }
    @Test void derive_female_age10(){ assertEquals("Female 7-10",  EventMasterService.deriveClassFromDob("F", dobForAge(10))); }
    @Test void derive_female_age11(){ assertEquals("Female 11-14", EventMasterService.deriveClassFromDob("F", dobForAge(11))); }
    @Test void derive_female_age14(){ assertEquals("Female 11-14", EventMasterService.deriveClassFromDob("F", dobForAge(14))); }
    @Test void derive_female_age15(){ assertEquals("Female 15-29", EventMasterService.deriveClassFromDob("F", dobForAge(15))); }
    @Test void derive_female_age29(){ assertEquals("Female 15-29", EventMasterService.deriveClassFromDob("F", dobForAge(29))); }
    @Test void derive_female_age30(){ assertEquals("Female 30+",   EventMasterService.deriveClassFromDob("F", dobForAge(30))); }
    @Test void derive_nullDob()     { assertNull(EventMasterService.deriveClassFromDob("M", null)); }
    @Test void derive_nullGender()  { assertNull(EventMasterService.deriveClassFromDob(null, dobForAge(10))); }

    // ---- importBookings — new member ----

    @Test
    void importBookings_newMember_isAddedToList() {
        List<Member> members = new ArrayList<>();
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, "200", null);

        BookingImportResult result = service.importBookings(members, List.of(e));

        assertEquals(1, result.added().size());
        assertEquals(0, result.updated().size());
        assertEquals(1, members.size());
        assertEquals("26U001", result.added().get(0).getLicenseNumber());
    }

    @Test
    void importBookings_newMember_plateSetFromBooking() {
        List<Member> members = new ArrayList<>();
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, "200", null);

        service.importBookings(members, List.of(e));

        assertEquals("200", members.get(0).getPlate20());
    }

    @Test
    void importBookings_newMember_transponderSetFromBooking() {
        List<Member> members = new ArrayList<>();
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, null, "AB-12345");

        service.importBookings(members, List.of(e));

        assertEquals("AB-12345", members.get(0).getTransponder20());
    }

    @Test
    void importBookings_newMember_genderNormalised() {
        List<Member> members = new ArrayList<>();
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, null, null);

        service.importBookings(members, List.of(e));

        assertEquals("F", members.get(0).getGender());
    }

    @Test
    void importBookings_newMember_invalidPlate_notSet() {
        List<Member> members = new ArrayList<>();
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, "1500", null);

        service.importBookings(members, List.of(e));

        assertNull(members.get(0).getPlate20());
    }

    @Test
    void importBookings_newMember_invalidTransponder_notSet() {
        List<Member> members = new ArrayList<>();
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, null, "Unknown");

        service.importBookings(members, List.of(e));

        assertNull(members.get(0).getTransponder20());
    }

    // ---- importBookings — existing member backfill ----

    @Test
    void importBookings_existingMember_backfillsPlate() {
        Member m = new Member();
        m.setLicenseNumber("26U001");
        List<Member> members = new ArrayList<>(List.of(m));
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, "205", null);

        BookingImportResult result = service.importBookings(members, List.of(e));

        assertEquals(1, result.updated().size());
        assertEquals("205", m.getPlate20());
        assertEquals("205", result.updated().get(0).updatedPlate());
    }

    @Test
    void importBookings_existingMember_doesNotOverwritePlate() {
        Member m = new Member();
        m.setLicenseNumber("26U001");
        m.setPlate20("100");
        List<Member> members = new ArrayList<>(List.of(m));
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, "205", null);

        BookingImportResult result = service.importBookings(members, List.of(e));

        assertEquals(0, result.updated().size());
        assertEquals("100", m.getPlate20());
    }

    @Test
    void importBookings_existingMember_backfillsTransponder() {
        Member m = new Member();
        m.setLicenseNumber("26U001");
        List<Member> members = new ArrayList<>(List.of(m));
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, null, "AB-12345");

        BookingImportResult result = service.importBookings(members, List.of(e));

        assertEquals(1, result.updated().size());
        assertEquals("AB-12345", m.getTransponder20());
        assertEquals("AB-12345", result.updated().get(0).updatedTransponder());
    }

    @Test
    void importBookings_existingMember_doesNotOverwriteTransponder() {
        Member m = new Member();
        m.setLicenseNumber("26U001");
        m.setTransponder20("XY-99999");
        List<Member> members = new ArrayList<>(List.of(m));
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, null, "AB-12345");

        BookingImportResult result = service.importBookings(members, List.of(e));

        assertEquals(0, result.updated().size());
        assertEquals("XY-99999", m.getTransponder20());
    }

    @Test
    void importBookings_licenseMatchIsCaseInsensitive() {
        Member m = new Member();
        m.setLicenseNumber("26u001");
        List<Member> members = new ArrayList<>(List.of(m));
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, "205", null);

        service.importBookings(members, List.of(e));

        assertEquals(1, members.size()); // no new member added
        assertEquals("205", m.getPlate20());
    }

    @Test
    void importBookings_multipleEntries_processedIndependently() {
        List<Member> members = new ArrayList<>();
        List<BookingEntry> entries = List.of(
                entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                        "Cork BMX", null, "Female 15+", null, "200", null),
                entry("26U002", "Bob", "Jones", "Male", "2012-03-10",
                        "Lucan BMX", null, "Male 13-14", null, "201", "AB-12345")
        );

        BookingImportResult result = service.importBookings(members, entries);

        assertEquals(2, result.added().size());
        assertEquals(2, members.size());
    }

    // ---- importBookings — no-op cases ----

    @Test
    void importBookings_emptyEntries_noChange() {
        List<Member> members = new ArrayList<>();
        BookingImportResult result = service.importBookings(members, List.of());
        assertTrue(result.added().isEmpty());
        assertTrue(result.updated().isEmpty());
        assertTrue(members.isEmpty());
    }

    @Test
    void importBookings_existingMember_nothingToBackfill_notReported() {
        Member m = new Member();
        m.setLicenseNumber("26U001");
        m.setPlate20("100");
        m.setTransponder20("XY-99999");
        List<Member> members = new ArrayList<>(List.of(m));
        BookingEntry e = entry("26U001", "Alice", "Smith", "Female", "2010-05-01",
                "Cork BMX", null, "Female 15+", null, "205", "AB-12345");

        BookingImportResult result = service.importBookings(members, List.of(e));

        assertTrue(result.updated().isEmpty());
    }
}
