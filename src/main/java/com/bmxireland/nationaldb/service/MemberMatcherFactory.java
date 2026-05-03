package com.bmxireland.nationaldb.service;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.model.Member;

import java.util.List;

/**
 * Spring-managed factory that constructs {@link MemberMatchingSession} instances.
 * One session should be created per import run; sessions are not thread-safe.
 */
@Component
public class MemberMatcherFactory {

    public MemberMatchingSession createSession(List<Member> members) {
        return new MemberMatchingSession(members);
    }
}
