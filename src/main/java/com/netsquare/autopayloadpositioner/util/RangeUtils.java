package com.netsquare.autopayloadpositioner.util;

import burp.api.montoya.core.Range;
import com.netsquare.autopayloadpositioner.core.InsertionOp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RangeUtils {
    private RangeUtils() {
    }

    public static List<Range> validatePositions(List<Range> ranges, int maxLength) {
        if (ranges.isEmpty()) return ranges;

        // Sort by start index first, then by shortest length first.
        // This helps preserve precise inner insertion points when broader ranges overlap.
        ranges.sort(Comparator
                .comparingInt(Range::startIndexInclusive)
                .thenComparingInt(r -> r.endIndexExclusive() - r.startIndexInclusive()));

        List<Range> validRanges = new ArrayList<>();
        for (Range range : ranges) {
            // Basic validation
            if (range.startIndexInclusive() < 0 ||
                    range.endIndexExclusive() > maxLength ||
                    range.startIndexInclusive() >= range.endIndexExclusive()) {
                continue;
            }

            if (validRanges.isEmpty()) {
                validRanges.add(range);
                continue;
            }

            Range previous = validRanges.get(validRanges.size() - 1);
            boolean overlaps = range.startIndexInclusive() < previous.endIndexExclusive();

            if (!overlaps) {
                validRanges.add(range);
                continue;
            }

            // Prefer a more specific inner range when it is fully contained in the previous one.
            boolean containedInPrevious = range.startIndexInclusive() >= previous.startIndexInclusive()
                    && range.endIndexExclusive() <= previous.endIndexExclusive();
            int previousLen = previous.endIndexExclusive() - previous.startIndexInclusive();
            int currentLen = range.endIndexExclusive() - range.startIndexInclusive();

            if (containedInPrevious && currentLen < previousLen) {
                validRanges.set(validRanges.size() - 1, range);
            }
        }

        return validRanges;
    }

    public static List<Range> shiftRangesForInsertions(List<Range> originalRanges, List<InsertionOp> insertions) {
        if (insertions.isEmpty()) return new ArrayList<>(originalRanges);

        List<Range> shifted = new ArrayList<>(originalRanges.size());
        for (Range range : originalRanges) {
            int newStart = shiftBoundary(range.startIndexInclusive(), insertions);
            int newEnd = shiftBoundary(range.endIndexExclusive(), insertions);
            shifted.add(Range.range(newStart, newEnd));
        }
        return shifted;
    }

    public static int shiftBoundary(int boundary, List<InsertionOp> insertions) {
        int delta = 0;
        for (InsertionOp op : insertions) {
            if (op.index() <= boundary) {
                delta += op.text().length();
            } else {
                break;
            }
        }
        return boundary + delta;
    }
}

