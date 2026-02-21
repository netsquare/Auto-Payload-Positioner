package com.netsquare.autopayloadpositioner.burp;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.intruder.HttpRequestTemplate;
import com.netsquare.autopayloadpositioner.body.BodyPositioner;
import com.netsquare.autopayloadpositioner.core.PayloadPositionMode;
import com.netsquare.autopayloadpositioner.core.PlaceholderResult;
import com.netsquare.autopayloadpositioner.core.PositionFinder;
import com.netsquare.autopayloadpositioner.placeholder.PlaceholderApplier;
import com.netsquare.autopayloadpositioner.util.RangeUtils;

import java.util.List;

public final class RequestProcessor {
    private final PlaceholderApplier placeholderApplier = new PlaceholderApplier();
    private final PositionFinder positionFinder = new PositionFinder(new BodyPositioner());

    public HttpRequestTemplate buildIntruderTemplate(HttpRequest request, PayloadPositionMode mode) {
        String originalRequestString = request.toString();

        // Placeholder insertion first (for Montoya non-zero insertion requirement).
        PlaceholderResult placeholderResult = placeholderApplier.apply(request, originalRequestString);

        // Compute positions against the original request, then shift to match inserted placeholders.
        List<Range> originalPositions = positionFinder.findPositions(request, originalRequestString, mode);
        List<Range> shiftedPositions = RangeUtils.shiftRangesForInsertions(originalPositions, placeholderResult.insertions());
        shiftedPositions.addAll(placeholderResult.placeholderRanges());

        String finalRequestString = placeholderResult.modifiedRequestString();
        List<Range> finalPositions = RangeUtils.validatePositions(shiftedPositions, finalRequestString.length());

        ByteArray content = placeholderResult.hasChanges()
                ? ByteArray.byteArray(finalRequestString)
                : request.toByteArray();

        return new HttpRequestTemplate() {
            @Override
            public ByteArray content() {
                return content;
            }

            @Override
            public List<Range> insertionPointOffsets() {
                return finalPositions;
            }
        };
    }
}

