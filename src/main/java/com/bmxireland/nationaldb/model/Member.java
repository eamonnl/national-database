package com.bmxireland.nationaldb.model;

/**
 * Represents a member record in the BMX Ireland national database.
 * Field ordering matches the xlsx column layout.
 */
public class Member {

    private int rowIndex; // 0-based row index in the spreadsheet (data rows only)

    // Required fields
    private String licenseNumber;
    private String licenseClass;
    private String licenseExpiry;
    private String givenName;
    private String familyName;
    private String birthDate;
    private String gender;
    private String active;

    // Optional plate (race) numbers
    private String plate20;
    private String plate24;
    private String plateRetro;
    private String plateOpen;

    // Optional transponder numbers
    private String transponder20;
    private String transponder24;
    private String transponderRetro;
    private String transponderOpen;

    // Additional fields
    private String licenseCountryCode;
    private String internationalLicense;
    private String clubName;
    private String teamName1;
    private String teamName2;
    private String teamName3;
    private String teamName4;
    private String teamName5;
    private String email;
    private String ccEmail;
    private String emergencyContactPerson;
    private String emergencyContactNumber;

    public Member() {
    }

    // --- Getters and Setters ---

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getLicenseClass() {
        return licenseClass;
    }

    public void setLicenseClass(String licenseClass) {
        this.licenseClass = licenseClass;
    }

    public String getLicenseExpiry() {
        return licenseExpiry;
    }

    public void setLicenseExpiry(String licenseExpiry) {
        this.licenseExpiry = licenseExpiry;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public String getPlate20() {
        return plate20;
    }

    public void setPlate20(String plate20) {
        this.plate20 = plate20;
    }

    public String getPlate24() {
        return plate24;
    }

    public void setPlate24(String plate24) {
        this.plate24 = plate24;
    }

    public String getPlateRetro() {
        return plateRetro;
    }

    public void setPlateRetro(String plateRetro) {
        this.plateRetro = plateRetro;
    }

    public String getPlateOpen() {
        return plateOpen;
    }

    public void setPlateOpen(String plateOpen) {
        this.plateOpen = plateOpen;
    }

    public String getTransponder20() {
        return transponder20;
    }

    public void setTransponder20(String transponder20) {
        this.transponder20 = transponder20;
    }

    public String getTransponder24() {
        return transponder24;
    }

    public void setTransponder24(String transponder24) {
        this.transponder24 = transponder24;
    }

    public String getTransponderRetro() {
        return transponderRetro;
    }

    public void setTransponderRetro(String transponderRetro) {
        this.transponderRetro = transponderRetro;
    }

    public String getTransponderOpen() {
        return transponderOpen;
    }

    public void setTransponderOpen(String transponderOpen) {
        this.transponderOpen = transponderOpen;
    }

    public String getLicenseCountryCode() {
        return licenseCountryCode;
    }

    public void setLicenseCountryCode(String licenseCountryCode) {
        this.licenseCountryCode = licenseCountryCode;
    }

    public String getInternationalLicense() {
        return internationalLicense;
    }

    public void setInternationalLicense(String internationalLicense) {
        this.internationalLicense = internationalLicense;
    }

    public String getClubName() {
        return clubName;
    }

    public void setClubName(String clubName) {
        this.clubName = clubName;
    }

    public String getTeamName1() {
        return teamName1;
    }

    public void setTeamName1(String teamName1) {
        this.teamName1 = teamName1;
    }

    public String getTeamName2() {
        return teamName2;
    }

    public void setTeamName2(String teamName2) {
        this.teamName2 = teamName2;
    }

    public String getTeamName3() {
        return teamName3;
    }

    public void setTeamName3(String teamName3) {
        this.teamName3 = teamName3;
    }

    public String getTeamName4() {
        return teamName4;
    }

    public void setTeamName4(String teamName4) {
        this.teamName4 = teamName4;
    }

    public String getTeamName5() {
        return teamName5;
    }

    public void setTeamName5(String teamName5) {
        this.teamName5 = teamName5;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCcEmail() {
        return ccEmail;
    }

    public void setCcEmail(String ccEmail) {
        this.ccEmail = ccEmail;
    }

    public String getEmergencyContactPerson() {
        return emergencyContactPerson;
    }

    public void setEmergencyContactPerson(String emergencyContactPerson) {
        this.emergencyContactPerson = emergencyContactPerson;
    }

    public String getEmergencyContactNumber() {
        return emergencyContactNumber;
    }

    public void setEmergencyContactNumber(String emergencyContactNumber) {
        this.emergencyContactNumber = emergencyContactNumber;
    }

    /**
     * Returns a display-friendly summary of this member.
     */
    public String toSummary() {
        return String.format("[%s] %s %s (%s) - %s - Club: %s",
                licenseNumber,
                givenName != null ? givenName : "",
                familyName != null ? familyName : "",
                licenseClass != null ? licenseClass : "",
                active != null ? active : "",
                clubName != null ? clubName : "");
    }

    /**
     * Returns a detailed multi-line view of this member.
     */
    public String toDetailString() {
        StringBuilder sb = new StringBuilder();
        sb.append("──────────────────────────────────────────\n");
        sb.append(String.format("  License Number  : %s\n", safe(licenseNumber)));
        sb.append(String.format("  License Class   : %s\n", safe(licenseClass)));
        sb.append(String.format("  License Expiry  : %s\n", safe(licenseExpiry)));
        sb.append(String.format("  Given Name      : %s\n", safe(givenName)));
        sb.append(String.format("  Family Name     : %s\n", safe(familyName)));
        sb.append(String.format("  Birth Date      : %s\n", safe(birthDate)));
        sb.append(String.format("  Gender          : %s\n", safe(gender)));
        sb.append(String.format("  Active          : %s\n", safe(active)));
        sb.append(String.format("  Plate 20        : %s\n", safe(plate20)));
        sb.append(String.format("  Plate 24        : %s\n", safe(plate24)));
        sb.append(String.format("  Plate Retro     : %s\n", safe(plateRetro)));
        sb.append(String.format("  Plate Open      : %s\n", safe(plateOpen)));
        sb.append(String.format("  Transponder 20  : %s\n", safe(transponder20)));
        sb.append(String.format("  Transponder 24  : %s\n", safe(transponder24)));
        sb.append(String.format("  Transponder Ret : %s\n", safe(transponderRetro)));
        sb.append(String.format("  Transponder Open: %s\n", safe(transponderOpen)));
        sb.append(String.format("  Country Code    : %s\n", safe(licenseCountryCode)));
        sb.append(String.format("  Int. License    : %s\n", safe(internationalLicense)));
        sb.append(String.format("  Club Name       : %s\n", safe(clubName)));
        sb.append(String.format("  Team 1          : %s\n", safe(teamName1)));
        sb.append(String.format("  Team 2          : %s\n", safe(teamName2)));
        sb.append(String.format("  Team 3          : %s\n", safe(teamName3)));
        sb.append(String.format("  Team 4          : %s\n", safe(teamName4)));
        sb.append(String.format("  Team 5          : %s\n", safe(teamName5)));
        sb.append(String.format("  Email           : %s\n", safe(email)));
        sb.append(String.format("  CC Email        : %s\n", safe(ccEmail)));
        sb.append(String.format("  Emergency Person: %s\n", safe(emergencyContactPerson)));
        sb.append(String.format("  Emergency Phone : %s\n", safe(emergencyContactNumber)));
        sb.append("──────────────────────────────────────────");
        return sb.toString();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    @Override
    public String toString() {
        return toSummary();
    }
}
