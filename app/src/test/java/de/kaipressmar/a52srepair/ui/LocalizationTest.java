package de.kaipressmar.a52srepair.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import de.kaipressmar.a52srepair.R;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** English (default) and German must stay complete and structurally identical. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LocalizationTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%\\d+\\$[sd]");

    /** Every translation (values-de and any future values-xx) must be complete and consistent. */
    @Test public void everyTranslationCoversAllKeysWithSamePlaceholders() throws Exception {
        Map<String, String> english = translatable(new File("src/main/res/values/strings.xml"));
        File[] translations = new File("src/main/res").listFiles(
                dir -> dir.getName().matches("values-[a-z]{2}(-r[A-Z]{2})?")
                        && new File(dir, "strings.xml").exists());
        assertTrue("at least the German translation exists",
                translations != null && translations.length >= 1);

        for (File dir : translations) {
            Map<String, String> translated = translatable(new File(dir, "strings.xml"));
            assertEquals("keys differ between values/ and " + dir.getName(),
                    english.keySet(), translated.keySet());
            for (Map.Entry<String, String> entry : english.entrySet()) {
                assertEquals(dir.getName() + ": placeholders of " + entry.getKey(),
                        placeholders(entry.getValue()), placeholders(translated.get(entry.getKey())));
            }
        }
    }

    @Test @Config(qualifiers = "en") public void englishIsTheDefault() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("History", context.getString(R.string.nav_history));
        assertEquals("2 repair steps",
                context.getResources().getQuantityString(R.plurals.history_actions, 2, 2));
    }

    @Test @Config(qualifiers = "de") public void germanIsComplete() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("Verlauf", context.getString(R.string.nav_history));
        assertEquals("1 Reparaturschritt",
                context.getResources().getQuantityString(R.plurals.history_actions, 1, 1));
        assertEquals(3, context.getResources().getStringArray(R.array.preventive_mode_entries).length);
    }

    @Test @Config(qualifiers = "fr") public void unsupportedLanguagesFallBackToEnglish() {
        Context context = ApplicationProvider.getApplicationContext();
        assertEquals("Settings", context.getString(R.string.nav_settings));
    }

    /** name → text for every translatable string, plural item and array item. */
    private static Map<String, String> translatable(File file) throws Exception {
        assertTrue(file + " missing", file.exists());
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getDocumentElement();
        Map<String, String> out = new TreeMap<>();
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
            Element element = (Element) nodes.item(i);
            if ("false".equals(element.getAttribute("translatable"))) continue;
            String name = element.getAttribute("name");
            switch (element.getTagName()) {
                case "string":
                    out.put(name, element.getTextContent());
                    break;
                case "plurals":
                case "string-array":
                    NodeList items = element.getElementsByTagName("item");
                    for (int j = 0; j < items.getLength(); j++) {
                        Element item = (Element) items.item(j);
                        String key = element.getTagName().equals("plurals")
                                ? name + "#" + item.getAttribute("quantity") : name + "[" + j + "]";
                        out.put(key, item.getTextContent());
                    }
                    break;
                default:
                    break;
            }
        }
        return out;
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) found.add(matcher.group());
        found.sort(String::compareTo);
        return found;
    }
}
