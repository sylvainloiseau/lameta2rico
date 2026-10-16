# Plan: port `parse_lameta.py` to a Java Maven module emitting RiC-O

**Status (2026-10-15): implemented.** All steps of §6 are done, and `mvn verify` passes with
65 tests. §9 lists the places where the implementation differs from the plan as first written;
the sections below have been updated to match the code.

## 1. Goal and scope

Re-implement `../rdf4lfd/src/parse_lameta.py` (class `Lameta2RdfParser` and its helpers) as a
standalone Maven module in this directory (`lameta2rico/`), under the Java package root
`fr.cnrs.lacito.lameta2rdf`.

The module reads a SayMore/Lameta project directory, parses its XML metadata files
(`*.sprj`, `*.session`, `*.person`, `*.meta`), lists the documents in each directory and
produces an RDF graph with **Eclipse RDF4J**. The graph can be written to a file in any standard
RDF serialization (CLI), or streamed into a running RDF4J repository by a host application. Both
go through the **`ricimporterplugin`** plugin API (`io.github.sylvainloiseau:ricimporterplugin`,
already implemented and published on GitHub Packages; see §3.1).

The main functional change: **the output no longer uses the ad hoc `lameta:` vocabulary**
(`https://github.com/onset/lameta/`, defined in `src/lameta_namespace.py`). It uses
**RiC-O 1.0.2** (`https://www.ica.org/standards/RiC/ontology#`). Section 5 lists the
term-by-term mapping.

Out of scope:
- The Python `Lameta2RicO` class (post-hoc SPARQL rewriting of `lameta:` → `rico:`). The new
  converter emits RiC-O directly, so that class has no Java equivalent. Its stubs
  (`linkSessionToPeopleNode`, `typeValue`, `addInformationByFilePattern`…) are implemented
  natively where relevant (contributor → Person resolution, file-type detection).
- The other rdf4lfd converters (spreadsheet, directory, RO-Crate, aggregated graph).

## 2. What the Python code does today (behaviour to preserve or fix)

| Python element | Behaviour | Java decision |
|---|---|---|
| `Lameta2RdfParser.__init__` | Asserts the project dir exists; project file = `<dirname>.sprj`; graph bound to `lameta`, `rico`, `mydata` prefixes | Same checks. Prefixes: `rico`, `rdf`, `rdfs`, `xsd`, `skos`, `ric-rst`, plus `mydata` for the corpus prefix |
| `convert()` | Project doc → Sessions → People → DescriptionDocuments → OtherDocuments | **People first**, so the contributor names used in sessions and `.meta` files can be resolved to `rico:Person` nodes (see §5.7) |
| `XmlDocument2rdfTriple` | Generic XML → triples: each element name becomes a `lameta:` predicate. Nested elements get minted URIs `<parent>/<key><rank>`. Attributes become `lameta:<key>/@attr`. `@type` is dropped | **Replaced by an explicit, typed mapping.** A generic XML reader builds an in-memory tree. Per-document mappers turn the known fields into RiC-O. Unknown fields go through a documented fallback (§5.8) |
| `AbstractLametaDirectoryList._walkLametaDirectoryList` | For each subdirectory of `Sessions/` or `People/`, reads `<name><ext>` and the directory content | Same, plus a fix: if `<name>.session` is missing, fall back to the only `*.session` file in the directory and log a warning. `Test2/Sessions/Session3/SessionID3.session` crashes the Python code today |
| `ReadDirectoryContent._readDirectory` | Only files with a paired `.meta` are emitted. It raises an error on a subdirectory. File type is taken from the regex `<dirname>_(.+)<ext>` | Emit **every** regular non-hidden file, whether or not it has a `.meta`. Skip the `.session`/`.person` file itself, `*.meta` files and dotfiles (`.*.swp`). Log subdirectories and skip them instead of raising. Better file-type detection (§5.6) |
| `addSupplementaryDirectory` | `project lameta:DescriptionDocument "path"` literal, for every file **including `.meta`** | Model each directory as a `rico:RecordSet` with one `rico:Record` + `rico:Instantiation` per file. Its `.meta` is parsed as for session files |
| `_add_secure` | `None` value → blank node | Empty elements are **skipped**: no blank nodes, no empty literals |
| Sample files `sample/data/test.*` | Contain leading whitespace before `<?xml` | The XML reader trims the leading whitespace before parsing, since a strict JAXP parser rejects it |
| `projectfile = <dirname>.sprj` | `Test2/` holds `Test.sprj`, so the Python code fails on it | Same fallback as for sessions: the only `*.sprj` file of the directory, with a warning |
| `.meta` parsing | `Test2/Sessions/Session{2,3}/*.WAV.meta` are 0-byte files; `xmltodict` fails on them | Empty (whitespace-only) metadata files are treated as absent, with a warning. Non-empty malformed files still stop the conversion (`RdfConversionException` naming the file) |

## 3. Module layout

