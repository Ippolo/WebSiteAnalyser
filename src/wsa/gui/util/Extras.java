package wsa.gui.util;

import javafx.concurrent.Task;
import wsa.web.Loader;
import wsa.web.WebFactory;
import wsa.web.html.Parsed;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/** Una classe che fornisce strumenti per scaricare informazioni da un URI che non possono essere prese direttamente da
 * un {@link wsa.web.CrawlerResult}, ma che potrebbe essere utile visualizzare. Una volta che queste
 * informazioni sono scaricate vengono salvate in un oggetto {@link Extras.ExtraInfo} e sono accessibili
 * tramite il metodo {@link Extras#getExtraInfoOrNull(URI)} senza bisogno di doverle riscaricare. */
public class Extras {
    /* Nested Classes */
    /** Rappresenta alcune informazioni riguardo un link che non possono essere ricavate da un
     * {@link wsa.web.CrawlerResult}, ma che un BackEnd potrebbe proporre di visualizzare
     * riscaricando la pagina. */
    public static class ExtraInfo {
        public final int numberOfNodes;
        public final int numberOfImages;
        public ExtraInfo(int nodes, int images){
            numberOfNodes = nodes;
            numberOfImages = images;
        }
    }

    /* Instance Fields */
    private final Map<URI, ExtraInfo> map = new HashMap<>();

    /* Instance Methods */
    /** Ritorna un Task che scarica uri e ne ricava un oggetto ExtraInfo
     * @param uri l'uri da scaricare
     * @return un Task che ricava un oggetto ExtraInfo da uri*/
    public Task<ExtraInfo> newExtraInfoTask(URI uri){
        return new Task<ExtraInfo>() {
            @Override
            protected ExtraInfo call() throws Exception {
                try {
                    URL url = uri.toURL();
                    Loader loader = WebFactory.getLoader();
                    Parsed parsed = loader.load(url).parsed;
                    if (parsed != null){
                        int[] numberOfNodes = {0};
                        parsed.visit((n) -> numberOfNodes[0]++);
                        int[] numberOfImages = {0};
                        parsed.visit((n) -> {
                            if (n.tag != null && n.tag.equals("IMG"))
                                numberOfImages[0]++;
                        });
                        ExtraInfo extraInfo = new ExtraInfo(numberOfNodes[0], numberOfImages[0]);
                        map.put(uri, extraInfo);
                        return extraInfo;
                    } else {
                        return null;
                    }
                } catch (MalformedURLException e) {
                    System.out.println("MalformedURLException in wsa.gui.util.TaskFactory#newExtraInfoTask. codice 1");
                    return null;
                }
            }
        };
    }
    /** Ritorna l'oggetto ExtraInfo di un uri se è già stato scaricato da un Task, altrimenti null */
    public ExtraInfo getExtraInfoOrNull(URI uri) {
        return map.get(uri);
    }
}
