package com.netsquare.autopayloadpositioner.core.positioners;

import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class ParamPositioner {
    public void add(HttpRequest request, List<Range> positions) {
        for (ParsedHttpParameter param : request.parameters()) {
            positions.add(Range.range(
                    param.valueOffsets().startIndexInclusive(),
                    param.valueOffsets().endIndexExclusive()
            ));
        }
    }
}