```
lameta2rico/
├── pom.xml
├── README.md
├── src/main/java/fr/cnrs/lacito/lameta2rdf/
│   ├── cli/Lameta2RdfCli.java             # picocli entry point (§3.2), output via ricimporterplugin RdfOutput
│   ├── Lameta2RdfConverter.java           # implements fr.cnrs.lacito.ricimporterplugin.RdfConverter (≙ Lameta2RdfParser)
│   ├── Lameta2RdfConverterProvider.java   # implements fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider, name "lameta"
│   ├── lameta/                            # reading the Lameta project (no RDF here)
│   │   ├── LametaLayout.java              # constants: .sprj, Sessions, People, DescriptionDocuments, OtherDocuments, .session, .person, .meta
│   │   ├── LametaProjectReader.java       # directory walker → model below (fallback description files, empty .meta, hidden files)
│   │   ├── xml/XmlElement.java            # tiny immutable tree (name, attributes, text, children)
│   │   ├── xml/LametaXmlReader.java       # JAXP DOM → XmlElement (trims leading ws, UTF-8, DOCTYPE refused, `type` attr dropped)
│   │   └── model/                         # records: LametaProject, Folder (session/person), DocumentFile, Contribution
│   ├── rico/                              # RDF generation (emits to an RDFHandler)
│   │   ├── RicoGraphBuilder.java          # public entry: namespaces + mapper order, startRDF/endRDF
│   │   ├── UnmappedPolicy.java            # public enum DESCRIPTION | DROP (§5.8)
│   │   ├── UriFactory.java                # every URI is minted here (§4)
│   │   ├── ConversionContext.java         # per-run state shared by the mappers
│   │   ├── RdfSink.java                   # wraps the RDFHandler: drops repeated statements, typed date/year literals
│   │   ├── FieldCursor.java               # reads XML fields and tracks unused ones for the fallback
│   │   ├── VocabularyRegistry.java        # shared nodes: Language (code↔name), Place chains, controlled values
│   │   ├── AgentRegistry.java             # code/name → rico:Person resolution
│   │   ├── ProjectMapper.java
│   │   ├── SessionMapper.java
│   │   ├── PersonMapper.java
│   │   ├── DocumentMapper.java            # file → Record + Instantiation (+ .meta), file-type detection
│   │   ├── ContributionMapper.java        # contributions → rico:CreationRelation
│   │   └── UnmappedFieldHandler.java      # fallback policy (§5.8)
│   └── vocabulary/
│       ├── RICO.java                      # RDF4J IRI constants (Values.iri), in the style of org.eclipse.rdf4j.model.vocabulary.*
│       └── RicRst.java                    # ric-rst record set types (Fonds, Series, File)
├── src/main/resources/
│   ├── ontologies/RiC-O_1-0-2.rdf         # copied from ../rdf4lfd/src/ontologies/
│   └── META-INF/services/fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider   # registers Lameta2RdfConverterProvider
└── src/test/
    ├── java/fr/cnrs/lacito/lameta2rdf/…   # see §7
    └── resources/
        ├── LametaProjects/{Test,Test2}    # copied from ../rdf4lfd/sample/data/LametaProjects (WAV files emptied: only names matter)
        ├── xml/test.{session,person,meta} # copied from ../rdf4lfd/sample/data (leading-whitespace cases)
        └── expected/Test.ttl              # golden output for Test, reviewed by hand against §5
```

### pom.xml essentials
- `groupId` `fr.cnrs.lacito`, `artifactId` `lameta2rdf`, packaging `jar`, Java **17** (`maven.compiler.release`).
- **RDF library: Eclipse RDF4J 5.2.2**, the version ricimporterplugin uses, through a `rdf4j.version` property.
- Library dependencies (what a host application inherits):
  - **`io.github.sylvainloiseau:ricimporterplugin:0.1.0-SNAPSHOT`**: plugin API, `RepositoryLoader`, `RdfOutput`. It brings in `rdf4j-model` + `rdf4j-rio-api`.
  - `rdf4j-model-vocabulary` (`RDF`, `RDFS`, `XSD`, `SKOS`).
  - `org.slf4j:slf4j-api`: warnings about skipped files and unmapped fields.
  - `rdf4j-repository-api` is **not** declared here. It is `provided` in ricimporterplugin, so only hosts that call `RepositoryLoader` need it, and they already have it.
- CLI and serialization dependencies:
  - `rdf4j-rio-rdfxml`, `-turtle`, `-ntriples`, `-nquads`, `-trig`, `-trix`, `-n3`, `-jsonld`, `-rdfjson`, `-binary`. Rio discovers the writers through `ServiceLoader`, so adding a format means adding its jar.
  - `info.picocli:picocli`, `slf4j-simple` (runtime).
  - These go in the shaded CLI jar. For library consumers they are declared `<optional>true</optional>`, so a host keeps its own Rio set.
- Test: JUnit Jupiter 5.10, AssertJ 3.27, `rdf4j-repository-api` + `rdf4j-repository-sail` + `rdf4j-sail-memory` (one end-to-end load through `RepositoryLoader`).
- XML parsing uses the JDK (`javax.xml.parsers`), so it needs no dependency (replaces `xmltodict`).
- `maven-shade-plugin` builds an executable jar (classifier `cli`) with `Main-Class: fr.cnrs.lacito.lameta2rdf.cli.Lameta2RdfCli`. It needs the `ServicesResourceTransformer`, otherwise the Rio writer registrations and the converter SPI files overwrite each other in the merged jar.
- The plugin API is resolved from **GitHub Packages**:

  ```xml
  <repositories>
    <repository>
      <id>github</id>
      <name>GitHub Packages</name>
      <url>https://maven.pkg.github.com/sylvainloiseau/*</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>io.github.sylvainloiseau</groupId>
      <artifactId>ricimporterplugin</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>
    …
  </dependencies>
  ```

  GitHub Packages requires authentication even for reads. Credentials go in a `<server>` with
  `<id>github</id>` in `~/.m2/settings.xml` (username + a token with `read:packages`), never in
  the POM. For CI, use the same server id with `GITHUB_TOKEN`.

