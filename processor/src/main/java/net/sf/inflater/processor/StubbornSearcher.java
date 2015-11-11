package net.sf.inflater.processor;

import javax.lang.model.element.Element;
import javax.lang.model.util.ElementScanner6;

public abstract class StubbornSearcher<T> extends ElementScanner6<T, T> {
    @Override
    public T visitUnknown(Element e, T lastResult) {
        return e.accept(this, lastResult);
    }
}
