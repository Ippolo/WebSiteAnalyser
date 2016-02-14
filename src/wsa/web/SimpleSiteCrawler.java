package wsa.web;

import wsa.AppendableObjectOutputStream;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * Created by user on 01/06/15.
 */
class SimpleSiteCrawler implements SiteCrawler {
    /* Nested Classes */
    /** Classe interna che si occupa di gestire tutti i dati che un SiteCrawler deve fornire nell'implementazione della
     * sua interfaccia e il loro eventuale salvataggio su memoria secondaria per poter essere recuperati in seguito*/
    private class Data {
        final Set<URI> loadedSet = Collections.synchronizedSet(new HashSet<>());
        final Set<URI> errorSet = Collections.synchronizedSet(new HashSet<>());

        final Queue<CrawlerResult> resultQueue = new ConcurrentLinkedQueue<>();
        final Map<URI, CrawlerResult> uriMap = new ConcurrentHashMap<>();

        final URI domain;
        final Path directory;

        private final Queue<CrawlerResult> toBeStored;

        /** Inizializza le strutture dati opportune per la gestione dei dati.
         * Se dom è null e dir è diverso da null ripristina i dati dell'esplorazione
         * archiviata in dir
         * Si assume che dom e dir non siano mai entrambi null! */
        Data(URI dom, Path dir) throws IOException {
            if ( dir != null && !Files.isDirectory(dir) ) {
                throw new IllegalArgumentException("il percorso dato non è una directory");
            }
            directory = dir;
            toBeStored = directory == null ? null : new LinkedBlockingQueue<>();
            if (dom != null) {
                domain = dom;
                if (directory != null) {
                    // crea il file dove sarà memorizzato il dominio
                    Path domainPath = directory.resolve("domain");
                    ObjectOutputStream domainOOS = new ObjectOutputStream( Files.newOutputStream(domainPath) );
                    domainOOS.writeObject(domain);
                    domainOOS.close();
                    // crea il file dove mano a mano verranno archiviati i CrawlerResult
                    Path resultsPath = directory.resolve("results");
                    ObjectOutputStream resultsOOS = new ObjectOutputStream( Files.newOutputStream(resultsPath) );
                    resultsOOS.close();
                }

            } else /* dom == null && dir != null */ {
                try ( ObjectInputStream domainIS = new ObjectInputStream(
                                                        Files.newInputStream(directory.resolve("domain")) );
                      ObjectInputStream resultsIS = new ObjectInputStream(
                                                        Files.newInputStream( directory.resolve("results") ))
                ) {
                    // Ripristina il dominio
                    domain = (URI) domainIS.readObject();
                    // Ripristina i risultati già scaricati
                    boolean eof = false;
                    while (!eof) {
                        try {
                            URI uri = (URI) resultsIS.readObject();
                            boolean linkPage = resultsIS.readBoolean();
                            List<URI> links = (List<URI>) resultsIS.readObject();
                            List<String> errRawLinks = (List<String>) resultsIS.readObject();
                            Exception exc = (Exception) resultsIS.readObject();
                            CrawlerResult cr = new CrawlerResult(uri, linkPage, links, errRawLinks, exc);
                            uriMap.put(uri, cr);
                            if (cr.exc != null) {
                                errorSet.add(cr.uri);
                            } else {
                                loadedSet.add(cr.uri);
                            }
                        } catch (EOFException e) {
                            eof = true;
                        }
                    }
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("archivio non valido!");
                }

            }
        }

