package io.github.sylvainloiseau.lameta2rico.lameta.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.cnrs.lacito.ricimporterplugin.RdfConversionException;

/** Includes the cases of the Python TestDictionary2Triple / TestSelectMappingKind tests. */
class LametaXmlReaderTest {

    @Test
    void textElement() {
        XmlElement root = LametaXmlReader.parse("<xml><f>text1</f></xml>");
        assertThat(root.name()).isEqualTo("xml");
        assertThat(root.childText("f")).contains("text1");
    }

    @Test
    void attributesAreKeptExceptType() {
        XmlElement f = LametaXmlReader.parse("<xml><f attr='val' type='string'>text1</f></xml>").child("f").orElseThrow();
        assertThat(f.attributes()).containsOnlyKeys("attr").containsEntry("attr", "val");
        assertThat(f.text()).isEqualTo("text1");
    }

    @Test
    void nestingAndRepeatedElements() {
        XmlElement root = LametaXmlReader.parse("""
                <xml>
                  <super><sub1>val1</sub1><sub2>val2</sub2></super>
                  <x>a</x><x>b</x>
                </xml>""");
        XmlElement sup = root.child("super").orElseThrow();
        assertThat(sup.childText("sub1")).contains("val1");
        assertThat(sup.childText("sub2")).contains("val2");
        assertThat(root.children("x")).extracting(XmlElement::text).containsExactly("a", "b");
        assertThat(sup.text()).isEmpty();
    }

    @Test
    void emptyElementsHaveNoText() {
        XmlElement root = LametaXmlReader.parse("<Project><contributions type=\"xml\"></contributions><a/></Project>");
        assertThat(root.child("contributions")).hasValueSatisfying(c -> {
            assertThat(c.hasText()).isFalse();
            assertThat(c.children()).isEmpty();
        });
        assertThat(root.childText("a")).isEmpty();
        assertThat(root.childText("missing")).isEmpty();
    }

    @Test
    void leadingWhitespaceBeforeDeclarationIsAccepted() throws URISyntaxException {
        // sample/data/test.* files start with spaces before <?xml ...?>
        for (String name : new String[] {"test.session", "test.person", "test.meta"}) {
            Path p = Path.of(getClass().getResource("/xml/" + name).toURI());
            assertThat(LametaXmlReader.read(p).children()).as(name).isNotEmpty();
        }
    }

    @Test
    void nonAsciiContent() {
        assertThat(LametaXmlReader.parse("<?xml version=\"1.0\" encoding=\"utf-8\"?><P><d>Déposant</d></P>").childText("d"))
                .contains("Déposant");
    }

    @Test
    void doctypeIsRefused() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><r>&e;</r>";
        assertThatThrownBy(() -> LametaXmlReader.parse(xxe)).isInstanceOf(RdfConversionException.class);
    }

    @Test
    void emptyFileIsAbsentButReadFails(@TempDir Path tmp) throws IOException {
        Path empty = Files.writeString(tmp.resolve("x.meta"), "  \n");
        assertThat(LametaXmlReader.readIfNotBlank(empty)).isEmpty();
        assertThatThrownBy(() -> LametaXmlReader.read(empty)).isInstanceOf(RdfConversionException.class);
    }

    @Test
    void malformedFileNamesTheFile(@TempDir Path tmp) throws IOException {
        Path bad = Files.writeString(tmp.resolve("bad.session"), "<Session><id>x</Session>");
        assertThatThrownBy(() -> LametaXmlReader.readIfNotBlank(bad))
                .isInstanceOf(RdfConversionException.class)
                .hasMessageContaining("bad.session");
    }
}
