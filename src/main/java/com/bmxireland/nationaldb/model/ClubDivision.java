package com.bmxireland.nationaldb.model;

/**
 * Ability-based club racing divisions.
 * The code is written directly into the Sqorz entry CSV as the race class.
 */
public enum ClubDivision {

    D0("D0", "Unassigned"),
    D1("D1", "Airborne Aces"),
    D2("D2", "Corner Commanders"),
    D3("D3", "Chain Breakers"),
    D4("D4", "Gate Crushers"),
    D5("D5", "Intermaniacs"),
    D6("D6", "Speed Squad"),
    D7("D7", "Mini Shredders"),
    M1("M1", "Rhythm Masters");

    private final String code;
    private final String divisionName;

    ClubDivision(String code, String divisionName) {
        this.code = code;
        this.divisionName = divisionName;
    }

    public String code() { return code; }
    public String divisionName() { return divisionName; }

    /** Returns the division whose code matches, or empty if not recognised. */
    public static java.util.Optional<ClubDivision> fromCode(String code) {
        if (code == null) return java.util.Optional.empty();
        for (ClubDivision d : values()) {
            if (d.code.equalsIgnoreCase(code.trim())) return java.util.Optional.of(d);
        }
        return java.util.Optional.empty();
    }

    @Override
    public String toString() { return code + " — " + divisionName; }
}
