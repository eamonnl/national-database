package com.bmxireland.nationaldb.model;

/**
 * Represents a single row from an EventMaster BookingDetails export file.
 * dateOfBirth is normalised to YYYY-MM-DD during parsing.
 */
public record BookingEntry(
        String firstName,
        String lastName,
        String gender,
        String dateOfBirth,        // YYYY-MM-DD (normalised from d/M/yyyy on load)
        String licenseNumber,      // CI Licence Number
        String memberId,           // CI mid
        String clubName,           // CI Club
        String riderCategory,      // CI Rider Category
        String ageGroupYouth,      // Age Group Youth (mutually exclusive with ageGroupAdult)
        String ageGroupAdult,      // Age Group Adult
        String bmxRaceNumber,      // BMX Race Number
        String transponderNumber,  // Transponder Number
        String emergencyContactName,
        String emergencyContactPhone,
        String email,
        String mobile
) {}
