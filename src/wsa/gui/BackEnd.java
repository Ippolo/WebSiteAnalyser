package wsa.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import wsa.web.CrawlerResult;
import wsa.web.SiteCrawler;
import wsa.web.WebFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

/**
 * Una Classe che rappresenta l'esplorazione di un dominio. Gli oggetti di questa classe non contengono alcuna
 * componente grafica, ma hanno dei metodi che permettono di interfacciarsi con l'esplorazione e ricavare informazioni
 * da poter essere visualizzate in un'interfaccia. fornisce anche un metodo per accedere alla finestra principale
 * del WSA che gestisce tutte le esplorazioni dei domini in corso.
 */
public class BackEnd {
    /* Instance Fileds */
    private short DEBUG = 0;

    private final SiteCrawler siteCrawler;
    private final URI domain;
    private final MainFrame frame;

    private final ObservableList<CrawlerResult> resultObservableList = FXCollections.observableArrayList();

    private final Service<Void> service = new Service<Void>() {
        @Override protected Task<Void> createTask() {
            return new Task<Void>() {
                @Override protected Void call() throws Exception {
                    siteCrawler.start();
                    boolean mustStop = false;
                    while (!siteCrawler.getToLoad().isEmpty() && !mustStop) {
                        try {
                            CrawlerResult crawlerResult = siteCrawler.get().get(); // può lanciare NoSuchElementException
                            while (crawlerResult.uri != null) {
                                resultObservableList.add(crawlerResult);
                                crawlerResult = siteCrawler.get().get();
                        }
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            if ( isCancelled() ) {
                                if (DEBUG > 0)
                                    System.out.println("Worker Cancellato");
                                mustStop = true;
                            }
                        } catch (NoSuchElementException e) {
                            e.printStackTrace();
                            if (!siteCrawler.isRunning()) {
                                System.out.println("Il SiteCrawler usato da questo BackEnd non è in running!!!");
                            }
                        }
                        if ( isCancelled() ) {
                            if (DEBUG > 0) {
                                System.out.println("Worker Cancellato");
                            }
                            mustStop = true;
                        }
                    }
                    // Fa il get di eventuali risultati rimasti prima di sospendere il SiteCrawler
                    CrawlerResult crawlerResult = siteCrawler.get().get();
                    while (crawlerResult.uri != null) {
                        resultObservableList.add(crawlerResult);
                        crawlerResult = siteCrawler.get().get();
                    }
                    siteCrawler.suspend();
                    return null;
                }
            };
        }
    };

    /* Constructors */
    /** Metodo costruttore. Permette di iniziare l'esplorazione di un nuovo dominio o di ripristinarne una
     * precedentemente salvata in una directory
     * @param dom il dominio dell'esplorazione, null se si vuole ripristinare una vecchia esplorazione
     * @param dir la cartella dove archiviare l'esplorazione, null se non si vuole archiviare
     * @param owner la finestra principale del WSA*/
    public BackEnd(URI dom, Path dir, MainFrame owner) throws IOException {
        siteCrawler = WebFactory.getSiteCrawler(dom, dir);
        frame = owner;
        if (dom != null) {
            domain = dom;
        } else {
            try (  ObjectInputStream is = new ObjectInputStream( Files.newInputStream(dir.resolve("domain")) )  )
            {
                domain =  (URI)is.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("si sta cercando di leggere da un file inesistente " +
                                                   "o che non contiene un oggetto valido");
            }
        }
        // Riempie la lista dei risultati già esplorati
        Stream<URI> loadedStream = siteCrawler.getLoaded().stream();
        Stream<URI> errorsStream = siteCrawler.getErrors().stream();
        Stream.concat(loadedStream, errorsStream).forEach(  (u) -> resultObservableList.add( siteCrawler.get(u) )  );
    }

    /* Instance Methods */
    /** Quando questo metodo è invocato fa partire in background l'esplorazione del dominio e ritorna immediatamente */
    public void startCrawling() {
        if (!siteCrawler.getToLoad().isEmpty()){
            Worker.State state = service.getState();
            if( state == Worker.State.SUCCEEDED
                || state == Worker.State.CANCELLED
                || state == Worker.State.FAILED    )
            {
                service.reset();
            }
            service.start();
        }
    }

    /** Ritorna una ObservableList che si aggiorna costantemente da sola e contiene tutte le pagine che sono state
     * esplorate
     * @return una ObservableList di tutti i CrawlerResult esplorati*/
    public ObservableList<CrawlerResult> resultObservableList() {
        //return FXCollections.unmodifiableObservableList(resultObservableList);
        return resultObservableList;
    }

    /** Cancella definitivamente l'esplorazione, dopo non potrà più essere ripresa. Se era attiva l'archiviazione
     * potrà comunque essere ripristinata dalla cartella dove è stata archiviata */
    public void cancel() {
        service.cancel();
        resultObservableList.clear();
        siteCrawler.cancel();
    }

    /** Ritorna il dominio dell'esplorazione
     * @return il dominio dell'esplorazione */
    public URI getDomain(){
        return domain;
    }

    /** Ritorna la finestra principale del WSA, da cui è possibile accedere alle altre esplorazioni in corso
     * @return la finestra principale */
    public MainFrame getFrame() {
        return frame;
    }

    /** Ritorna il SiteCrawler che sta eseguendo l'esplorazione del dominio
     * @return il SiteCrawler che sta eseguendo l'esplorazione del dominio */
    public SiteCrawler getCrawler(){
        return siteCrawler;
    }

    /** Ritorna il Worker che esegue il background l'esplorazione del dominio
     * @return  il Worker che esegue il background l'esplorazione del dominio*/
    public Worker<Void> getWorker(){
        return service;
    }

}