package com.bmxireland.nationaldb.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.model.BookingEntry;
import com.bmxireland.nationaldb.model.Member;

/**
 * National Series strategy: derives each rider's Sqorz class from their EventMaster
 * age group, cross-checked against their date of birth. When the two disagree the
 * DOB-derived class is used and the discrepancy is surfaced as a warning.
 */
@Component
public class AgeBasedClassStrategy implements RaceClassStrategy {

    @Override
    public ClassResolution resolve(BookingEntry entry, Member member) {
        String emClass = StringUtils.isNotBlank(entry.ageGroupYouth())
                ? entry.ageGroupYouth() : entry.ageGroupAdult();
        String sqorzClass = EventMasterService.mapToSqorzClass(emClass);

        if (sqorzClass != null && !"SuperClass".equals(sqorzClass)) {
            String dobDerived = EventMasterService.deriveClassFromDob(member.getGender(), member.getBirthDate());
            if (dobDerived != null && !dobDerived.equals(sqorzClass)) {
                // DOB-derived class takes precedence; record the EventMaster value for warning output
                return new ClassResolution(dobDerived, sqorzClass);
            }
        }

        if (sqorzClass == null) {
            sqorzClass = EventMasterService.deriveClassFromDob(member.getGender(), member.getBirthDate());
        }

        return new ClassResolution(sqorzClass, null);
    }
}
