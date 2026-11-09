package de.zeiterfassung;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import de.zeiterfassung.service.ExportService;

class ExportServiceTest {

    private static String cell(String value) throws Exception {
        Method m = ExportService.class.getDeclaredMethod("csvCell", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, value);
    }

    @Test
    void nullBecomesEmpty() throws Exception {
        assertThat(cell(null)).isEmpty();
    }

    @Test
    void quotesAreDoubledAndTextIsWrapped() throws Exception {
        assertThat(cell("Er sagte \"hallo\"; ok")).isEqualTo("\"Er sagte \"\"hallo\"\"; ok\"");
    }

    @Test
    void formulaPrefixesAreNeutralised() throws Exception {
        assertThat(cell("=HYPERLINK(\"http://x\")")).startsWith("\"'=");
        assertThat(cell("+49 123")).startsWith("\"'+");
        assertThat(cell("-1")).startsWith("\"'-");
        assertThat(cell("@SUM(A1)")).startsWith("\"'@");
    }

    @Test
    void newlinesAreFlattened() throws Exception {
        assertThat(cell("a\nb")).isEqualTo("\"a b\"");
    }
}
