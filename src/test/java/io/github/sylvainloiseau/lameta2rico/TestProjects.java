package io.github.sylvainloiseau.lameta2rico;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import fr.cnrs.lacito.ricimporterplugin.ConverterOptions;

/** Access to the sample Lameta projects of {@code src/test/resources/LametaProjects}. */
public final class TestProjects {

    public static final String BASE = "http://tuwari.huma-num.fr/corpus/tuwari";

    private TestProjects() {
    }

    public static Path project(String name) {
        try {
            return Path.of(TestProjects.class.getResource("/LametaProjects/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copies a sample project into {@code target} (so tests can add or break files). */
    public static Path copy(String name, Path target) {
        Path source = project(name);
        Path dest = target.resolve(name);
        try (Stream<Path> files = Files.walk(source)) {
            for (Path p : files.toList()) {
                Path d = dest.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(d);
                } else {
                    Files.copy(p, d, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dest;
    }

    public static Lameta2RicoConverter converter(Path project) {
        return new Lameta2RicoConverter(project, ConverterOptions.of(BASE));
    }

    public static Model convert(String name) {
        return converter(project(name)).toModel();
    }

    public static IRI iri(String pathAfterBase) {
        return Values.iri(BASE + pathAfterBase);
    }

    public static Model expectedTestModel() {
        try (InputStream in = TestProjects.class.getResourceAsStream("/expected/Test.ttl")) {
            return Rio.parse(in, "", RDFFormat.TURTLE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
