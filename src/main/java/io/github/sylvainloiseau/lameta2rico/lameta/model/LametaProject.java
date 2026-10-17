package io.github.sylvainloiseau.lameta2rico.lameta.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.github.sylvainloiseau.lameta2rico.lameta.xml.XmlElement;

/**
 * In-memory view of a Lameta project directory. Built by
 * {@link io.github.sylvainloiseau.lameta2rico.lameta.LametaProjectReader}.
 *
 * @param root                 project directory
 * @param name                 project directory name
 * @param metadata             content of the {@code .sprj} file
 * @param people               folders of {@code People/}, sorted by name
 * @param sessions             folders of {@code Sessions/}, sorted by name
 * @param descriptionDocuments files of {@code DescriptionDocuments/}, if the folder exists
 * @param otherDocuments       files of {@code OtherDocuments/}, if the folder exists
 */
public record LametaProject(Path root, String name, XmlElement metadata, List<Folder> people, List<Folder> sessions,
        Optional<List<DocumentFile>> descriptionDocuments, Optional<List<DocumentFile>> otherDocuments) {

    public LametaProject {
        people = List.copyOf(people);
        sessions = List.copyOf(sessions);
    }
}
