package io.github.sylvainloiseau.lameta2rico.lameta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.sylvainloiseau.lameta2rico.lameta.model.DocumentFile;
import io.github.sylvainloiseau.lameta2rico.lameta.model.Folder;
import io.github.sylvainloiseau.lameta2rico.lameta.model.LametaProject;
import io.github.sylvainloiseau.lameta2rico.lameta.xml.LametaXmlReader;
import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;
import fr.cnrs.lacito.ricimporterplugin.RdfConversionException;

/**
 * Walks a Lameta project directory and reads its metadata files.
 *
 * <p>Compared with the Python {@code Lameta2RdfParser}:
 * <ul>
 *   <li>if {@code <dir>.sprj}, {@code <dir>.session} or {@code <dir>.person} is missing, the only
 *       file with that suffix in the folder is used instead (with a warning);</li>
 *   <li>every regular, non-hidden file is a document, whether or not it has a {@code .meta}
 *       sidecar; {@code .meta} files and the folder's own description file are not documents;</li>
 *   <li>subdirectories inside session/person folders are skipped with a warning instead of
 *       aborting the conversion;</li>
 *   <li>entries are sorted by name, so the output does not depend on the file system order.</li>
 * </ul>
 */
public final class LametaProjectReader {

    private static final Logger LOG = LoggerFactory.getLogger(LametaProjectReader.class);

    private LametaProjectReader() {
    }

    /** Checks that {@code root} looks like a Lameta project, and returns its {@code .sprj} file. */
    public static Path findProjectFile(Path root) {
        if (!Files.isDirectory(root)) {
            throw new RdfConversionException("Lameta project path must be an existing directory: " + root);
        }
        return findDescriptionFile(root, LametaLayout.PROJECT_SUFFIX, false)
                .orElseThrow(() -> new RdfConversionException("No " + LametaLayout.PROJECT_SUFFIX
                        + " project file in " + root));
    }

    public static LametaProject read(Path root) {
        Path projectRoot = root.toAbsolutePath().normalize();
        findProjectFile(projectRoot);
        Path projectFile = findDescriptionFile(projectRoot, LametaLayout.PROJECT_SUFFIX, true).orElseThrow();
        XmlElement metadata = LametaXmlReader.read(projectFile);
        return new LametaProject(projectRoot, projectRoot.getFileName().toString(), metadata,
                readFolders(projectRoot, LametaLayout.PEOPLE_DIR, LametaLayout.PERSON_SUFFIX),
                readFolders(projectRoot, LametaLayout.SESSIONS_DIR, LametaLayout.SESSION_SUFFIX),
                readDocumentFolder(projectRoot, LametaLayout.DESCRIPTION_DOCUMENTS_DIR),
                readDocumentFolder(projectRoot, LametaLayout.OTHER_DOCUMENTS_DIR));
    }

    private static List<Folder> readFolders(Path projectRoot, String dirName, String suffix) {
        Path parent = projectRoot.resolve(dirName);
        List<Folder> folders = new ArrayList<>();
        if (!Files.isDirectory(parent)) {
            LOG.warn("No {} directory in {}", dirName, projectRoot);
            return folders;
        }
        for (Path entry : list(parent)) {
            if (isHidden(entry)) {
                continue;
            }
            if (!Files.isDirectory(entry)) {
                LOG.warn("Skipping {}: only folders are expected in {}", entry.getFileName(), parent);
                continue;
            }
            Optional<Path> descriptionFile = findDescriptionFile(entry, suffix, true);
            XmlElement metadata = descriptionFile.flatMap(LametaProjectReader::readMetadata).orElseGet(() -> {
                if (descriptionFile.isEmpty()) {
                    LOG.warn("No {} file in {}", suffix, entry);
                }
                return XmlElement.empty("");
            });
            folders.add(new Folder(entry.getFileName().toString(), entry, metadata,
                    readDocuments(projectRoot, entry, descriptionFile)));
        }
        return folders;
    }

    private static Optional<List<DocumentFile>> readDocumentFolder(Path projectRoot, String dirName) {
        Path dir = projectRoot.resolve(dirName);
        return Files.isDirectory(dir) ? Optional.of(readDocuments(projectRoot, dir, Optional.empty())) : Optional.empty();
    }

    private static List<DocumentFile> readDocuments(Path projectRoot, Path dir, Optional<Path> descriptionFile) {
        List<DocumentFile> documents = new ArrayList<>();
        for (Path f : list(dir)) {
            String name = f.getFileName().toString();
            if (isHidden(f) || name.endsWith(LametaLayout.META_SUFFIX)
                    || descriptionFile.map(d -> d.equals(f)).orElse(false)) {
                continue;
            }
            if (Files.isDirectory(f)) {
                LOG.warn("Skipping subdirectory {}", f);
                continue;
            }
            if (!Files.isRegularFile(f)) {
                continue;
            }
            Path metaFile = f.resolveSibling(name + LametaLayout.META_SUFFIX);
            Optional<XmlElement> meta = Files.isRegularFile(metaFile)
                    ? readMetadata(metaFile)
                    : Optional.empty();
            String relative = projectRoot.relativize(f).toString().replace(f.getFileSystem().getSeparator(), "/");
            documents.add(new DocumentFile(f, relative, meta));
        }
        for (Path f : list(dir)) {
            String name = f.getFileName().toString();
            if (name.endsWith(LametaLayout.META_SUFFIX) && !isHidden(f)
                    && !Files.exists(f.resolveSibling(name.substring(0, name.length() - LametaLayout.META_SUFFIX.length())))) {
                LOG.warn("Orphan metadata file (no matching document): {}", f);
            }
        }
        return documents;
    }

    /** {@code <dir>/<dirname><suffix>}, else the only {@code *<suffix>} file of {@code dir}. */
    private static Optional<Path> findDescriptionFile(Path dir, String suffix, boolean warn) {
        Path expected = dir.resolve(dir.getFileName() + suffix);
        if (Files.isRegularFile(expected)) {
            return Optional.of(expected);
        }
        List<Path> candidates = list(dir).stream()
                .filter(p -> !isHidden(p) && Files.isRegularFile(p) && p.getFileName().toString().endsWith(suffix))
                .toList();
        if (candidates.size() == 1) {
            if (warn) {
                LOG.warn("{} not found; using {}", expected.getFileName(), candidates.get(0).getFileName());
            }
            return Optional.of(candidates.get(0));
        }
        if (candidates.size() > 1 && warn) {
            LOG.warn("{} not found and several {} files in {}; none used", expected.getFileName(), suffix, dir);
        }
        return Optional.empty();
    }

    /** Reads a metadata file; an empty file is reported and treated as absent. */
    private static Optional<XmlElement> readMetadata(Path file) {
        Optional<XmlElement> content = LametaXmlReader.readIfNotBlank(file);
        if (content.isEmpty()) {
            LOG.warn("Empty metadata file ignored: {}", file);
        }
        return content;
    }

    private static boolean isHidden(Path p) {
        return p.getFileName().toString().startsWith(".");
    }

    private static List<Path> list(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.sorted().toList();
        } catch (IOException e) {
            throw new RdfConversionException("Cannot list " + dir, e);
        }
    }
}
