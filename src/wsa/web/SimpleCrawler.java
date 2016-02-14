package wsa.web;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/**
 * Un Crawler che Per scaricare le pagine usa esclusivamente {@link wsa.web.AsyncLoader} fornito da {@link WebFactory#getAsyncLoader()}.
 */
class SimpleCrawler implements Crawler{
    /* Nested Classes */
    /** Una classe per controllare se un url è scaricabile in modo asincrono. Simile all'interfaccia AsyncLoader ma
     * non scarica la pagina, fa solo il check usando il metodo #check di un Loader.
     */
    private class AsyncChecker{
        Future<LoadResult> submit(URL url) {
            if (this.isShutdown())
                throw new IllegalStateException("Il loader è chiuso");
            return executor.submit( () -> {
                Exception exc = loader.check(url);
                return new LoadResult(url, null, exc);
            });
        }
        /** Chiude il loader e rilascia tutte le risorse. Dopo di ciò non può più
         * essere usato. */
        void shutdown() {
            executor.shutdown();
            executor = null;
        }
        /** Ritorna true se è chiuso.
         * @return true se è chiuso */
        boolean isShutdown(){
            return (executor == null);
        }

        private Loader loader = WebFactory.getLoader();

        private ExecutorService executor = Executors.newFixedThreadPool(65, (runnable) -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setDaemon(true);
            return thread;
        });
    }

    /* Instance Fields */
    private final Queue<URI> downloadQueue = new ConcurrentLinkedQueue<>();
    private final Set<URI> toLoadSet = Collections.synchronizedSet(new HashSet<>());
    private final Set<URI> loadedSet = Collections.synchronizedSet(new HashSet<>());
    private final Set<URI> errorSet = Collections.synchronizedSet(new HashSet<>());
    private final Predicate<URI> pageLink;

    private AsyncLoader asyncLoader;
    private AsyncChecker asyncChecker;
    private final Queue<CrawlerResult> resultQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Future<LoadResult>> futureQueue = new ConcurrentLinkedQueue<>();

    private Thread runningThread = null;

    private short DEBUG = 0;

    /* Constructors */
    /** Costruttore.
    * Si assume che gli URI passati  siano tutti assoluti.
    * @param loaded  insieme URI scaricati
    * @param toLoad  insieme URI da scaricare
    * @param errs  insieme URI con errori
    * @param pageLink  determina gli URI per i quali i link contenuti nelle
    *                  relative pagine sono usati per continuare il crawling*/
    SimpleCrawler( Collection<URI> loaded,
                   Collection<URI> toLoad,
                   Collection<URI> errs,
                   Predicate<URI> pageLink
    ) {
        if (loaded != null) {
            loadedSet.addAll(loaded);
        }
        if (errs != null) {
            errorSet.addAll(errs);
        }
        if (toLoad != null) {
            toLoad.stream().forEach(this::add);
        }
        if (pageLink != null) {
            this.pageLink = pageLink;
        } else  {
            this.pageLink = ( (uri) -> true );
        }
    }

    /* Instance Methods */
    /** Aggiunge un URI all'insieme degli URI da scaricare. Se però è presente
     * tra quelli già scaricati, quelli ancora da scaricare o quelli che sono
     * andati in errore, l'aggiunta non ha nessun effetto. Se invece è un nuovo
     * URI, è aggiunto all'insieme di quelli da scaricare.
     * Si assume che gli URI passati  siano tutti assoluti.
     * @throws IllegalStateException se il Crawler è cancellato
     * @param uri  un URI che si vuole scaricare */
    @Override
    public void add(URI uri) {
        if (this.isCancelled()) {
            throw new IllegalStateException("il Crawler è cancellato");
        } else if ( !uri.isAbsolute() ) {
            System.out.println("ERRORE: si sta cercando di aggiungere al crawler un uri non assoluto. " +
                                                                             "Questa azione sarà ingorata.");
        } else if( !loadedSet.contains(uri)
                   && !errorSet.contains(uri)
                   && !toLoadSet.contains(uri) ) {
            downloadQueue.add(uri);
            toLoadSet.add(uri);
        }
    }

    /** Inizia l'esecuzione del Crawler se non è già in esecuzione e ci sono URI
     * da scaricare, altrimenti l'invocazione è ignorata. Quando è in esecuzione
     * il metodo isRunning ritorna true.
     * @throws IllegalStateException se il Crawler è cancellato */
    @Override
    public void start() {
        if (this.isCancelled()) throw new IllegalStateException("Il Crawler è cancellato");
        if ( ( runningThread == null || !runningThread.isAlive() )
                && ( !downloadQueue.isEmpty() || !toLoadSet.isEmpty() ) )
        {
            runningThread = new Thread( () -> {
                asyncLoader = WebFactory.getAsyncLoader();
                asyncChecker = new AsyncChecker();
                Set<URI> currentlyDownloadingSet = new HashSet<>();
                boolean isInterrupted = false;
                while (!isInterrupted) {
                    //Mette il prossimo uri a scaricare o lo aggiunge
                    //agli errori se non è possibile convertirlo in url
                    URI uri = downloadQueue.poll();
                    if (uri != null){// se c'è un uri che deve essere scaricato
                        currentlyDownloadingSet.add(uri);
                        try {
                            URL url = uri.toURL();
                            // se l'uri è da seguire lo elabora con asyncLoader, altrimenti con asyncChecker
                            boolean isToFollow = pageLink.test(uri);
                            Future<LoadResult> future;
                            if (isToFollow)
                                future = asyncLoader.submit(url);
                            else
                                future = asyncChecker.submit(url);
                            futureQueue.add(future);
                        }
                        catch (IllegalArgumentException | MalformedURLException exc){
                            //aggiunge l'url agli errori
                            currentlyDownloadingSet.remove(uri);
                            CrawlerResult crawlerResult = new CrawlerResult(uri, false, null, null, exc);
                            resultQueue.add(crawlerResult);
                            errorSet.add(uri);
                            toLoadSet.remove(uri);
                        }
                    }
                    //Controlla se il prossimo uri ha scaricato
                    //ed eventualmente ne elabora il risultato
                    Future<LoadResult> future = futureQueue.poll();
                    if ( future != null ) {
                        if ( future.isDone() ) {
                            try {
                                LoadResult lr = future.get();// can throw InterruptedException, ExecutionException.
                                uri = lr.url.toURI();// can throw URISyntaxException.
                                currentlyDownloadingSet.remove(uri);
                                if (lr.exc != null) {// se si è verificata un'eccezione durante il download
                                    boolean linkPage = pageLink.test(uri);
                                    List<URI> links = linkPage ? new ArrayList<>() : null;
                                    List<String> errRawLinks = linkPage ? new ArrayList<>() : null;
                                    CrawlerResult crawlerResult = new CrawlerResult(uri, linkPage, links, errRawLinks, lr.exc);
                                    resultQueue.add(crawlerResult);
                                    errorSet.add(uri);
                                    toLoadSet.remove(uri);
                                }else {// se il download è andato a buon fine
                                    boolean linkPage = false;
                                    List<URI> links = null;
                                    List<String> errRawLinks = null;
                                    if (pageLink.test(uri)) {// se la pagina di questo uri è usata per continuate il crawling
                                        linkPage = true;
                                        links = new ArrayList<>();
                                        errRawLinks = new ArrayList<>();
                                        for (String link : lr.parsed.getLinks()) {
                                            try {
                                                URI linkURI = new URI(link);//throws URISyntaxException, NullPointerException
                                                if (!linkURI.isAbsolute())
                                                    linkURI = uri.resolve(linkURI);
                                                try {
                                                    linkURI = linkURI.toURL().toURI();// elimina eventuali problemi di conversione uri-url-uri, throws MalformesURLException
                                                    this.add(linkURI);
                                                }catch (MalformedURLException e){
                                                    CrawlerResult cr = new CrawlerResult(linkURI, false, null, null, e);
                                                    resultQueue.add(cr);
                                                    errorSet.add(linkURI);
                                                    //errorSet.add(uri);
                                                }
                                                links.add(linkURI);
                                            }catch (URISyntaxException | NullPointerException e){
                                                errRawLinks.add(link);
                                            }
                                        }
                                    }
                                    CrawlerResult crawlerResult = new CrawlerResult(uri, linkPage, links, errRawLinks, null);
                                    resultQueue.add(crawlerResult);
                                    loadedSet.add(uri);
                                    toLoadSet.remove(uri);
                                }
                            }catch (ExecutionException | URISyntaxException | InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        else {
                            futureQueue.add(future);
                        }
                    }
                    if (uri == null && future == null) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            if (DEBUG > 0)
                                System.out.println("stopping crawler");
                            asyncLoader.shutdown();
                            asyncChecker.shutdown();
                            downloadQueue.addAll(currentlyDownloadingSet);
                            futureQueue.clear();
                            isInterrupted = true;
                        }
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        if (DEBUG > 0)
                            System.out.println("stopping crawler");
                        asyncLoader.shutdown();
                        asyncChecker.shutdown();
                        downloadQueue.addAll(currentlyDownloadingSet);
                        futureQueue.clear();
                        isInterrupted = true;
                    }
                }
            });
            runningThread.setDaemon(true);
            runningThread.start();
        }
    }

    /** Sospende l'esecuzione del Crawler. Se non è in esecuzione, ignora
     * l'invocazione. L'esecuzione può essere ripresa invocando start. Durante
     * la sospensione l'attività del Crawler dovrebbe essere ridotta al minimo
     * possibile (eventuali thread dovrebbero essere terminati).
     * @throws IllegalStateException se il Crawler è cancellato */
    @Override
    public void suspend() {
        if ( this.isCancelled() ) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        if (runningThread != null) {
            runningThread.interrupt();
        }
        runningThread = null;
    }

    /** Cancella il Crawler per sempre. Dopo questa invocazione il Crawler non
     * può più essere usato. Tutte le risorse devono essere rilasciate. */
    @Override
    public void cancel() {
        if (runningThread != null) runningThread.interrupt();
        if (asyncLoader != null) {
            asyncLoader.shutdown();
        }
        if (asyncChecker != null) {
            asyncChecker.shutdown();
        }
        asyncLoader = null;
        asyncChecker = null;
        runningThread = null;
    }

    /** Ritorna il risultato relativo al prossimo URI. Se il Crawler non è in
     * esecuzione, ritorna un Optional vuoto. Non è bloccante, ritorna
     * immediatamente anche se il prossimo risultato non è ancora pronto.
     * @throws IllegalStateException se il Crawler è cancellato
     * @return  il risultato relativo al prossimo URI scaricato */
    @Override
    public Optional<CrawlerResult> get() {
        if ( this.isCancelled() ) {
            throw new IllegalStateException("Il Crawler è cancellato");
        } else if ( !this.isRunning() ) {
            return Optional.empty();
        }
        CrawlerResult result = resultQueue.poll();
        if (result == null) {
            result = new CrawlerResult(null, false, null, null, null);
        }
        return Optional.of(result);
    }

    /** Ritorna una view dell'insieme di tutti gli URI scaricati, possibilmente vuoto.
     * @throws IllegalStateException se il Crawler è cancellato
     * @return l'insieme di tutti gli URI scaricati (mai null) */
    @Override
    public Set<URI> getLoaded() {
        if (this.isCancelled()) throw new IllegalStateException("Il Crawler è cancellato");
        //return new HashSet<>(loadedSet);
        return loadedSet;
    }

    /** Ritorna l'insieme, possibilmente vuoto, degli URI che devono essere
     * ancora scaricati. Quando l'esecuzione del crawler termina normalmente
     * l'insieme è vuoto.
     * ATTENZIONE: non è una view ma una copia!
     * @throws IllegalStateException se il Crawler è cancellato
     * @return l'insieme degli URI ancora da scaricare (mai null) */
    @Override
    public Set<URI> getToLoad() {
        if (this.isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        return toLoadSet;
        /*Set<URI> returnSet = Collections.synchronizedSet(new HashSet<>(downloadQueue));
        synchronized (toLoadSet) {
            Iterator i = toLoadSet.iterator(); // Must be in the synchronized block
            while (i.hasNext()) {
                returnSet.add((URI) i.next());
            }
        }
        return returnSet;*/
    }

    /** Ritorna una view dell'insieme, possibilmente vuoto, degli URI che non è stato
     * possibile scaricare a causa di errori.
     * @throws IllegalStateException se il crawler è cancellato
     * @return l'insieme degli URI che hanno prodotto errori (mai null) */
    @Override
    public Set<URI> getErrors() {
        if ( this.isCancelled() ) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        //return new HashSet<>(errorSet);
        return errorSet;
    }

    /** Ritorna true se il Crawler è in esecuzione.
     * @return true se il Crawler è in esecuzione */
    @Override
    public boolean isRunning() {
        return (this.runningThread != null && this.runningThread.isAlive());
    }

    /** Ritorna true se il Crawler è stato cancellato. In tal caso non può più
     * essere usato.
     * @return true se il Crawler è stato cancellato */
    @Override
    public boolean isCancelled() {
        return (resultQueue == null);
    }
}