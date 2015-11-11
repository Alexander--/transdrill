package net.sf.inflater.processor.internal;

import net.sf.inflater.CreateInflater;
import net.sf.inflater.processor.FirstFoundScanner;
import net.sf.inflater.processor.StubbornSearcher;
import net.sf.inflater.processor.StubbornTypeTester;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

final class Session {
    private final Set<Resources> fullyProcessedResources = new HashSet<>();
    private final Set<Resources> nonProcessedResources = new HashSet<>();
    private final ProcessingEnvironment environment;

    private File resourceDir;

    Session(ProcessingEnvironment environment) {
        this.environment = environment;

        resourceDir = getConfiguredResourceDir(environment);
    }

    void notice(String message) {
        environment.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING, message);
    }

    void log(String message) {
        environment.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }

    void warn(String message) {
        environment.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
    }

    void errorOut(String message, Element element) {
        environment.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    void processRoundTargets(RoundEnvironment roundEnv) {
        final Round round = new Round(this);

        final List<Element> annotatedOnes = new ArrayList<>();
        for (Element packageElement:roundEnv.getElementsAnnotatedWith(CreateInflater.class)) {
            annotatedOnes.add(packageElement);

            findSomeNewResourceClass(packageElement);
        }

        if (!annotatedOnes.isEmpty() && resourceDir == null) {
            errorOut("Supply " + InflaterProcessor.ARG_RESOURCE_DIR + " argument with full path" +
                     " of Android resource directory to annotation processor. See documentation of" +
                     " your compiler and build system for details.", annotatedOnes.get(0));

            throw new IllegalArgumentException("Resource directory unknown, failed to proceed");
        }

        for (Resources resources:nonProcessedResources) {
            Round.ProcessingResult results = round.processLayouts(resources);

            switch (results.state) {
                case ERROR:
                    errorOut(results.errorMessage, results.errorElement);
                    break;
                case COMPLETE:
                    nonProcessedResources.remove(resources);
                    fullyProcessedResources.add(resources);
                case PARTIAL:
                    // ok
            }
        }
    }

    // XXX: this method makes some assumptions:
    // * Simple name of resource class mist be "R"
    // * It is ok, if there is no such class
    // * "R" is a single non-inner class in the supplied package
    // * JLS is somewhat Java-6/7/8 like
    private Resources findSomeNewResourceClass(Element presumablyPackageElement) {
        return presumablyPackageElement.accept(new StubbornSearcher<Resources>() {
            private final ElementVisitor<Resources, ?> classNameTest = new StubbornTypeTester<Resources>() {
                @Override
                public Resources visitTypeAsClass(TypeElement candidate, Void unused) {
                    if ("R".contentEquals(candidate.getSimpleName())) {
                        Resources resourcesClass = new Resources(candidate);

                        if (!fullyProcessedResources.contains(resourcesClass) && !nonProcessedResources.contains(resourcesClass)) {
                            nonProcessedResources.add(resourcesClass);

                            return resourcesClass;
                        }
                    }

                    return null;
                }
            };

            @Override
            public Resources visitType(TypeElement elementToCheck, Resources lastFound) {
                final Resources newResult;

                return (newResult = elementToCheck.accept(classNameTest, null)) != null
                        ? newResult
                        : lastFound;
            }
        }, null);
    }

    Filer provideFiler() {
        return environment.getFiler();
    }

    File getResourceDir() {
        if (resourceDir == null) {
            resourceDir = getConfiguredResourceDir();
        }

        return resourceDir;
    }

    private File getConfiguredResourceDir() {
        Map<String, String> options = environment.getOptions();

        final String resourceDir;
        if (options != null && ((resourceDir = options.get(InflaterProcessor.ARG_RESOURCE_DIR)) != null)) {
            return new File(resourceDir);
        }

        notice("Failed to get Android resource directory from compiler options, guessing the hard way");

        return painfullyGuessResourceDir();
    }

    private File painfullyGuessResourceDir() {
        return null;
    }

    private File painfullyGuessManifestPath() {
        final File someFilesystemDir = painfullyGuessSourcesDir();

        if (someFilesystemDir != null) {
            log("Assuming, that " + someFilesystemDir + " is below AndroidManifest.xml in filesystem");
        }
    }

    private File painfullyGuessSourcesDir() {
        final JavaFileManager.Location[] probingRoots = new JavaFileManager.Location[] {
                StandardLocation.SOURCE_PATH,
                StandardLocation.SOURCE_OUTPUT,
        };

        final String randomName = "dummy" + UUID.randomUUID().toString();

        final Filer filer = environment.getFiler();

        FileObject probe = null;
        try {
            for (JavaFileManager.Location someLocation : probingRoots) {
                try {
                    probe = filer.getResource(someLocation, "", randomName);

                    if (probe != null) {
                        break;
                    }
                } catch (IOException | IllegalArgumentException failure) {
                    warn("Failed to probe resources location: " + Utils.explain(failure));
                }
            }

            if (probe != null) {
                // so ugly :`(
                String dummySourceFilePath = probe.toUri().toString();
                if (dummySourceFilePath.startsWith("file:")) {
                    if (!dummySourceFilePath.startsWith("file://")) {
                        dummySourceFilePath = "file://" + dummySourceFilePath.substring("file:".length());
                    }
                } else {
                    dummySourceFilePath = "file://" + dummySourceFilePath;
                }

                return new File(new URI(dummySourceFilePath));
            }
        } catch (URISyntaxException failure) {
            warn("Unable to resolve filesystem path to dummy file: " + Utils.explain(failure));
        } finally {
            if (probe != null) {
                if (probe.delete()) {
                    warn("Failed to delete temporary dummy file " + probe.getName());
                }
            }
        }

        return null;
    }

    private static abstract class AndroidManifestFinderStrategy {
        final String name;
        final Matcher matcher;

        AndroidManifestFinderStrategy(String name, Pattern sourceFolderPattern, String sourceFolder) {
            this.name = name;
            this.matcher = sourceFolderPattern.matcher(sourceFolder);
        }

        File findAndroidManifestFile() {
            for (String location : possibleLocations()) {
                File manifestFile = new File(matcher.group(1), location + "/AndroidManifest.xml");
                if (manifestFile.exists()) {
                    return manifestFile;
                }
            }
            return null;
        }

        boolean applies() {
            return  matcher.matches();
        }

        abstract Iterable<String> possibleLocations();
    }

    private static final class BlindManifestFinderStrategy extends AndroidManifestFinderStrategy {
        private static final int MAX_PARENTS_FROM_SOURCE_FOLDER = 10;
        private static final int MAX_CHILD_DEPTH = 4;

        private final Set<Path> visited = new HashSet<>();

        private final File projectRoot;

        BlindManifestFinderStrategy(String sourceFolder) {
            super("Blind", null, sourceFolder);

            this.projectRoot = new File(sourceFolder);
        }

        @Override
        File findAndroidManifestFile() {


            Path searchRoot = projectRoot.toPath();

            Path manifestFile = searchRoot.resolve("AndroidManifest.xml");

            try {
                for (int i = 0; i < MAX_PARENTS_FROM_SOURCE_FOLDER; i++) {
                    if (Files.exists(manifestFile) || (manifestFile = searchInside(searchRoot)) != null) {
                        break;
                    } else {
                        if ((searchRoot = searchRoot.getParent()) == null) {
                            break;
                        } else {
                            manifestFile = searchRoot.resolve("AndroidManifest.xml");
                        }
                    }
                }
            } catch (IOException e) {
                // This most likely means, that directory depth was too great. Or that we have
                // wondered too far towards parental directories and no longer have the read access.
                // Or something. Regardless, it's better to bail now instead of annoying user
                // with our attempts any longer

                // not even logging
            }

            return (manifestFile != null && Files.exists(manifestFile)) ? manifestFile.toFile() : null;
        }

        private Path found;

        private Path searchInside(Path directory) throws IOException {
            Files.walkFileTree(directory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), MAX_CHILD_DEPTH, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if ("AndroidManifest.xml".equalsIgnoreCase(file.getFileName().toString())) {
                        found = file;

                        return FileVisitResult.TERMINATE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return visited.contains(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
            });

            visited.add(directory);

            return found;
        }

        @Override
        Iterable<String> possibleLocations() {
            return Collections.singleton("");
        }

        @Override
        boolean applies() {
            return true;
        }
    }

    private static final class GradleAndroidManifestFinderStrategy extends AndroidManifestFinderStrategy {

        static final Pattern GRADLE_GEN_FOLDER = Pattern.compile("^(.*?)build[\\\\/]generated[\\\\/]source[\\\\/]apt(.*)$");

        GradleAndroidManifestFinderStrategy(String sourceFolder) {
            super("Gradle", GRADLE_GEN_FOLDER, sourceFolder);
        }

        @Override
        Iterable<String> possibleLocations() {
            String gradleVariant = matcher.group(2);

            return Arrays.asList("build/intermediates/manifests/full" + gradleVariant, "build/bundles" + gradleVariant);
        }
    }

    private static final class MavenAndroidManifestFinderStrategy extends AndroidManifestFinderStrategy {

        static final Pattern MAVEN_GEN_FOLDER = Pattern.compile("^(.*?)target[\\\\/]generated-sources.*$");

        MavenAndroidManifestFinderStrategy(String sourceFolder) {
            super("Maven", MAVEN_GEN_FOLDER, sourceFolder);
        }

        @Override
        Iterable<String> possibleLocations() {
            return Arrays.asList("target", "src/main", "");
        }
    }

    private static final class EclipseAndroidManifestFinderStrategy extends AndroidManifestFinderStrategy {

        static final Pattern ECLIPSE_GEN_FOLDER = Pattern.compile("^(.*?)\\.apt_generated.*$");

        EclipseAndroidManifestFinderStrategy(String sourceFolder) {
            super("Eclipse", ECLIPSE_GEN_FOLDER, sourceFolder);
        }

        @Override
        Iterable<String> possibleLocations() {
            return Collections.singleton("");
        }
    }
}
