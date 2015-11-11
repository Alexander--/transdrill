package net.sf.inflater.processor;

import javax.lang.model.element.Element;
import javax.lang.model.util.ElementKindVisitor6;

public abstract class StubbornTypeTester<T> extends ElementKindVisitor6<T, Void> {
    @Override
    public T visitUnknown(Element e, Void unused) {
        return null;
    }
}