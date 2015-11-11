package net.sf.inflater.processor.internal;

import com.google.auto.service.AutoService;

import net.sf.inflater.CreateInflater;
import net.sf.inflater.Layout;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
public final class InflaterProcessor implements Processor {
    public static final String ARG_RESOURCE_DIR = "resourceDir";

    private final HashSet<String> set = new HashSet<>(); {
        set.add(Layout.class.getCanonicalName());
        set.add(CreateInflater.class.getCanonicalName());
    }

    private Session session;

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return set;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public void init(ProcessingEnvironment environment) {
        session = new Session(environment);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.errorRaised()) {
            session.warn("Pending errors present, aborting layout processing");
        } else {
            session.processRoundTargets(roundEnv);
        }

        return false;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return Collections.emptyList();
    }
}
