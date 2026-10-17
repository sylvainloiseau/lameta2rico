package io.github.sylvainloiseau.lameta2rico.lameta.model;

import java.nio.file.Path;
import java.util.Optional;

import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;

/**
 * A document of a Lameta project (media file, consent form, description document...) with its
 * optional {@code .meta} sidecar.
 *
 * @param file         absolute path of the document
 * @param relativePath path relative to the project root, with {@code /} separators
 * @param meta         content of {@code <file>.meta}, if present
 */
public record DocumentFile(Path file, String relativePath, Optional<XmlElement> meta) {

    public String fileName() {
        return file.getFileName().toString();
    }
}
