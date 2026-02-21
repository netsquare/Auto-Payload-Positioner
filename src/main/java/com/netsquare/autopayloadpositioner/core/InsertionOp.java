package com.netsquare.autopayloadpositioner.core;

/**
 * Represents a text insertion applied to the request string at a specific index.
 * Used to shift previously computed {@code Range} offsets to match the modified request.
 */
public final class InsertionOp {
    private final int index;
    private final String text;

    public InsertionOp(int index, String text) {
        this.index = index;
        this.text = text;
    }

    public int index() {
        return index;
    }

    public String text() {
        return text;
    }
}

