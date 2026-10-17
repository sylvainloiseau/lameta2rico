package io.github.sylvainloiseau.lameta2rico.lameta.model;

import java.nio.file.Path;
import java.util.List;

import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;

/**
 * A session or person folder: its description file and the documents it contains.
 *
 * @param dirName  folder name (used in IRIs and for file-type detection)
 * @param dir      absolute path of the folder
 * @param metadata content of the {@code .session} / {@code .person} file (an empty element
 *                 if the folder has none)
 * @param files    documents of the folder, sorted by name
 */
public record Folder(String dirName, Path dir, XmlElement metadata, List<DocumentFile> files) {

    public Folder {
        files = List.copyOf(files);
    }
}