        void put(CrawlerResult cr) {
            uriMap.put(cr.uri, cr);
            resultQueue.add(cr);
            if (cr.exc != null) {
                errorSet.add(cr.uri);
            } else {
                loadedSet.add(cr.uri);
            }
            if (toBeStored != null) {
                toBeStored.add(cr);
            }
        }
        /** Salva lo stato attuale del SiteCrawler su memoria secondaria.
         * ATTENZIONE: se viene chiamato da più thread contemporaneamente
         * potrebbe dare risultati non deterministici !!! */
        void store() {
            try (
                    ObjectOutputStream toLoadOutputStream = new ObjectOutputStream(
                                                            Files.newOutputStream(directory.resolve("toLoad")) );
                    ObjectOutputStream resultsOutputStream = new AppendableObjectOutputStream(
                                                             Files.newOutputStream( directory.resolve("results"),
                                                                                    StandardOpenOption.APPEND) )
            ) {
                // Archivia gli URI che sono ancora in attesa di essere scaricati
                Set<URI> toLoadUris = getToLoad();
                toLoadOutputStream.writeObject(toLoadUris);
                // Archivia i CrawlerResult
                for ( CrawlerResult cr : toBeStored ) {
                    resultsOutputStream.writeObject(cr.uri);
                    resultsOutputStream.writeBoolean(cr.linkPage);
                    resultsOutputStream.writeObject(cr.links);
                    resultsOutputStream.writeObject(cr.errRawLinks);
                    resultsOutputStream.writeObject(cr.exc);
                }
                toBeStored.clear();
            } catch (IOException e) {
                System.out.println(e);
                throw new IllegalArgumentException("si sta cercando di scrivere su un file inesistente " +
                                                   "o di salvare oggetti che non sono Serializable");
            }
        }

        Set<URI> retrieveToLoadURIsFromDisk() throws IOException, ClassNotFoundException {
            InputStream toLoadFileStream = Files.newInputStream( directory.resolve("toLoad") );
            ObjectInputStream toLoadInputStream = new ObjectInputStream(toLoadFileStream);
            Set<URI> toLoadURIs = (Set<URI>) toLoadInputStream.readObject();
            toLoadInputStream.close();
            return toLoadURIs;
        }

        void cancel() {
            uriMap.clear();
            loadedSet.clear();
            errorSet.clear();
            resultQueue.clear();
        }
    }

    /* Instance Fields */
    private final Crawler crawler;

    private final Data data;

    private Thread runningThread = null;

    /* Constructors */

