package com.netsquare.autopayloadpositioner.core;

import burp.api.montoya.core.Range;

import java.util.List;

public final class PlaceholderResult {
    private final String modifiedRequestString;
    private final List<Range> placeholderRanges;
    private final List<InsertionOp> insertions;
    private final boolean hasChanges;

    public PlaceholderResult(String modifiedRequestString,
                             List<Range> placeholderRanges,
                             List<InsertionOp> insertions,
                             boolean hasChanges) {
        this.modifiedRequestString = modifiedRequestString;
        this.placeholderRanges = placeholderRanges;
        this.insertions = insertions;
        this.hasChanges = hasChanges;
    }

    public String modifiedRequestString() {
        return modifiedRequestString;
    }

    public List<Range> placeholderRanges() {
        return placeholderRanges;
    }

    public List<InsertionOp> insertions() {
        return insertions;
    }

    public boolean hasChanges() {
        return hasChanges;
    }
}

