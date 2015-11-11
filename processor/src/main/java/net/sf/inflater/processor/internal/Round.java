package net.sf.inflater.processor.internal;

import java.util.Collection;

import javax.lang.model.element.Element;

final class Round {
    private final Session session;

    Round(Session session) {
        this.session = session;
    }

    public ProcessingResult processLayouts(Resources resourcesClass) {
        return new ProcessingResult(State.ERROR);
    }

    public static class ProcessingResult {
        public final State state;

        public final Element errorElement;
        public final String errorMessage;

        public ProcessingResult(State state) {
            this.state = state;
            this.errorElement = null;
            this.errorMessage = null;
        }

        public ProcessingResult(State state, Element errorElement, String errorMessage) {
            this.state = state;
            this.errorElement = errorElement;
            this.errorMessage = errorMessage;
        }
    }

    public enum State {
        COMPLETE,
        PARTIAL,
        ERROR
    }
}