    /** Ripristina l'esplorazione da una directory */
    SimpleSiteCrawler(Path dir) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("la directory di archiviazione non può essere null!");
        }
        data = new Data(null, dir);
        try {
            // Ripristina gli URI che sono in attesa di essere scaricati
            Set<URI> toLoadURIs = data.retrieveToLoadURIsFromDisk();
            // Ripristina le pagine già scaricate
            crawler = WebFactory.getCrawler( data.loadedSet,
                                             toLoadURIs,
                                             data.errorSet,
                                             (u) -> SiteCrawler.checkSeed(data.domain, u) );
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e);
            throw new IllegalArgumentException( "la directory di archiviazione non esiste o non è valida" );
        }
    }
    /**Costruisce un nuovo SimpleSiteCrawler.      Se dom e directory sono entrambi non null,
     * assume che sia un nuovo web site con dominio dom da archiviare nella directory
     * directory. Se dom non è null e directory è null, l'esplorazione del web site con dominio
     * dom sarà eseguita senza archiviazione. Se dom è null e directory non è null, assume
     * che l'esplorazione del web site sia già archiviata nella directory directory e la
     * apre. Per scaricare le pagine usa esclusivamente un {@link wsa.web.Crawler}
     * fornito da
     * {@link wsa.web.WebFactory#getCrawler(Collection, Collection, Collection, Predicate)}.
     * @param dom  un dominio o null
     * @param dir  un percorso di una directory o null
     * @throws IllegalArgumentException se dom e directory sono entrambi null o dom è
     * diverso da null e non è un dominio o directory è diverso da null non è una
     * directory o dom è null e directory non contiene l'archivio di un SiteCrawler.
     * @throws IOException se accade un errore durante l'accesso all'archivio
     * del SiteCrawler*/
    SimpleSiteCrawler(URI dom, Path dir) throws IOException {
        if ( dom == null || !SiteCrawler.checkDomain(dom) ) {
            throw new IllegalArgumentException("il dominio non è valido");
        }
        data = new Data(dom, dir);
        crawler = WebFactory.getCrawler( null, null, null, (u) -> SiteCrawler.checkSeed(data.domain, u) );
    }

    /* Instance Methods */
    /** Aggiunge un seed URI. Se però è presente tra quelli già scaricati,
     * quelli ancora da scaricare o quelli che sono andati in errore,
     * l'aggiunta non ha nessun effetto. Se invece è un nuovo URI, è aggiunto
     * all'insieme di quelli da scaricare.
     * @throws IllegalArgumentException se uri non appartiene al dominio di
     * questo SuteCrawlerrawler
     * @throws IllegalStateException se il SiteCrawler è cancellato
     * @param uri  un URI */
    @Override
    public void addSeed(URI uri) {
        if (isCancelled() ) {
            throw new IllegalStateException("Il SiteCrawler è cancellato");
        } else if ( !SiteCrawler.checkSeed(data.domain, uri) ) {
            throw new IllegalArgumentException("L'uri non appartiene al dominio di questo SiteCrawler");
        }
        crawler.add(uri);
    }
    /** Inizia l'esecuzione del SiteCrawler se non è già in esecuzione e ci sono
     * URI da scaricare, altrimenti l'invocazione è ignorata. Quando è in
     * esecuzione il metodo isRunning ritorna true.
     * @throws IllegalStateException se il SiteCrawler è cancellato */
    @Override
    public void start() {
        if (isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        boolean isThreadAlive = runningThread != null && runningThread.isAlive();
        if ( isThreadAlive || getToLoad().isEmpty() ) {
            return;
        }
        runningThread = new Thread( () -> {
            crawler.start();
            long lastArchiveTime = System.currentTimeMillis();
            boolean mustStop = false;
            while (!mustStop) {
                try {
                    CrawlerResult crawlerResult = crawler.get().get();
                    while (crawlerResult.uri != null) {
                        data.put(crawlerResult);
                        crawlerResult = crawler.get().get();
                    }
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    mustStop = true;
                } catch (NoSuchElementException e) {
                    e.printStackTrace();
                    if (!crawler.isRunning()) {
                        System.out.println("Il Crawler usato da questo SiteCrawler non è in running!!!");
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    mustStop = true;
                }
                // se sono passati più di 30 secondi dall'ultimo backup lo rifà
                boolean isTimeToBackup = (System.currentTimeMillis() - lastArchiveTime) >= 30000;
                if (data.directory != null && isTimeToBackup) {
                    data.store();
                    lastArchiveTime = System.currentTimeMillis();
                }
            }
            // Fa il get di eventuali risultati rimasti prima di sospendere il Crawler
            CrawlerResult crawlerResult = crawler.get().get();
            while (crawlerResult.uri != null) {
                data.put(crawlerResult);
                crawlerResult = crawler.get().get();
            }
            crawler.suspend();
            if (data.directory != null) {
                data.store();
            }
        });
        this.runningThread.setDaemon(true);
        this.runningThread.start();

    }

    /** Sospende l'esecuzione del SiteCrawler. Se non è in esecuzione, ignora
     * l'invocazione. L'esecuzione può essere ripresa invocando start. Durante
     * la sospensione l'attività dovrebbe essere ridotta al minimo possibile
     * (eventuali thread dovrebbero essere terminati). Se è stata specificata
     * una directory per l'archiviazione, lo stato del crawling è archiviato.
     * @throws IllegalStateException se il SiteCrawler è cancellato */
    @Override
    public void suspend() {
        if (isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        if (runningThread != null) {
            runningThread.interrupt();
        }
        runningThread = null;
    }

    /** Cancella il SiteCrawler per sempre. Dopo questa invocazione il
     * SiteCrawler non può più essere usato. Tutte le risorse sono
     * rilasciate. */
    @Override
    public void cancel() {
        if (runningThread != null) {
            runningThread.interrupt();
        }
        crawler.cancel();
        data.cancel();
        runningThread = null;
    }

    /** Ritorna il risultato relativo al prossimo URI. Se il SiteCrawler non è
     * in esecuzione, ritorna un Optional vuoto. Non è bloccante, ritorna
     * immediatamente anche se il prossimo risultato non è ancora pronto.
     * @throws IllegalStateException se il SiteCrawler è cancellato
     * @return  il risultato relativo al prossimo URI scaricato */
    @Override
    public Optional<CrawlerResult> get() {
        if (isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        Optional<CrawlerResult> optional;
        if ( this.isRunning() ) {
            CrawlerResult result = data.resultQueue.poll();
            if (result == null) {
                // Controlla che il Crawler non abbia un risultato pronto all'istante
                if ( crawler.isRunning() ) {
                    synchronized (crawler) {
                        result = crawler.get().get();
                    }
                    if (result.uri != null) {
                        data.put(result);
                        result = data.resultQueue.poll();
                    }
                } else {
                    result = new CrawlerResult(null, false, null, null, null);
                }
            }
            optional = Optional.of(result);
        } else {
            optional = Optional.empty();
        }
        return optional;
    }

    /** Ritorna il risultato del tentativo di scaricare la pagina che
     * corrisponde all'URI dato.
     * @param uri  un URI
     * @throws IllegalArgumentException se uri non è nell'insieme degli URI
     * scaricati né nell'insieme degli URI che hanno prodotto errori.
     * @throws IllegalStateException se il SiteCrawler è cancellato
     * @return il risultato del tentativo di scaricare la pagina */
    @Override
    public CrawlerResult get(URI uri) {
        if (isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        } else if ( !data.loadedSet.contains(uri) && !data.errorSet.contains(uri) ) {
            throw new IllegalArgumentException( "uri non è nell'insieme degli URI scaricati " +
                                                "né nell'insieme degli URI che hanno prodotto errori");
        }
        return data.uriMap.get(uri);
    }

    /** Ritorna l'insieme di tutti gli URI scaricati, possibilmente vuoto.
     * @throws IllegalStateException se il SiteCrawler è cancellato
     * @return l'insieme di tutti gli URI scaricati (mai null) */
    @Override
    public Set<URI> getLoaded() {
        if (isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        return data.loadedSet;
    }

    /** Ritorna l'insieme, possibilmente vuoto, degli URI che devono essere
     * ancora scaricati. Quando l'esecuzione del crawler termina normalmente
     * l'insieme è vuoto.
     * @throws IllegalStateException se il SiteCrawler è cancellato
     * @return l'insieme degli URI ancora da scaricare (mai null) */
    @Override
    public Set<URI> getToLoad() {
        if (isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        return crawler.getToLoad();
    }

    /** Ritorna l'insieme, possibilmente vuoto, degli URI che non è stato
     * possibile scaricare a causa di errori.
     * @throws IllegalStateException se il SiteCrawler è cancellato
     * @return l'insieme degli URI che hanno prodotto errori (mai null) */
    @Override
    public Set<URI> getErrors() {
        if (isCancelled()) {
            throw new IllegalStateException("Il Crawler è cancellato");
        }
        return data.errorSet;
    }

    /** Ritorna true se il SiteCrawler è in esecuzione.
     * @return true se il SiteCrawler è in esecuzione */
    @Override
    public boolean isRunning() {
        return runningThread != null && runningThread.isAlive();
    }

    /** Ritorna true se il SiteCrawler è stato cancellato. In tal caso non può
     * più essere usato.
     * @return true se il SiteCrawler è stato cancellato */
    @Override
    public boolean isCancelled() {
        return crawler == null;
    }
}
