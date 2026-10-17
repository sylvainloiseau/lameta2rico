package io.github.sylvainloiseau.lameta2rico;

import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.rio.RDFHandler;

import io.github.sylvainloiseau.lameta2rico.lameta.LametaProjectReader;
import io.github.sylvainloiseau.lameta2rico.rico.RicoGraphBuilder;
import io.github.sylvainloiseau.lameta2rico.rico.UnmappedPolicy;
import io.github.sylvainloiseau.lameta2rico.rico.UriFactory;
import fr.cnrs.lacito.ricimporterplugin.ConverterOptions;
import fr.cnrs.lacito.ricimporterplugin.RdfConversionException;
import fr.cnrs.lacito.ricimporterplugin.RdfConverter;

/**
 * Converts a SayMore/Lameta project directory into a RiC-O graph (the Java port of the Python
 * {@code Lameta2RdfParser}).
 *
 * <p>The project is read again on every {@link #convert} call, so repeated runs reflect the
 * current state of the directory.
 */
public final class Lameta2RicoConverter implements RdfConverter {

    /** Option: emit the {@code howToContact} field of people (personal data). Default {@code false}. */
    public static final String OPTION_INCLUDE_CONTACT_INFO = "includeContactInfo";
    /** Option: {@code description} (default) or {@code drop}, for fields without a RiC-O mapping. */
    public static final String OPTION_UNMAPPED = "unmapped";

    private final Path projectDir;
    private final UriFactory uris;
    private final UnmappedPolicy unmappedPolicy;
    private final boolean includeContactInfo;

    /**
     * @throws RdfConversionException   if {@code projectDir} is not a Lameta project
     * @throws IllegalArgumentException if an option value is invalid
     */
    public Lameta2RicoConverter(Path projectDir, ConverterOptions options) {
        this.projectDir = projectDir.toAbsolutePath().normalize();
        LametaProjectReader.findProjectFile(this.projectDir);
        this.uris = new UriFactory(options.baseIri(), this.projectDir.getFileName().toString());
        this.unmappedPolicy = UnmappedPolicy.parse(options.get(OPTION_UNMAPPED, "description"));
        this.includeContactInfo = options.getBoolean(OPTION_INCLUDE_CONTACT_INFO, false);
    }

    public Path projectDir() {
        return projectDir;
    }

    @Override
    public void convert(RDFHandler handler) {
        new RicoGraphBuilder(uris, unmappedPolicy, includeContactInfo)
                .build(LametaProjectReader.read(projectDir), handler);
    }

    /** The project record set, {@code {P}/project/{projectDir}}. */
    @Override
    public Optional<IRI> suggestedContext() {
        return Optional.of(uris.project());
    }
}
