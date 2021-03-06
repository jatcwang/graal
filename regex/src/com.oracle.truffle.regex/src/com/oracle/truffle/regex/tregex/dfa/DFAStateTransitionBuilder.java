/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class DFAStateTransitionBuilder extends TransitionBuilder<NFATransitionSet> implements JsonConvertible {

    private final NFATransitionSet transitions;
    private MatcherBuilder matcherBuilder;

    private int id = -1;
    private DFAStateNodeBuilder source;
    private DFAStateNodeBuilder target;
    private DFACaptureGroupTransitionBuilder captureGroupTransition;

    DFAStateTransitionBuilder(MatcherBuilder matcherBuilder, NFAStateTransition transition, NFA nfa, boolean forward, boolean prioritySensitive) {
        this.transitions = NFATransitionSet.create(nfa, forward, prioritySensitive, transition);
        this.matcherBuilder = matcherBuilder;
    }

    DFAStateTransitionBuilder(MatcherBuilder matcherBuilder, NFATransitionSet transitions) {
        this.transitions = transitions;
        this.matcherBuilder = matcherBuilder;
    }

    public DFAStateTransitionBuilder createNodeSplitCopy() {
        return new DFAStateTransitionBuilder(matcherBuilder, transitions);
    }

    @Override
    public MatcherBuilder getMatcherBuilder() {
        return matcherBuilder;
    }

    @Override
    public void setMatcherBuilder(MatcherBuilder matcherBuilder) {
        this.matcherBuilder = matcherBuilder;
    }

    @Override
    public DFAStateTransitionBuilder createMerged(TransitionBuilder<NFATransitionSet> other, MatcherBuilder mergedMatcher) {
        return new DFAStateTransitionBuilder(mergedMatcher, transitions.createMerged(other.getTransitionSet()));
    }

    @Override
    public void mergeInPlace(TransitionBuilder<NFATransitionSet> other, MatcherBuilder mergedMatcher) {
        transitions.addAll(other.getTransitionSet());
        matcherBuilder = mergedMatcher;
    }

    @Override
    public NFATransitionSet getTransitionSet() {
        return transitions;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public DFAStateNodeBuilder getSource() {
        return source;
    }

    public void setSource(DFAStateNodeBuilder source) {
        this.source = source;
    }

    public DFAStateNodeBuilder getTarget() {
        return target;
    }

    public void setTarget(DFAStateNodeBuilder target) {
        this.target = target;
    }

    public DFACaptureGroupTransitionBuilder getCaptureGroupTransition() {
        return captureGroupTransition;
    }

    public void setCaptureGroupTransition(DFACaptureGroupTransitionBuilder captureGroupTransition) {
        this.captureGroupTransition = captureGroupTransition;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonArray nfaTransitions = Json.array(getTransitionSet().stream().map(t -> Json.val(t.getId())));
        if (target.getAnchoredFinalStateTransition() != null) {
            nfaTransitions.append(Json.val(target.getAnchoredFinalStateTransition().getId()));
        }
        if (target.getUnAnchoredFinalStateTransition() != null) {
            nfaTransitions.append(Json.val(target.getUnAnchoredFinalStateTransition().getId()));
        }
        JsonObject ret = Json.obj(Json.prop("id", id),
                        Json.prop("source", source.getId()),
                        Json.prop("target", target.getId()),
                        Json.prop("matcherBuilder", getMatcherBuilder().toString()),
                        Json.prop("nfaTransitions", nfaTransitions));
        if (captureGroupTransition != null) {
            ret.append(Json.prop("captureGroupTransition", captureGroupTransition.toLazyTransition(new CompilationBuffer())));
        }
        return ret;
    }
}