### 3.1 Library API and triplestore integration: the `ricimporterplugin` dependency

The converter-neutral plugin API was moved out of this module, into the separate Maven module
**`ricimporterplugin`** (`io.github.sylvainloiseau:ricimporterplugin`, package
`fr.cnrs.lacito.ricimporterplugin`, `Automatic-Module-Name: fr.cnrs.lacito.ricimporterplugin`;
source repository: `github.com/sylvainloiseau/ricImporterPlugin`). It was first developed as
`app2ric` (`fr.cnrs.lacito:app2ric`, package `fr.cnrs.lacito.app2ric`), then renamed; its API
is unchanged. It is implemented and tested; see its plan (`.claude/plan/`) and README.
This module only **implements** it:

- `Lameta2RdfConverter implements RdfConverter`:
  - `convert(RDFHandler)` streams the graph: `startRDF`, namespaces, statements, `endRDF`. It
    follows the contract in the `RdfConverter` Javadoc: no context on statements, no duplicates,
    repeatable, and input errors reported as `RdfConversionException`.
  - `suggestedContext()` returns the project record set IRI `{P}/project/{projectDir}`.
  - `toModel()` is inherited.
- `Lameta2RdfConverterProvider implements RdfConverterProvider`: `name()` = `"lameta"`. `create(projectDir, options)` validates the directory eagerly (it must contain `<dir>.sprj`). `options()` declares:

  | Option | Default | Meaning |
  |---|---|---|
  | `includeContactInfo` | `false` | emit `howToContact` (§5.5) |
  | `unmapped` | `description` | `description` or `drop` (§5.8) |

  `ConverterOptions.baseIri()` is the corpus URI prefix `{P}`. ricimporterplugin already strips any trailing `/`.
- Registered in `META-INF/services/fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider`.

What a host application with an RDF4J triplestore does (nothing Lameta-specific):

```java
RdfConverter c = RdfConverters.get("lameta")
        .create(projectDir, ConverterOptions.builder("http://tuwari.huma-num.fr/corpus/tuwari").build());
RepositoryLoader.replaceGraph(repository, c);   // atomic: clear the project graph + stream into it via RDFInserter
```

`RepositoryLoader` (in ricimporterplugin) runs one transaction with the repository's default isolation
level: it clears only the target named graph, streams the statements through RDF4J's
`RDFInserter` (no intermediate `Model`), commits, and rolls back on failure. It can also join a
caller's transaction (`load(conn, …)`). *(Implementation note: the ricimporterplugin tests showed that
`IsolationLevels.NONE`, proposed in the earlier version of this plan, breaks the rollback on the
memory store, so it is no longer the default.)*

