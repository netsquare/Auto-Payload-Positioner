package com.netsquare.autopayloadpositioner.core;

import burp.api.montoya.core.Range;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.netsquare.autopayloadpositioner.body.BodyPositioner;
import com.netsquare.autopayloadpositioner.core.positioners.HeaderPositioner;
import com.netsquare.autopayloadpositioner.core.positioners.MethodPositioner;
import com.netsquare.autopayloadpositioner.core.positioners.ParamPositioner;
import com.netsquare.autopayloadpositioner.core.positioners.UrlPositioner;

import java.util.ArrayList;
import java.util.List;

public final class PositionFinder {
    private final MethodPositioner methodPositioner = new MethodPositioner();
    private final UrlPositioner urlPositioner = new UrlPositioner();
    private final ParamPositioner paramPositioner = new ParamPositioner();
    private final HeaderPositioner headerPositioner = new HeaderPositioner();
    private final BodyPositioner bodyPositioner;

    public PositionFinder(BodyPositioner bodyPositioner) {
        this.bodyPositioner = bodyPositioner;
    }

    public List<Range> findPositions(HttpRequest request, String requestString, PayloadPositionMode mode) {
        List<Range> positions = new ArrayList<>();

        if (ModeRules.shouldIncludeMethod(mode)) {
            methodPositioner.add(request, requestString, positions);
        }

        if (ModeRules.shouldIncludeUrlPath(mode)) {
            urlPositioner.add(request, requestString, ModeRules.shouldUseFullUrlPath(mode), positions);
        }

        if (ModeRules.shouldIncludeParameters(mode)) {
            paramPositioner.add(request, positions);
        }

        if (ModeRules.shouldIncludeBody(mode)) {
            bodyPositioner.addBodyPositions(request, requestString, positions);
        }

        if (ModeRules.shouldIncludeHeaders(mode)) {
            headerPositioner.add(request, requestString, positions);
        }

        return positions;
    }
}

