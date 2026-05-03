package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;

/**
 * Outcome of a single match attempt via {@link MemberMatchingSession}.
 */
public record MatchResult(
        Member member,
        String matchMethod,
        boolean nameWasCorrected,
        boolean ambiguous,
        int ambiguousCount
) {
    public boolean found() { return member != null; }

    public static MatchResult of(Member member, String method, boolean corrected) {
        return new MatchResult(member, method, corrected, false, 0);
    }

    public static MatchResult ambiguous(int count) {
        return new MatchResult(null, null, false, true, count);
    }

    public static MatchResult noMatch() {
        return new MatchResult(null, null, false, false, 0);
    }
}
