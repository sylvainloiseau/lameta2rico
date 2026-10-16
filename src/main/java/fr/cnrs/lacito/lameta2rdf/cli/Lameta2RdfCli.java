package fr.cnrs.lacito.lameta2rdf.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.eclipse.rdf4j.rio.RDFFormat;

import fr.cnrs.lacito.lameta2rdf.Lameta2RdfConverter;
import fr.cnrs.lacito.lameta2rdf.rico.UnmappedPolicy;
import fr.cnrs.lacito.ricimporterplugin.ConverterOptions;
import fr.cnrs.lacito.ricimporterplugin.RdfConverter;
import fr.cnrs.lacito.ricimporterplugin.RdfOutput;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Command line: {@code lameta2rdf [-f FORMAT] [-p PREFIX] <project dir> <output file>}.
 * Exit codes: 0 success, 1 conversion error, 2 usage error.
 */
@Command(name = "lameta2rdf", mixinStandardHelpOptions = true, version = "lameta2rdf 0.1.0-SNAPSHOT",
        description = "Converts a SayMore/Lameta project into a RiC-O RDF graph.",
        footer = {"", "The output format is taken from --format, else from the output file extension "
                + "(.rdf/.xml/.owl, .ttl, .nt, .nq, .trig, .trix, .n3, .jsonld, .rj, .brf), else RDF/XML."})
public final class Lameta2RdfCli implements Callable<Integer> {

    static final int EXIT_CONVERSION_ERROR = 1;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Parameters(index = "0", paramLabel = "PROJECT_DIR", description = "Root directory of the Lameta project.")
    Path projectDir;

    @Parameters(index = "1", paramLabel = "OUTPUT_FILE", description = "File to write (replaced if it exists).")
    Path output;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT",
            description = "rdfxml, turtle, ntriples, nquads, trig, trix, n3, jsonld, rdfjson, binary "
                    + "(or an RDF4J format name or MIME type).")
    String format;

    @Option(names = {"-p", "--prefix"}, paramLabel = "URI",
            description = "Corpus URI prefix for minted IRIs (default: urn:lameta2rdf:<project dir name>).")
    String prefix;

    @Option(names = "--include-contact-info", description = "Emit people's howToContact field (personal data).")
    boolean includeContactInfo;

    @Option(names = "--unmapped", paramLabel = "POLICY", defaultValue = "description",
            description = "Fields without RiC-O mapping: description (default) or drop.")
    String unmapped;

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();
        UnmappedPolicy.parse(unmapped); // fail early on a bad value
        String base = prefix;
        if (base == null || base.isBlank()) {
            Path name = projectDir.toAbsolutePath().normalize().getFileName();
            base = "urn:lameta2rdf:" + (name == null ? "project" : name);
            err.println("Warning: no --prefix given; using " + base + ". Pass an HTTP prefix for publishable data.");
        }
        ConverterOptions options = ConverterOptions.builder(base)
                .set(Lameta2RdfConverter.OPTION_INCLUDE_CONTACT_INFO, includeContactInfo)
                .set(Lameta2RdfConverter.OPTION_UNMAPPED, unmapped)
                .build();
        RDFFormat rdfFormat = RdfOutput.resolveFormat(format, output, RDFFormat.RDFXML);
        RdfConverter converter = new Lameta2RdfConverter(projectDir, options);
        long count = RdfOutput.write(converter, output, rdfFormat);
        err.println("Wrote " + count + " statements to " + output + " (" + rdfFormat.getName() + ")");
        return 0;
    }

    /** Runs the command and returns its exit code (for tests and embedding). */
    public static int run(PrintWriter out, PrintWriter err, String... args) {
        CommandLine cl = new CommandLine(new Lameta2RdfCli());
        cl.setOut(out);
        cl.setErr(err);
        cl.setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
            commandLine.getErr().println("Error: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            return EXIT_CONVERSION_ERROR;
        });
        return cl.execute(args);
    }

    public static void main(String[] args) {
        int code = run(new PrintWriter(System.out, true), new PrintWriter(System.err, true), args);
        System.exit(code);
    }
}
