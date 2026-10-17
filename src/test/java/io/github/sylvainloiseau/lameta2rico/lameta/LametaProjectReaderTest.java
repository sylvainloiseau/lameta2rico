package io.github.sylvainloiseau.lameta2rico.lameta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.sylvainloiseau.lameta2rico.TestProjects;
import io.github.sylvainloiseau.lameta2rico.lameta.model.DocumentFile;
import io.github.sylvainloiseau.lameta2rico.lameta.model.Folder;
import io.github.sylvainloiseau.lameta2rico.lameta.model.LametaProject;
import fr.cnrs.lacito.ricimporterplugin.RdfConversionException;

class LametaProjectReaderTest {

    @Test
    void readsTestProject() {
        LametaProject p = LametaProjectReader.read(TestProjects.project("Test"));
        assertThat(p.name()).isEqualTo("Test");
        assertThat(p.metadata().childText("Title")).contains("Test");
        assertThat(p.people()).extracting(Folder::dirName).containsExactly("John Doe", "Joshua Kelekele", "Wilema Doe");
        assertThat(p.sessions()).extracting(Folder::dirName).containsExactly("Session1_ID", "Session2_ID", "Session4", "SessionID3");

        Folder session1 = p.sessions().get(0);
        assertThat(session1.metadata().childText("id")).contains("Session1_ID");
        assertThat(session1.files()).singleElement().satisfies(f -> {
            assertThat(f.fileName()).isEqualTo("131123_03_Source.WAV");
            assertThat(f.relativePath()).isEqualTo("Sessions/Session1_ID/131123_03_Source.WAV");
            assertThat(f.meta()).isPresent();
        });
    }

    @Test
    void filesWithoutMetaAreDocumentsAndMetaFilesAreNot() {
        LametaProject p = LametaProjectReader.read(TestProjects.project("Test"));
        assertThat(p.otherDocuments()).hasValueSatisfying(files -> assertThat(files)
                .singleElement().satisfies(f -> {
                    assertThat(f.fileName()).isEqualTo("idees.txt");
                    assertThat(f.meta()).isEmpty();
                }));
        assertThat(p.descriptionDocuments()).hasValueSatisfying(files -> assertThat(files)
                .extracting(DocumentFile::fileName).containsExactly("idees.txt"));
    }

    @Test
    void fallsBackToTheOnlyDescriptionFile() {
        // Test2/Test.sprj and Test2/Sessions/Session3/SessionID3.session do not match their folder names
        LametaProject p = LametaProjectReader.read(TestProjects.project("Test2"));
        assertThat(p.metadata().childText("Title")).contains("Test");
        Folder session3 = p.sessions().stream().filter(s -> s.dirName().equals("Session3")).findFirst().orElseThrow();
        assertThat(session3.metadata().childText("id")).contains("SessionID3");
        assertThat(session3.files()).extracting(DocumentFile::fileName).containsExactly("131111_03_Source.WAV");
    }

    @Test
    void emptyMetaFileIsTreatedAsAbsent() {
        Folder session2 = LametaProjectReader.read(TestProjects.project("Test2")).sessions().get(1);
        assertThat(session2.dirName()).isEqualTo("Session2");
        assertThat(session2.files()).singleElement().satisfies(f -> assertThat(f.meta()).isEmpty());
    }

    @Test
    void hiddenFilesAndSubdirectoriesAreSkipped(@TempDir Path tmp) throws IOException {
        Path project = TestProjects.copy("Test", tmp);
        Path session = project.resolve("Sessions/SessionID3");
        Files.writeString(session.resolve(".SessionID3.session.swp"), "binary junk");
        Files.createDirectories(session.resolve("nested"));
        Files.writeString(session.resolve("notes.txt"), "x");
        Folder folder = LametaProjectReader.read(project).sessions().get(3);
        assertThat(folder.files()).extracting(DocumentFile::fileName).containsExactly("notes.txt");
    }

    @Test
    void rejectsNonProjects(@TempDir Path tmp) {
        assertThatThrownBy(() -> LametaProjectReader.findProjectFile(tmp.resolve("missing")))
                .isInstanceOf(RdfConversionException.class).hasMessageContaining("existing directory");
        assertThatThrownBy(() -> LametaProjectReader.findProjectFile(tmp))
                .isInstanceOf(RdfConversionException.class).hasMessageContaining(".sprj");
    }

    @Test
    void malformedSessionFileFailsTheConversion(@TempDir Path tmp) throws IOException {
        Path project = TestProjects.copy("Test", tmp);
        Files.writeString(project.resolve("Sessions/Session4/Session4.session"), "<Session><id>");
        assertThatThrownBy(() -> LametaProjectReader.read(project))
                .isInstanceOf(RdfConversionException.class).hasMessageContaining("Session4.session");
    }
}
