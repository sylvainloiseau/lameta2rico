package io.github.sylvainloiseau.lameta2rico.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.sylvainloiseau.lameta2rico.TestProjects;

class Lameta2RicoCliTest {

    @TempDir
    Path tmp;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        return Lameta2RicoCli.run(new PrintWriter(out, true), new PrintWriter(err, true), args);
    }

    private static Model parse(Path file, RDFFormat format) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return Rio.parse(in, "", format);
        }
    }

    private String project() {
        return TestProjects.project("Test").toString();
    }

    @Test
    void formatFromExtension() throws IOException {
        Path ttl = tmp.resolve("out.ttl");
        assertThat(run("-p", TestProjects.BASE, project(), ttl.toString())).isZero();
        assertThat(Models.isomorphic(parse(ttl, RDFFormat.TURTLE), TestProjects.expectedTestModel())).isTrue();
        assertThat(err.toString()).contains("Turtle");
    }

    @Test
    void rdfXmlForRdfExtensionAndAsFallback() throws IOException {
        Path rdf = tmp.resolve("out.rdf");
        Path unknown = tmp.resolve("out.data");
        assertThat(run("-p", TestProjects.BASE, project(), rdf.toString())).isZero();
        assertThat(run("-p", TestProjects.BASE, project(), unknown.toString())).isZero();
        assertThat(Files.readString(rdf)).startsWith("<?xml");
        assertThat(Models.isomorphic(parse(unknown, RDFFormat.RDFXML), TestProjects.expectedTestModel())).isTrue();
    }

    @Test
    void explicitFormatOverridesExtension() throws IOException {
        Path out = tmp.resolve("out.rdf");
        assertThat(run("-f", "nt", "-p", TestProjects.BASE, project(), out.toString())).isZero();
        assertThat(Models.isomorphic(parse(out, RDFFormat.NTRIPLES), TestProjects.expectedTestModel())).isTrue();
    }

    @Test
    void defaultPrefixWithWarning() throws IOException {
        Path out = tmp.resolve("out.nt");
        assertThat(run(project(), out.toString())).isZero();
        assertThat(err.toString()).contains("urn:lameta2rico:Test");
        assertThat(Files.readString(out)).contains("<urn:lameta2rico:Test/project/Test>");
    }

    @Test
    void conversionErrorExitsWithOneAndWritesNothing() {
        Path out = tmp.resolve("out.ttl");
        assertThat(run("-p", TestProjects.BASE, tmp.resolve("missing").toString(), out.toString())).isEqualTo(1);
        assertThat(err.toString()).contains("Error:").contains("existing directory");
        assertThat(out).doesNotExist();
    }

    @Test
    void badOptionValuesExitWithOne() {
        assertThat(run("--unmapped", "keep", project(), tmp.resolve("o.ttl").toString())).isEqualTo(1);
        assertThat(run("-f", "docx", project(), tmp.resolve("o.ttl").toString())).isEqualTo(1);
        assertThat(err.toString()).contains("docx");
    }

    @Test
    void usageErrorExitsWithTwo() {
        assertThat(run()).isEqualTo(2);
        assertThat(err.toString()).contains("PROJECT_DIR");
    }

    @Test
    void help() {
        assertThat(run("--help")).isZero();
        assertThat(out.toString()).contains("--format").contains("--include-contact-info");
    }
}
