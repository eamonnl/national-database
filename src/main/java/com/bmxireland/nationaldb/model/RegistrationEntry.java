package com.bmxireland.nationaldb.model;

/**
 * Represents a single row from a Cycling Ireland registration export file.
 * Field names match the column headers in the exported xlsx.
 */
public record RegistrationEntry(
        String club,
        String registrationDate,
        String expiryDate,
        String licenseNumber,
        String memberId,        // MID — the stable Cycling Ireland identifier (internationalLicense)
        String category,        // col 7: e.g. "LC_U10", "FC_U14", "CS"
        String firstName,
        String lastName,
        String email,
        String dateOfBirth,
        String gender,
        String nationality,
        String emergencyContactName,
        String emergencyContactPhone,
        String riderCategory    // col 8: e.g. "U8", "U12", "SENIOR", "M30"
) {}
