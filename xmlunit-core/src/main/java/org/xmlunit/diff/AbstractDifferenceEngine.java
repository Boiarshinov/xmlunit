/*
  This file is licensed to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package org.xmlunit.diff;

import java.util.Collections;
import java.util.Map;

/**
 * Useful base-implementation of some parts of the DifferenceEngine
 * interface.
 */
public abstract class AbstractDifferenceEngine implements DifferenceEngine {
    private final ComparisonListenerSupport listeners =
        new ComparisonListenerSupport();
    private NodeMatcher nodeMatcher = new DefaultNodeMatcher();
    private DifferenceEvaluator diffEvaluator = DifferenceEvaluators.Default;
    private ComparisonController comparisonController = ComparisonControllers.Default;
    private Map<String, String> uri2Prefix = Collections.emptyMap();

    protected AbstractDifferenceEngine() { }

    @Override
    public void addComparisonListener(ComparisonListener l) {
        if (l == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.addComparisonListener(l);
    }

    @Override
    public void addMatchListener(ComparisonListener l) {
        if (l == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.addMatchListener(l);
    }

    @Override
    public void addDifferenceListener(ComparisonListener l) {
        if (l == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.addDifferenceListener(l);
    }

    @Override
    public void setNodeMatcher(NodeMatcher n) {
        if (n == null) {
            throw new IllegalArgumentException("node matcher must"
                                               + " not be null");
        }
        nodeMatcher = n;
    }

    /**
     * Provides access to the configured NodeMatcher.
     */
    protected NodeMatcher getNodeMatcher() {
        return nodeMatcher;
    }

    @Override
    public void setDifferenceEvaluator(DifferenceEvaluator e) {
        if (e == null) {
            throw new IllegalArgumentException("difference evaluator must"
                                               + " not be null");
        }
        diffEvaluator = e;
    }

    /**
     * Provides access to the configured DifferenceEvaluator.
     */
    protected DifferenceEvaluator getDifferenceEvaluator() {
        return diffEvaluator;
    }

    @Override
    public void setComparisonController(ComparisonController c) {
        if (c == null) {
            throw new IllegalArgumentException("comparison controller must"
                                               + " not be null");
        }
        comparisonController = c;
    }

    /**
     * Provides access to the configured ComparisonController.
     */
    protected ComparisonController getComparisonController() {
        return comparisonController;
    }

    @Override
    public void setNamespaceContext(Map<String, String> uri2Prefix) {
        this.uri2Prefix = Collections.unmodifiableMap(uri2Prefix);
    }

    /**
     * Provides access to the configured namespace context.
     */
    protected Map<String, String> getNamespaceContext() {
        return uri2Prefix;
    }

    /**
     * Compares the detail values for object equality, lets the
     * difference evaluator and comparison controller evaluate the
     * result, notifies all listeners and returns the outcome.
     *
     * @return the outcome as pair of result and a flag that says
     * "stop the whole comparison process" when true.
     */
    protected final ComparisonState compare(Comparison comp) {
        Object controlValue = comp.getControlDetails().getValue();
        Object testValue = comp.getTestDetails().getValue();
        boolean equal = controlValue == null
            ? testValue == null : controlValue.equals(testValue);
        ComparisonResult initial =
            equal ? ComparisonResult.EQUAL : ComparisonResult.DIFFERENT;
        ComparisonResult altered =
            getDifferenceEvaluator().evaluate(comp, initial);
        listeners.fireComparisonPerformed(comp, altered);
        return altered != ComparisonResult.EQUAL
            && comparisonController.stopDiffing(new Difference(comp, altered))
            ? new FinishedComparisonState(altered)
            : new OngoingComparisonState(altered);
    }

    /**
     * Returns a string representation of the given XPathContext.
     */
    protected static String getXPath(XPathContext ctx) {
        return ctx == null ? null : ctx.getXPath();
    }

    /**
     * Encapsulates a comparison that may or may not be performed.
     */
    protected interface DeferredComparison {
        /**
         * Perform the comparison.
         */
        ComparisonState apply();
    }

    /**
     * Encapsulates the current result and a flag that
     * indicates whether comparison should be stopped.
     */
    protected abstract class ComparisonState {
        private final boolean finished;
        private final ComparisonResult result;

        protected ComparisonState(boolean finished, ComparisonResult result) {
            this.finished = finished;
            this.result = result;
        }

        protected ComparisonState andThen(DeferredComparison newStateProducer) {
            return finished ? this : newStateProducer.apply();
        }
        protected ComparisonState andIfTrueThen(boolean predicate,
                                                DeferredComparison newStateProducer) {
            return predicate ? andThen(newStateProducer) : this;
        }
        protected ComparisonState andThen(final Comparison comp) {
            return andThen(new DeferredComparison() {
                    @Override
                    public ComparisonState apply() {
                        return compare(comp);
                    }
                });
        }
        protected ComparisonState andIfTrueThen(boolean predicate,
                                                final Comparison comp) {
            return andIfTrueThen(predicate, new DeferredComparison() {
                    @Override
                    public ComparisonState apply() {
                        return compare(comp);
                    }
                });
        }
        @Override
        public String toString() {
            return getClass().getName() + ": current result is " + result;
        }
        @Override
        public boolean equals(Object other) {
            if (other == null || !getClass().equals(other.getClass())) {
                return false;
            }
            ComparisonState cs = (ComparisonState) other;
            return finished == cs.finished && result == cs.result;
        }
        @Override
        public int hashCode() {
            return (finished ? 7 : 1) * result.hashCode();
        }
    }

    protected final class FinishedComparisonState extends ComparisonState {
        protected FinishedComparisonState(ComparisonResult result) {
            super(true, result);
        }
    }

    protected final class OngoingComparisonState extends ComparisonState {
        protected OngoingComparisonState(ComparisonResult result) {
            super(false, result);
        }
        protected OngoingComparisonState() {
            this(ComparisonResult.EQUAL);
        }
    }
}