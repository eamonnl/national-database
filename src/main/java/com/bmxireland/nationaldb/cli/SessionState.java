package com.bmxireland.nationaldb.cli;

import java.util.List;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.model.Member;

/**
 * Holds the mutable session state shared by all CLI menu handlers:
 * the in-memory member list and the unsaved-changes flag.
 */
@Component
public class SessionState {

    private List<Member> members;
    private boolean unsavedChanges = false;

    public List<Member> getMembers() { return members; }
    public void setMembers(List<Member> members) { this.members = members; }

    public boolean hasUnsavedChanges() { return unsavedChanges; }
    public void markChanged() { unsavedChanges = true; }
    public void clearChanged() { unsavedChanges = false; }
}
