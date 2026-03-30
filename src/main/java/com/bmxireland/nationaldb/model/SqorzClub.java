package com.bmxireland.nationaldb.model;

/**
 * Represents a club entry from the Sqorz timing system club list.
 */
public record SqorzClub(
        String groupId,
        String groupName,
        String shortName,
        String country
) {}
