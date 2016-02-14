package wsa.web.html;

import org.w3c.dom.Document;

/**
 * Una factory per Parsed
 */
public class ParsedFactory {
    /** Ritorna un Parsed a partire dalla struttura di un {@link org.w3c.dom.Document} */
    public static Parsed getFromDocument(Document doc) {
        return new DocumentParsed(doc);
    }
}
