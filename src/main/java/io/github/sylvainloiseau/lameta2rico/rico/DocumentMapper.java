package io.github.sylvainloiseau.lameta2rico.rico;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.vocabulary.SKOS;

import io.github.sylvainloiseau.lameta2rico.lameta.model.Contribution;
import io.github.sylvainloiseau.lameta2rico.lameta.model.DocumentFile;
import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;
import io.github.sylvainloiseau.lameta2rico.vocabulary.RICO;

/** A document → {@code rico:Record} + {@code rico:Instantiation}, plus its {@code .meta} (plan §5.2, §5.6). */
final class DocumentMapper {

    /** File roles Lameta appends to renamed files ({@code <session>_<Role>.<ext>}), longest first. */
    static final List<String> KNOWN_FILE_ROLES = List.of(
            "Source", "Transcription", "Translation", "Consent", "Careful_Speech", "Oral_Translation", "Annotation")
            .stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();

    private final ConversionContext ctx;
    private final ContributionMapper contributions;

    DocumentMapper(ConversionContext ctx) {
        this.ctx = ctx;
        this.contributions = new ContributionMapper(ctx);
    }

    /**
     * @param container record set including the document
     * @param dirName   name of the folder holding the document (for file-type detection)
     * @param languages languages of the content (session languages), may be empty
     * @param describes resource the document describes (project, for description documents)
     */
    IRI map(DocumentFile document, IRI container, String dirName, List<IRI> languages, Optional<IRI> describes) {
        String fileName = document.fileName();
        IRI record = ctx.uris.record(container, fileName);
        IRI instantiation = ctx.uris.instantiation(container, fileName);

        ctx.sink.add(container, RICO.includesOrIncluded, record);
        ctx.sink.type(record, RICO.Record);
        ctx.sink.literal(record, RICO.title, fileName);
        ctx.sink.add(record, RICO.hasOrHadDigitalInstantiation, instantiation);

        ctx.sink.type(instantiation, RICO.Instantiation);
        ctx.sink.literal(instantiation, RICO.title, fileName);
        ctx.sink.literal(instantiation, RICO.identifier, document.relativePath());
        ctx.sink.add(instantiation, RICO.isOrWasDigitalInstantiationOf, record);

        fileType(fileName, dirName).ifPresent(type -> ctx.sink.add(record, RICO.hasDocumentaryFormType,
                ctx.vocabulary.term("documentaryFormType", null, type, RICO.DocumentaryFormType)));
        languages.forEach(l -> ctx.sink.add(record, RICO.hasOrHadLanguage, l));
        describes.ifPresent(d -> ctx.sink.add(record, RICO.describesOrDescribed, d));

        document.meta().ifPresent(meta -> mapMeta(record, meta));
        return record;
    }

    private void mapMeta(IRI record, XmlElement meta) {
        FieldCursor fields = new FieldCursor(meta);
        fields.text("notes").ifPresent(n -> ctx.sink.literal(record, SKOS.NOTE, n));
        fields.consume("contributions");
        contributions.map(record, Contribution.from(meta), Optional.empty());
        fields.child("CustomFields").ifPresent(cf -> ctx.unmapped.handleCustomFields(record, cf));
        ctx.unmapped.handle(record, fields.remaining());
    }

    /**
     * File type from the Lameta naming convention: {@code <dirName>_<Type>.<ext>}, else a known
     * role at the end of the base name ({@code 131123_03_Source.WAV} → {@code Source}).
     */
    static Optional<String> fileType(String fileName, String dirName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        Matcher m = Pattern.compile("^" + Pattern.quote(dirName) + "_(.+)$").matcher(base);
        if (m.matches()) {
            return Optional.of(m.group(1));
        }
        return KNOWN_FILE_ROLES.stream().filter(role -> base.endsWith("_" + role)).findFirst();
    }
}
