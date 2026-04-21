package com.bmxireland.nationaldb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Maps each RegistrationEntry field to the corresponding column header in the
 * Cycling Ireland registration export file.  Update application.yml when CI
 * changes column names — no code change or rebuild required.
 */
@Component
@ConfigurationProperties(prefix = "ci-registration.format")
public class RegistrationFormatConfig {

    private String club              = "Club";
    private String registrationDate  = "Registration Date";
    private String expiryDate        = "Expiry Date";
    private String licenseNumber     = "Licence Number";
    private String memberId          = "MID";
    private String category          = "Category";
    private String riderCategory     = "Rider Category";
    private String firstName         = "First Name";
    private String lastName          = "Last Name";
    private String email             = "Email";
    private String dateOfBirth       = "DOB";
    private String gender            = "Gender";
    private String nationality       = "Nationality";
    private String emergencyContactName  = "Emergency Contact Name";
    private String emergencyContactPhone = "Emergency Contact Phone";

    public String getClub()                  { return club; }
    public String getRegistrationDate()      { return registrationDate; }
    public String getExpiryDate()            { return expiryDate; }
    public String getLicenseNumber()         { return licenseNumber; }
    public String getMemberId()              { return memberId; }
    public String getCategory()             { return category; }
    public String getRiderCategory()         { return riderCategory; }
    public String getFirstName()             { return firstName; }
    public String getLastName()              { return lastName; }
    public String getEmail()                 { return email; }
    public String getDateOfBirth()           { return dateOfBirth; }
    public String getGender()               { return gender; }
    public String getNationality()           { return nationality; }
    public String getEmergencyContactName()  { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }

    public void setClub(String v)                  { this.club = v; }
    public void setRegistrationDate(String v)      { this.registrationDate = v; }
    public void setExpiryDate(String v)            { this.expiryDate = v; }
    public void setLicenseNumber(String v)         { this.licenseNumber = v; }
    public void setMemberId(String v)              { this.memberId = v; }
    public void setCategory(String v)             { this.category = v; }
    public void setRiderCategory(String v)         { this.riderCategory = v; }
    public void setFirstName(String v)             { this.firstName = v; }
    public void setLastName(String v)              { this.lastName = v; }
    public void setEmail(String v)                 { this.email = v; }
    public void setDateOfBirth(String v)           { this.dateOfBirth = v; }
    public void setGender(String v)               { this.gender = v; }
    public void setNationality(String v)           { this.nationality = v; }
    public void setEmergencyContactName(String v)  { this.emergencyContactName = v; }
    public void setEmergencyContactPhone(String v) { this.emergencyContactPhone = v; }
}
