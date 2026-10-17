package io.github.sylvainloiseau.lameta2rico;

import java.nio.file.Path;
import java.util.List;

import fr.cnrs.lacito.ricimporterplugin.ConverterOptions;
import fr.cnrs.lacito.ricimporterplugin.InputKind;
import fr.cnrs.lacito.ricimporterplugin.OptionDescriptor;
import fr.cnrs.lacito.ricimporterplugin.RdfConverter;
import fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider;

/** Registers the Lameta converter under the name {@value #NAME} for {@code RdfConverters}. */
public class Lameta2RicoConverterProvider implements RdfConverterProvider {

    public static final String NAME = "lameta";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "SayMore/Lameta project directory to RiC-O";
    }

    /** The input is the Lameta project directory, the one holding the {@code .sprj} file. */
    @Override
    public InputKind inputKind() {
        return InputKind.DIRECTORY;
    }

    @Override
    public List<OptionDescriptor> options() {
        return List.of(
                new OptionDescriptor(Lameta2RicoConverter.OPTION_INCLUDE_CONTACT_INFO,
                        "Emit the howToContact field of people (personal data)", "false"),
                new OptionDescriptor(Lameta2RicoConverter.OPTION_UNMAPPED,
                        "Fields without RiC-O mapping: 'description' (rico:generalDescription \"label: value\") or 'drop'",
                        "description"));
    }

    @Override
    public RdfConverter create(Path input, ConverterOptions options) {
        return new Lameta2RicoConverter(input, options);
    }
}
