# lameta2rico

Converts a [SayMore/Lameta](https://github.com/onset/lameta) project directory into an RDF graph
that uses [RiC-O 1.0.2](https://www.ica.org/standards/RiC/ontology) (Records in Contexts). This
is the Java port of `parse_lameta.py` from rdf4lfd: the ad hoc `lameta:` vocabulary is replaced
by RiC-O, and the output goes through Eclipse RDF4J.

The converter implements the [ricimporterplugin](https://github.com/sylvainloiseau/ricImporterPlugin)
API (`RdfConverter`/`RdfConverterProvider`, name `"lameta"`, input kind `DIRECTORY`). It can therefore be used from the
command line, as a library, or loaded straight into an RDF4J triplestore.

## Build

Requires Java 17+ and Maven. The `ricimporterplugin` dependency is resolved from GitHub
Packages, which needs a token with `read:packages` in `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USER</username>
    <password>YOUR_TOKEN</password>
  </server>
</servers>
```

```
mvn verify      # build + tests; produces target/lameta2rico-<version>-cli.jar (executable)
```

## Command line

```
java -jar target/lameta2rico-0.1.0-SNAPSHOT-cli.jar [-f FORMAT] [-p PREFIX]
     [--include-contact-info] [--unmapped=description|drop] PROJECT_DIR OUTPUT_FILE
```

- **Format**: `-f` (`rdfxml`, `turtle`, `ntriples`, `nquads`, `trig`, `trix`, `n3`, `jsonld`,
  `rdfjson`, `binary`, or an RDF4J format name or MIME type). Otherwise it is taken from the
  output file extension (`.rdf`/`.xml`/`.owl`, `.ttl`, `.nt`, `.nq`, `.trig`, `.trix`, `.n3`,
  `.jsonld`, `.rj`, `.brf`). Otherwise RDF/XML.
- **`-p`**: corpus URI prefix used to mint IRIs, e.g. `http://tuwari.huma-num.fr/corpus/tuwari`.
  Defaults to `urn:lameta2rico:<project dir name>`, with a warning.
- **`--include-contact-info`**: also export people's `howToContact` field (personal data; off
  by default).
- **`--unmapped`**: fields with no RiC-O equivalent (custom fields, parents' languages,
  education…) become `rico:generalDescription "<label>: <value>"` (`description`, the default)
  or are left out (`drop`).

Exit codes: 0 success, 1 conversion error, 2 usage error. The output file is written
atomically, so a failed run never leaves a partial file.

```
java -jar target/lameta2rico-0.1.0-SNAPSHOT-cli.jar -p http://tuwari.huma-num.fr/corpus/tuwari \
     ~/Lameta/Tuwari tuwari.ttl
```

## Library

```java
RdfConverter converter = RdfConverters.get("lameta").create(
        Path.of("/data/Lameta/Tuwari"),
        ConverterOptions.builder("http://tuwari.huma-num.fr/corpus/tuwari")
                .set("unmapped", "drop")          // optional
                .build());
// or: new Lameta2RicoConverter(dir, options)

Model model = converter.toModel();                                    // in memory
RdfOutput.write(converter, Path.of("tuwari.ttl"), RDFFormat.TURTLE);  // to a file
RepositoryLoader.replaceGraph(repository, converter);                 // into an RDF4J repository:
                                                                      // replaces the project's named graph atomically
converter.convert(anyRdfHandler);                                     // any RDF4J sink
```

When used as a library, the RDF4J writer modules and picocli are optional dependencies: add
the `rdf4j-rio-*` modules for the formats you write. `RepositoryLoader` needs
`rdf4j-repository-api`, which an application with a triplestore already has.

## Output model (summary)

| Lameta | RiC-O |
|---|---|
| Project (`.sprj`) | `rico:RecordSet` (`ric-rst:Fonds`), with record sets (`ric-rst:Series`) for `Sessions`, `People`, `DescriptionDocuments`, `OtherDocuments` |
| Session folder | `rico:RecordSet` (`ric-rst:File`) that `rico:documents` a `rico:Activity` (the recording event) |
| Person | `rico:Person` + a `rico:RecordSet` of their documents (`rico:hasOrHadMainSubject`) |
| File (+ `.meta`) | `rico:Record` + `rico:Instantiation` (`rico:hasOrHadDigitalInstantiation`); `rico:identifier` = path relative to the project |
| Contributor | `rico:CreationRelation` (`relationHasSource` / `relationHasTarget` / `creationWithRole`); `rico:hasCreator` for author-like roles |
| Languages, genres, places, roles, keywords… | shared nodes: `rico:Language`, `rico:ActivityType`, `rico:Place`, `rico:RoleType`, `rico:Concept`… |

The full field-by-field mapping, the IRI scheme and the open modelling questions are in
[`.claude/plan/lameta2rdf-java-port.md`](.claude/plan/lameta2rdf-java-port.md).

Differences from the Python converter:
- Contributors and participants are linked to the people of the `People` folder (matched by code, then by name).
- Files without a `.meta` file are included.
- A `.sprj`/`.session`/`.person` file whose name does not match its folder is still found, if it is the only one.
- Empty `.meta` files are ignored, with a warning.
- Hidden files (editor swap files) are skipped.
