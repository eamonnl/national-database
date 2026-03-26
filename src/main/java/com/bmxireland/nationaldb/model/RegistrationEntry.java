package com.bmxireland.nationaldb.model;

/**
 * Represents a single row from a Cycling Ireland registration export file.
 * Field names match the column headers in the exported xlsx.
 */
public record RegistrationEntry(
        String club,
        String registrationDate,
        String expiryDate,
        String newOrRenewal,    // "NEW" or "RENEWAL"
        String licenseNumber,
        String memberId,        // MID — the stable Cycling Ireland identifier (internationalLicense)
        String category,
        String firstName,
        String lastName,
        String email,
        String dateOfBirth,
        String gender,
        String nationality,
        String emergencyContactName,
        String emergencyContactPhone
) {}
