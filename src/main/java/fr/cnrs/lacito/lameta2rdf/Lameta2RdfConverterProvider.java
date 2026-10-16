package fr.cnrs.lacito.lameta2rdf;

import java.nio.file.Path;
import java.util.List;

import fr.cnrs.lacito.ricimporterplugin.ConverterOptions;
import fr.cnrs.lacito.ricimporterplugin.OptionDescriptor;
import fr.cnrs.lacito.ricimporterplugin.RdfConverter;
import fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider;

/** Registers the Lameta converter under the name {@value #NAME} for {@code RdfConverters}. */
public class Lameta2RdfConverterProvider implements RdfConverterProvider {

    public static final String NAME = "lameta";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "SayMore/Lameta project directory to RiC-O";
    }

    @Override
    public List<OptionDescriptor> options() {
        return List.of(
                new OptionDescriptor(Lameta2RdfConverter.OPTION_INCLUDE_CONTACT_INFO,
                        "Emit the howToContact field of people (personal data)", "false"),
                new OptionDescriptor(Lameta2RdfConverter.OPTION_UNMAPPED,
                        "Fields without RiC-O mapping: 'description' (rico:generalDescription \"label: value\") or 'drop'",
                        "description"));
    }

    @Override
    public RdfConverter create(Path input, ConverterOptions options) {
        return new Lameta2RdfConverter(input, options);
    }
}
