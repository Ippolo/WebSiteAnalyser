package wsa.web;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import org.w3c.dom.Document;
import wsa.web.html.Parsed;
import wsa.web.html.ParsedFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Un Loader basato sul WebEngine di JavaFX.
 */
class JFXLoader implements Loader{
    /* Instance Fields */
    private WebEngine wEngine = null;

    private Parsed urlParsed = null;
    private boolean downloadFailed = false;

    /* Instance Methods */
    /** Ritorna il risultato del tentativo di scaricare la pagina specificata. È
     * bloccante, finchè l'operazione non è conclusa non ritorna.
     * @param url  l'URL di una pagina web
     * @return il risultato del tentativo di scaricare la pagina */
    @Override
    public LoadResult load(URL url) {
        LoadResult loadResult;
        Exception exc = null;
        urlParsed = null;       // sono variabili di classe perchè il ChangeListener del Worker della WebEngine deve
        downloadFailed = false; // conservare il riferimento a loro durante le diverse chiamate di questo metodo
        if (wEngine == null) {
            Platform.runLater(() -> {
                wEngine = new WebEngine();
                wEngine.getLoadWorker().stateProperty().addListener((o, ov, nv) -> {
                    if (nv == Worker.State.SUCCEEDED) {
                        Document doc = wEngine.getDocument();
                        if (doc != null)
                            urlParsed = ParsedFactory.getFromDocument(doc);
                        else
                            downloadFailed = true;
                    } else if (nv == Worker.State.FAILED || nv == Worker.State.CANCELLED)
                        downloadFailed = true;
                });
            });
        }
        Platform.runLater(() -> {
            wEngine.load(""); // Carica prima una pagina vuota per resettare la WebEngine in caso l'url da caricare coincida con l'ultimo caricato
            while (urlParsed == null && !downloadFailed) ;
            urlParsed = null;
            downloadFailed = false;
            wEngine.load(url.toString());
        });
        while (urlParsed == null && !downloadFailed)
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                downloadFailed = true;
            }

        if (downloadFailed) // se il download fallisce urlParsed[0] rimane null, altrimenti exc
            exc = new IOException("Il download è fallito");
        loadResult = new LoadResult(url, urlParsed, exc);
        return loadResult;
    }
    /** Ritorna null se l'URL è scaricabile senza errori, altrimenti ritorna
     * un'eccezione che riporta l'errore.
     * @param url  un URL
     * @return null se l'URL è scaricabile senza errori, altrimenti
     * l'eccezione */
    @Override
    public Exception check(URL url) {
        Exception exc = null;
        try {
            URLConnection urlConnection = url.openConnection();
            urlConnection.connect();
        }
        catch (Exception ex) { exc = ex; }
        return exc;
    }
}