**Internal dedup (still this module's job).** Vocabulary nodes (languages, places, roles…) are
reused across sessions. `VocabularyRegistry` records which node descriptions were already
emitted, so each one is sent once. Streamed files therefore contain no duplicate statements,
as the `RdfConverter` contract requires.

Alternatives considered and not chosen (unchanged):
- Registering Lameta as a Rio `RDFParserFactory`/`RDFFormat`: the Rio parser contract is a single `InputStream`, and a Lameta project is a directory tree.
- Declarative mappings (RML/CARML): they would not handle the directory walking, the person resolution or the language reconciliation.
- A SAIL or federated virtual repository: far too heavy for a batch conversion.

### 3.2 CLI

```
java -jar lameta2rdf-cli.jar [-f <format>] [-p <corpus URI prefix>]
                             [--include-contact-info] [--unmapped=description|drop]
                             <lameta project dir> <output file>
```

- Two positional arguments: the project root and the output file.
- **Format resolution** is delegated to `RdfOutput.resolveFormat(formatOption, outputFile, RDFFormat.RDFXML)` (ricimporterplugin):
  1. `-f/--format` if given. It accepts a short name (`rdfxml`/`xml`, `turtle`/`ttl`,
     `ntriples`/`nt`, `nquads`/`nq`, `trig`, `trix`, `n3`, `jsonld`, `rdfjson`/`rj`,
     `binary`/`brf`), an RDF4J format name, or a MIME type.
  2. Otherwise, the format is guessed from the output file extension (`.rdf`/`.owl`/`.xml` →
     RDF/XML, `.ttl`, `.nt`, `.nq`, `.trig`, `.trix`, `.jsonld`, `.rj`, `.brf`…).
  3. Otherwise, **RDF/XML**.
- Writing is delegated to `RdfOutput.write(converter, outputFile, format)`, which covers:
  - pretty-printing (and inlined blank nodes for Turtle/TriG);
  - putting the statements in the suggested context (the project IRI) for quad formats;
  - writing to a temporary file and moving it into place atomically, so a failed run never
    leaves a truncated file;
  - a clear error if the writer jar for an explicitly requested format is missing.
- Namespaces (`rico`, `ric-rst`, `skos`, `xsd`, `rdf`, `rdfs`, `mydata`) are emitted by the
  converter through `handleNamespace` before any statement.
- `--include-contact-info` and `--unmapped` are mapped to the provider options above.
- `-p/--prefix`: corpus URI prefix. It is optional, and defaults to
  `urn:lameta2rdf:{projectDirName}` with a warning. Pass a real HTTP prefix for publishable data.
- Exit codes: 0 = success, 1 = conversion error (message on stderr), 2 = usage error (picocli).

## 4. URI scheme (`UriFactory`)

`{P}` = corpus URI prefix (e.g. `http://tuwari.huma-num.fr/corpus/tuwari`) with any trailing `/` removed.
Path segments are percent-encoded (UTF-8, space → `%20`; the Python code used `quote_plus`, so `+`).

| Entity | URI | Python equivalent |
|---|---|---|
| Project record set | `{P}/project/{projectDir}` | same |
| Sessions record set | `{P}/project/{projectDir}/Sessions` | `{P}/Sessions` |
| People record set | `{P}/project/{projectDir}/People` | `{P}/People` |
| DescriptionDocuments / OtherDocuments record sets | `{P}/project/{projectDir}/DescriptionDocuments` (resp. `OtherDocuments`) | — (literals only) |
| Session record set | `{P}/session/{sessionDir}` | `{P}/Sessions/{sessionDir}` |
| Session activity (the recording event) | `{P}/session/{sessionDir}/activity` | — (merged with the session node) |
| Person (agent) | `{P}/person/{code}`, or `{P}/person/{personDir}` if `code` is empty | `{P}/People/{personDir}` |
| Agent not found among people (`Depositor`, `ContactPerson`) | `{P}/agent/{name}` (`rico:Agent`) | — |
| Person documents record set | `{P}/person/{personDir}/documents` | — (merged with the person node) |
| Nickname | `{P}/person/{code}/name/nickname` (`rico:AgentName`) | — |
| Record (one per file) | `{containerURI}/record/{fileName}` | `{containerURI}/{fileName}` |
| Instantiation | `{containerURI}/instantiation/{fileName}` | same node as the record |
| Creation relation | `{sourceURI}/creation/{n}` (n = 1-based rank in `<contributions>`) | `{uri}/contributions/contributor{n}` |
| Language | `{P}/language/{iso639}`, or `{P}/language/name/{name}` if no code is known | — |
| Controlled-value nodes | `{P}/{kind}/{facet}/{value}`, where kind ∈ `roleType`, `activityType`, `concept`, `place`, `rule`, `demographicGroup`, `occupationType`, `documentaryFormType`, `recordState`, `activity` | — |

## 5. Vocabulary mapping: `lameta:` → RiC-O

Prefixes:
`rico:` = `https://www.ica.org/standards/RiC/ontology#` (the Python `rico_namespace.py` used the wrong IRI `…/ontology/2.0/`, which is fixed here);
`ric-rst:` = `https://www.ica.org/standards/RiC/vocabularies/recordSetTypes#`;
`skos:` = `http://www.w3.org/2004/02/skos/core#`.

Every `rico:` term below was checked against `RiC-O_1-0-2.rdf`, including its domain and range.
Notation: `S` = the session record set, `A` = the session activity, `R` = a file Record,
`I` = its Instantiation, `Pr` = the project record set, `Pe` = a Person.

### 5.1 Classes (`rdf:type` values)

| lameta (old) | RiC-O (new) | Notes |
|---|---|---|
| `lameta:Project` | `rico:RecordSet` + `rico:hasRecordSetType ric-rst:Fonds` | The fieldwork corpus is produced organically by the project; `ric-rst:Collection` is the alternative (Q1) |
| `lameta:Sessions` (dir link) | `rico:RecordSet` + `ric-rst:Series`, linked by `Pr rico:includesOrIncluded` | |
| `lameta:Session` | split into **`rico:RecordSet` (`ric-rst:File`)** for the session folder **+ `rico:Activity`** for the recording event, linked by `S rico:documents A` | Old `Lameta2RicO` mapped Session → `rico:Event`. `rico:Activity` ⊂ `rico:Event` and is the range of `rico:documents` |
| `lameta:People` (dir link) | `rico:RecordSet` + `ric-rst:Series`, linked by `Pr rico:includesOrIncluded` | |
| `lameta:People` (type of each person) | **`rico:Person`** + a `rico:RecordSet` (`ric-rst:File`) holding that person's documents, linked by `rico:hasOrHadMainSubject Pe` | A Person cannot include records in RiC-O |
| `lameta:File` | **`rico:Record`** (intellectual content) + **`rico:Instantiation`** (the file on disk), linked by `R rico:hasOrHadDigitalInstantiation I` | Old `Lameta2RicO` used `rico:Instantiation` only |
| `lameta:DescriptionDocument` / `lameta:OtherDocument` | `rico:RecordSet` (`ric-rst:Series`) per directory; each file → `rico:Record` + `rico:Instantiation`. Description documents also get `R rico:describesOrDescribed Pr` | Python emitted path literals only |

### 5.2 Structural predicates

| lameta (old) | RiC-O (new) |
|---|---|
| `Pr lameta:Sessions <…/Sessions>` | `Pr rico:includesOrIncluded <…/Sessions>` |
| `<…/Sessions> lameta:Session S` | `<…/Sessions> rico:includesOrIncluded S` |
| `Pr lameta:People <…/People>` | `Pr rico:includesOrIncluded <…/People>` |
| `<…/People> lameta:People Pe` | `<…/People> rico:includesOrIncluded <person documents RS>`; `<person documents RS> rico:hasOrHadMainSubject Pe` |
| `X lameta:File F` | `X rico:includesOrIncluded R` (X is a session, person-documents or supplementary record set) |
| `F lameta:FileUrl "path"` | `I rico:identifier "Sessions/Session1_ID/131123_03_Source.WAV"` (path relative to the project root, so the output is portable) |
| `F lameta:FileType "Source"` | `R rico:hasDocumentaryFormType <{P}/documentaryFormType/Source>` (`rico:DocumentaryFormType`, `rico:name "Source"`) |
| `Pr lameta:DescriptionDocument "path"` | see 5.1 |
| `Pr lameta:OtherDocument "path"` | see 5.1 |
| nested-node minting (`<uri>/<key><n>`) | removed; only the n-ary relations and vocabulary nodes of §4 get URIs |
| (new) | `R rico:title "<file name>"`, `I rico:title "<file name>"`, `I rico:isOrWasDigitalInstantiationOf R` (inverse, for easy querying) |

### 5.3 Project file (`<Project>` in `*.sprj`)

| lameta (old) | RiC-O (new) | Notes |
|---|---|---|
| `lameta:Title` | `Pr rico:title` | |
| `lameta:ProjectDescription` | `Pr rico:scopeAndContent` | |
| `lameta:VernacularISO3CodeAndName` (`"tww : Tuwari"`) | `Pr rico:hasOrHadSomeMembersWithLanguage <{P}/language/tww>`; Language node: `a rico:Language; rico:identifier "tww"; rico:name "Tuwari"` | Split on `:`. The code↔name pair goes into the registry so that persons can say `Tuwari` (§5.5) |
| `lameta:AnalysisISO3CodeAndName` (`"en : English"`) | same, with the `en` Language | Vernacular vs analysis distinction lost (Q3) |
| `lameta:Location` | `Pr rico:isAssociatedWithPlace <place>`; Place: `a rico:Place; rico:name "<Location>"; rico:location "<Location>"` | |
| `lameta:Region`, `lameta:Country`, `lameta:Continent` | one `rico:Place` each (`rico:name`), chained by `rico:isOrWasPartOf` (Location → Region → Country → Continent). The most specific one is linked by `rico:isAssociatedWithPlace` | Shared with the session `Location_*` fields: the same names give the same nodes |
| `lameta:FundingProjectTitle` + `lameta:grantId` | `Pr rico:resultsOrResultedFrom <{P}/activity/funding/{grantId}>`; Activity: `a rico:Activity; rico:name "<FundingProjectTitle>"; rico:identifier "<grantId>"` | |
| `lameta:AccessProtocol` (`PARADISEC`) | `Pr rico:isOrWasRegulatedBy <{P}/rule/accessProtocol/PARADISEC>`; `a rico:Rule; rico:name "PARADISEC"` | |
| `lameta:Depositor` | `Pr rico:hasAccumulator <agent>` | Agent resolved through `AgentRegistry`; otherwise a new `rico:Agent` with `rico:name` (Q4) |
| `lameta:ContactPerson` | `Pr rico:hasOrHadManager <agent>` | Closest RiC-O property (Q4) |
| `lameta:DateAvailable` | `Pr rico:publicationDate "2021-09-09"^^xsd:date` | |
| `lameta:contributions` | `rico:CreationRelation` nodes (§5.7) | |

### 5.4 Session file (`<Session>` in `*.session`)

| lameta (old) | RiC-O (new) | Notes |
|---|---|---|
| `lameta:id` | `S rico:identifier`, `A rico:identifier` | |
| `lameta:title` | `S rico:title`; `A rico:name` | |
| `lameta:languages` | split on `;` or `,`; `S rico:hasOrHadSomeMembersWithLanguage <language>`; also `R rico:hasOrHadLanguage` on each Record of the session | `hasOrHadLanguage` cannot be used on a RecordSet (its domain is Agent/Record/RecordPart) |
| `lameta:workingLanguages` | same properties as `languages` | Subject vs working language distinction lost (Q3) |
| `lameta:genre` | `A rico:hasActivityType <{P}/activityType/genre/singing>` (`a rico:ActivityType; rico:name`) | |
| `Sub-Genre` (commented out in `LametaNS`) | `A rico:hasActivityType <{P}/activityType/subGenre/…>` + `skos:broader <genre node>` | |
| `lameta:location` | `A rico:hasOrHadLocation <place>` (Place as in §5.3) | |
| `lameta:Location_Region` / `Location_Country` / `Location_Continent` (AdditionalFields) | `rico:Place` chain as in §5.3; `location` `rico:isOrWasPartOf` region… | |
| `lameta:access` (`O`) | `S rico:isOrWasRegulatedBy <{P}/rule/access/O>` (`a rico:Rule; rico:name "O"; rico:isOrWasPartOf <access protocol rule>`) | |
| `lameta:accessDescription` | `S rico:conditionsOfAccess` | |
| `lameta:synopsis` | `S rico:scopeAndContent`; `A rico:generalDescription` | |
| `lameta:status` (`In_Progress`) | `S rico:hasOrHadAllMembersWithRecordState <{P}/recordState/In_Progress>` (`a rico:RecordState`) | |
| `lameta:date` | `A rico:date "2021-09-09"^^xsd:date`; `S rico:creationDate "…"^^xsd:date` | Typed as `xsd:date` only when it parses as ISO-8601, otherwise a plain literal |
| `lameta:keyword` (`"Keyword1, keyword2"`) | split on `,`; for each: `S rico:hasOrHadSubject <{P}/concept/keyword/…>` (`a rico:Concept; rico:name`) | |
| `lameta:topic` | `S rico:hasOrHadMainSubject <{P}/concept/topic/…>` | |
| `lameta:notes` | `S skos:note` | RiC-O 1.0.2 has no note property (Q2) |
| `lameta:participants` (`"JD;JD;"`) | split on `;`, deduplicate, resolve; `A rico:hasOrHadParticipant Pe` | |
| `lameta:contributions` / `contributor` / `name` / `role` / `date` / `comments` | `rico:CreationRelation` with source `S` (§5.7); each contributor is also `A rico:hasOrHadParticipant Pe` | |
| `lameta:Involvement` (AdditionalFields) | `A rico:hasActivityType <{P}/activityType/involvement/elicited>` | |
| `lameta:Planning_Type` | `A rico:hasActivityType <{P}/activityType/planningType/…>` | |
| `lameta:Social_Context` | `A rico:hasActivityType <{P}/activityType/socialContext/…>` | |
| `lameta:AdditionalFields` (container) | removed (only its children are mapped) | |
| `lameta:CustomFields`, `CustomField1`, `CustomFieldB` | fallback (§5.8) | |

### 5.5 Person file (`<Person>` in `*.person`)

| lameta (old) | RiC-O (new) | Notes |
|---|---|---|
| `lameta:name` | `Pe rico:name` | as in old `Lameta2RicO` |
| `lameta:nickName` | `Pe rico:hasOrHadAgentName [a rico:AgentName; rico:textualValue "John"]` (URI `{personURI}/name/nickname`) | |
| `lameta:code` | `Pe rico:identifier` | as in old `Lameta2RicO` |
| `lameta:primaryLanguage` | `Pe rico:hasOrHadLanguage <language>` | The value may be a name (`Tuwari`) or a code (`tpi`). Resolved through the registry, using the code↔name pairs from the project file |
| `lameta:otherLanguage0`, `otherLanguage1`, … (any `otherLanguageN`) | `Pe rico:hasOrHadLanguage <language>` | |
| `lameta:primaryLanguageLearnedIn` | fallback (§5.8) | No RiC-O equivalent |
| `lameta:fathersLanguage`, `lameta:mothersLanguage` | fallback (§5.8) | Would need modelling the parents as Persons with a `rico:FamilyRelation`; not done |
| `lameta:education` | fallback (§5.8) | |
| `lameta:birthYear` | `Pe rico:birthDate "1977"^^xsd:gYear` | Old `Lameta2RicO` used `rico:date` |
| `lameta:gender` | `Pe rico:hasOrHadDemographicGroup <{P}/demographicGroup/gender/Male>` (`a rico:DemographicGroup`) | |
| `lameta:ethnicGroup` | `Pe rico:hasOrHadDemographicGroup <{P}/demographicGroup/ethnicGroup/…>` | |
| `lameta:primaryOccupation` | `Pe rico:hasOrHadOccupationOfType <{P}/occupationType/…>` (`a rico:OccupationType`) | |
| `lameta:howToContact` | **not emitted** by default (personal data); with `--include-contact-info` it goes through the fallback | |
| `lameta:description` | `Pe rico:generalDescription` | as in old `Lameta2RicO` |
| `lameta:notes` | `Pe skos:note` | Old `Lameta2RicO` targeted `rico:note`, which does not exist in RiC-O 1.0.2 |
| `lameta:contributions` | rarely filled; handled like any record (§5.7) with source = the person documents record set | |

### 5.6 File metadata (`*.meta`) and file names

| lameta (old) | RiC-O (new) | Notes |
|---|---|---|
| `lameta:notes` | `R skos:note` | |
| `lameta:contributions`/`contributor` | `rico:CreationRelation`, source = `R` (§5.7) | |
| `smxrole` (not in `LametaNS`) | a second `rico:creationWithRole` RoleType, unless it is `unspecified` | |
| `lameta:CustomFields` | fallback (§5.8) | |
| `lameta:FileType` | `R rico:hasDocumentaryFormType` (§5.2). Detection: (1) basename without extension matches `^{dirName}_(.+)$`; else (2) the last `_`-segment is a known Lameta role (`Source`, `Transcription`, `Translation`, `Consent`, `Careful_Speech`, `Oral_Translation`, `Annotation`) | (2) is new: with the sample names (`131123_03_Source.WAV` in `Session1_ID`) the Python regex never matches |

### 5.7 Contributions (`<contributions><contributor>…`) → `rico:CreationRelation`

For each `<contributor>` under a source `X` (project, session record set, or file Record):

```
<X/creation/n> a rico:CreationRelation ;
    rico:relationHasSource X ;
    rico:relationHasTarget Pe ;
    rico:creationWithRole <{P}/roleType/participant> ;   # a rico:RoleType ; rico:name "participant"
    rico:date "…"^^xsd:date ;                            # omitted when Lameta's sentinel 0001-01-01
    rico:generalDescription "<comments>" .
```

`rico:hasCreator` shortcut triples (`X rico:hasCreator Pe`) are added only for author-like
roles (`author`, `compiler`, `recorder`, `transcriber`, `translator`, `annotator`,
`researcher`), not for `participant`/`speaker`/`consultant`. For sessions, the contributor is
also `A rico:hasOrHadParticipant Pe`.

**Agent resolution (`AgentRegistry`)**: the People directory is parsed first and every person
is indexed by `code` and by `name`. In `<contributor><name>`, `<participants>`, `Depositor`
and `ContactPerson`, values are looked up by code first, then by name. When nothing matches,
the converter mints `{P}/person/{value}` (`a rico:Person; rico:name "<value>"`) and logs a
warning. This replaces the unfinished `Lameta2RicO.linkSessionToPeopleNode`.

### 5.8 Unmapped fields (fallback policy)

These are `CustomFields/*`, `primaryLanguageLearnedIn`, `fathersLanguage`, `mothersLanguage`,
`education`, `howToContact` (when enabled), and any element not listed above.

- `--unmapped=description` (default): emit `<subject> rico:generalDescription "<label>: <value>"`.
  The label is the element name with camelCase or `_` split into words (e.g. `"Fathers language: en"`, `"Primary language learned in: …"`). `CustomFields` children keep their own name as the label (`"CustomField1: CustomField1Value"`).
- `--unmapped=drop`: emit nothing.
- In both modes, each distinct unmapped element name is logged once at INFO level.

This keeps the output inside RiC-O (+ `skos:note`) with no ad hoc namespace.

### 5.9 `LametaNS` terms with no mapping

`x`, `parent`, `sub1`, `sub2`, `super`, `f`, `xml`, `_pytestfixturefunction`, `f/@attr`,
`parent/@attr1`, `parent/@attr2` are artefacts of the Python unit tests and have no
equivalent. `lameta:Sessions`, `lameta:People`, `lameta:AdditionalFields` and
`lameta:CustomFields` are containers and are replaced by the structure in §5.2.

## 6. Implementation steps (all done)

0. **Prerequisite (done)**: `ricimporterplugin` is implemented and published on GitHub Packages.
   Check that `~/.m2/settings.xml` has the `github` server credentials.
1. **Scaffold**: `pom.xml` (with the `ricimporterplugin` dependency), package skeleton, `.gitignore`. Copy `RiC-O_1-0-2.rdf` into
   `src/main/resources/ontologies/`. Copy `sample/data/LametaProjects/{Test,Test2}` and
   `sample/data/test.{session,person,meta}` into `src/test/resources/`.
2. **Vocabulary**: `RICO.java` with RDF4J `IRI` constants (`Values.iri(NAMESPACE, "…")`) for every
   term in §5, plus `NS` for `Namespace` registration. `RicRst.java` for record set types. Write a
   test that parses the bundled ontology with `Rio` (RDF/XML) into a `Model` and asserts
   that every constant in `RICO` is declared there (catches typos and the wrong-namespace bug).
3. **XML reading**: `LametaXmlReader` (secure JAXP: disallow DOCTYPE, no external entities; trim
   leading whitespace; ignore `type` attributes) → `XmlElement`. Port the existing
   `TestDictionary2Triple`/`TestSelectMappingKind` cases as reader tests (text, attributes,
   repeated elements, nesting, empty elements).
4. **Lameta model**: `LametaProject` walks the directory and builds `ProjectMetadata`,
   `List<PersonMetadata>`, `List<SessionMetadata>` and the supplementary documents. Each holds
   its `DocumentFile`s (path, optional `.meta` → `FileMetadata`) and `Contribution`s. This
   includes the fixes in §2 (fallback `.session` file, skipping dotfiles and subdirectories,
   files without `.meta`).
5. **Registries**: `UriFactory`, `VocabularyRegistry` (get-or-create, deterministic URIs,
   language code↔name reconciliation), `AgentRegistry`.
6. **Mappers**, in conversion order: `PersonMapper` → `ProjectMapper` → `SessionMapper` →
   `DocumentMapper` → `ContributionMapper`, plus `UnmappedFieldHandler`. Each mapper is a
   function `(metadata, RDFHandler)`. Tests use a `StatementCollector` to check each one on its own.
7. **Orchestrator + plugin**: `Lameta2RdfConverter implements RdfConverter`,
   `Lameta2RdfConverterProvider` (options `includeContactInfo`, `unmapped`) + the
   `META-INF/services/fr.cnrs.lacito.ricimporterplugin.RdfConverterProvider` registration (§3.1).
8. **CLI**: `Lameta2RdfCli` (§3.2): picocli arguments → `ConverterOptions`, then
   `RdfOutput.resolveFormat` + `RdfOutput.write`, exit codes. Shade-plugin setup with the
   `ServicesResourceTransformer`.
9. **Integration tests and golden file**: convert `Test` and write
   `src/test/resources/expected/Test.ttl`. Compare with `org.eclipse.rdf4j.model.util.Models.isomorphic`. Review the
   golden file by hand once against this mapping.
10. **README**: CLI usage, library usage (`RdfConverters.get("lameta")`, then `toModel()` /
    `RdfOutput.write` / `RepositoryLoader.replaceGraph`; link to the ricimporterplugin README for details),
    URI scheme, a summary of the mapping table, known losses (Q1–Q5).

## 7. Tests (implemented: 65, all passing)

| Test class | What it checks |
|---|---|
| `vocabulary.RicoVocabularyTest` | Every `RICO` constant is declared (`owl:Class` / `owl:ObjectProperty` / `owl:DatatypeProperty`) in the bundled `RiC-O_1-0-2.rdf`; the namespace is the ontology's |
| `lameta.xml.LametaXmlReaderTest` | Ported Python XML cases (text, attributes, repetition, nesting, empty elements); leading whitespace (`xml/test.*`); non-ASCII; DOCTYPE refused; empty file absent; malformed file names the file |
| `lameta.LametaProjectReaderTest` | Folder listing and order; files without `.meta` included and `.meta` files excluded; `.sprj`/`.session` fallback (Test2); empty `.meta` ignored; dotfiles and subdirectories skipped; non-project and malformed-file errors |
| `rico.MappingHelpersTest` | File-type detection; humanized labels; list splitting; ISO date check; URI percent-encoding; language code↔name reconciliation; `UnmappedPolicy` parsing; sink dedup |
| `RicoMappingTest` (nested: Project, Person, Session, Document, Options) | The rules of §5 on the converted `Test` project, e.g. `"tww : Tuwari"` is one node reused by `primaryLanguage "Tuwari"`; `"JD;JD;"` gives one participant; a `0001-01-01` date is dropped; `xsd:gYear` birth year; `howToContact` absent by default and present with the option; `unmapped=drop` |
| `Lameta2RdfConverterTest` | Golden file (`Models.isomorphic` with `expected/Test.ttl`); Test2 converts; vocabulary closure (only RiC-O terms from the ontology, `rdf`/`rdfs`/`xsd`/`skos`, `ric-rst` values, no `lameta:` IRI); streaming: one `startRDF`/`endRDF`, no duplicates, no contexts, equal to `toModel()`; repeatable; `suggestedContext`; eager rejection of bad input/options; ServiceLoader discovery; `RepositoryLoader.replaceGraph` twice into a memory store gives exactly `toModel()` in the project graph |
| `cli.Lameta2RdfCliTest` | Format from the extension (`.ttl`, `.rdf`), RDF/XML fallback, `-f` override; default prefix + warning; exit 1 with no output file on a conversion error; exit 1 on bad `--unmapped`/`-f`; exit 2 on a usage error; `--help` |

A mutation check was run once: changing one name in the golden file, and disabling the
`0001-01-01` filter, made 6 tests fail (golden, mapping and CLI); both changes were reverted.

## 8. Open questions (defaults chosen; easy to change later)

- **Q1**: Project record set type: `ric-rst:Fonds` (default) or `ric-rst:Collection`.
- **Q2**: `notes` → `skos:note` (default), because RiC-O 1.0.2 has no note property. The pure-RiC-O alternative is `rico:generalDescription`, which then collides with `synopsis`/`description` (person).
- **Q3**: The vernacular/analysis (project) and subject/working (session) language roles are flattened onto the same RiC-O properties. Keeping them would need either a small sub-property extension or `rico:generalDescription` annotations.
- **Q4**: `Depositor` → `rico:hasAccumulator` and `ContactPerson` → `rico:hasOrHadManager` are approximations. Both hold names of people who may be absent from the People directory.
- **Q5**: The `ric-rst:` values come from the separate RiC record set types vocabulary, which is not part of `RiC-O_1-0-2.rdf`. Bundle it, or reference it only by IRI (default)?

## 9. Implementation notes (differences from the first version of this plan)

1. **Dedup at statement level.** Instead of `VocabularyRegistry` tracking which node
   descriptions were emitted, `RdfSink` keeps a set of the statements sent and drops repeats.
   This is simpler, covers every shared node (and person nodes reached from several
   sessions), and costs memory proportional to the graph, which is small for Lameta metadata.
2. **Lighter Lameta model.** The plan listed typed records (`ProjectMetadata`,
   `SessionMetadata`…). The implementation keeps each metadata file as an `XmlElement` inside
   `LametaProject` / `Folder` / `DocumentFile`, and the mappers read fields through
   `FieldCursor`, which also collects the unused fields for the fallback (§5.8). The walker
   is `LametaProjectReader`.
3. **Public entry point in `rico`.** `RicoGraphBuilder` (public) runs the mappers in order:
   register project languages → register people → people → project → sessions. This lets
   the mappers, sink and registries stay package-private. `UnmappedPolicy` is a public enum.
4. **Robustness found with the samples.** Empty `.meta` files are ignored (Test2 has two).
   The `.sprj` fallback was added, because Test2 holds `Test.sprj`. Both are in §2.
5. **Unresolved `Depositor` / `ContactPerson`** become `{P}/agent/{name}` (`rico:Agent`)
   rather than `{P}/person/…`, since they may be organisations. Unresolved contributor and
   participant names still become `rico:Person` nodes.
6. **Labels of unmapped Lameta fields** are humanized without guessing apostrophes
   (`Fathers language`, not `Father's language`).
7. **Test names.** The plan's `…IT` classes are ordinary `…Test` classes, so Surefire runs
   them without a Failsafe setup. The repository round-trip lives in
   `Lameta2RdfConverterTest.loadsIntoRepositoryProjectGraph`.
8. **Shaded jar.** `lameta2rdf-<version>-cli.jar` (about 12 MB). The `ServicesResourceTransformer`
   merges the 13 Rio writer factories and the converter provider registration; both were
   checked in the built jar.

