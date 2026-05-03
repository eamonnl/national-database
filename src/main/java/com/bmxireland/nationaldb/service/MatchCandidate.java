package com.bmxireland.nationaldb.service;

/**
 * Input value object describing an incoming record to be matched against the member database.
 * Decouples the matcher from any specific import entry type.
 */
public record MatchCandidate(
        String mid,           // UCI / CI international licence number — nullable
        String licenseNumber, // domestic licence number — nullable
        String firstName,
        String lastName,
        String dateOfBirth,   // YYYY-MM-DD — nullable
        String clubName       // used as tiebreaker when name+DOB is ambiguous — nullable
) {}
