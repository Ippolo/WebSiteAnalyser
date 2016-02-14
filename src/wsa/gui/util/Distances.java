package wsa.gui.util;

import javafx.concurrent.Task;
import wsa.gui.BackEnd;
import wsa.web.SiteCrawler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/** Una classe che fornisce Task per calcolare la mappa delle distanze di un URI verso tutti gli altri uri di un
 * BackEnd e la massima distanza tra tutte le coppie di URI. Salva ogni mappa delle distanze calcolata in modo
 * da poter accedere al risultato in futuro senza inizializzare di nuovo un Task. Tutti i metodi devono essere chiamati
 * quando il SiteCrawler non è in esplorazione. */
public class Distances {
    /* Nested Classes */
    /** Rappresenta la distanza tra una coppia di uri */
    public static class URIDistance {
        public final URI from;
        public final URI to;
        public final int distance;
        public URIDistance(URI uri1, URI uri2, int distance){
            this.from = uri1;
            this.to = uri2;
            this.distance = distance;
        }
    }

    /* Instance Fields */
    private final BackEnd backEnd;
    private final Map<URI, Map<URI, Integer>> distanceMap = new HashMap<>();

    /* Constructors */
    /** Metodo Costruttore */
    public Distances(BackEnd owner) {
        backEnd = owner;
        backEnd.getWorker().runningProperty().addListener((o, ov, nv) -> {
            if (nv) {// se l'esplorazione viene cominciata o ripresa
                distanceMap.clear();// Cancella la mappa delle distanze
            }
        });
    }

    /* Instance Methods */
    /** Ritorna un task che calcola la distanza tra un uri e tutti gli URI appartenenti al dominio del BackEnd.
     * @param uri l'uri di cui calcolare la mappa delle distanze
     * @throws IllegalStateException se il SiteCrawler è in esplorazione
     * @throws IllegalArgumentException se uri non è stato esplorato
     * @return un task che calcola la mappa delle distanze di uri*/
    public Task<Map<URI, Integer>> newDistanceTask(URI uri) {
        if( backEnd.getWorker().isRunning() ) {
            throw new IllegalStateException("l'esplorazione è ancora in corso");
        }
        return new Task<Map<URI, Integer>>() {
            @Override
            protected Map<URI, Integer> call() throws Exception {
                return getDistancesCalculationCallable(uri, this).call();
            }
        };
    }
    /** Ritorna un task per calcolare la massima distanza tra tutte le coppie di uri appartenenti al dominio di un
     *  BackEnd.
     *  @throws IllegalStateException se il SiteCrawler è in esplorazione
     * @return  un task per calcolare la distanza tra tutte le coppie di uri appartenenti al dominio di crawler*/
    public Task<URIDistance> newMaxURIDistanceTask() {
        if( backEnd.getWorker().isRunning() ) {
            throw new IllegalStateException("l'esplorazione è ancora in corso");
        }
        SiteCrawler crawler = backEnd.getCrawler();
        URI domain = backEnd.getDomain();
        return new Task<URIDistance>() {
            @Override
            protected URIDistance call() throws Exception {
                ExecutorService executor = Executors.newCachedThreadPool((runnable) -> {
                    Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                    thread.setDaemon(true);
                    return thread;
                });
                // per prima cosa popola la mappa delle distanze tra tutte le coppie
                Map<URI, Future<Map<URI, Integer>>> futureMap = new HashMap<>();
                Stream<URI> loaded = crawler.getLoaded().stream();
                Stream<URI> errors = crawler.getErrors().stream();
                Stream.concat(loaded, errors)// Stream di tutti gli uri visitati
                        .filter((u) -> SiteCrawler.checkSeed(domain, u))// mantiene solo quelli interni
                        .forEach((u) -> {
                            if (!distanceMap.containsKey(u)) {
                                FutureTask<Map<URI, Integer>> task =
                                        new FutureTask<>( getDistancesCalculationCallable(u, this) );
                                executor.submit(task);
                                futureMap.put(u, task);
                            }
                        });
                for (URI uri : futureMap.keySet()){
                    try {
                        Map<URI, Integer> distMap = futureMap.get(uri).get();
                        // Se questo task è stato interrotto #getDistancesCalculationCallable potrebbe ritornare null
                        // e passare questo valore alla futureMap
                        if (distMap != null) {
                            distanceMap.put(uri, distMap);
                        }
                    } catch (InterruptedException ex) {
                        System.out.println("calcolo massima distanza interrotto");
                    } catch (ExecutionException ex) {
                        System.out.println("ExecutionException in wsa.gui.util.Distances - codice 1");
                    }
                }
                // Una volta popolata la mappa, calcola la massima distanza tra tutte le coppie!
                List<URIDistance> maxDistsPerURI = new ArrayList<>();
                for ( URI fromURI : distanceMap.keySet() ) {
                    if ( isCancelled() ) {
                        break;
                    }
                    Map<URI, Integer> distMap = distanceMap.get(fromURI);
                    URI currentMaxURI = null;
                    int currentMaxDist = -1;
                    for ( URI toURI : distMap.keySet() ) {
                        int dist = distMap.get(toURI);
                        if (dist > currentMaxDist) {
                            currentMaxDist = dist;
                            currentMaxURI = toURI;
                        }
                    }
                    maxDistsPerURI.add(new URIDistance(fromURI, currentMaxURI, currentMaxDist));
                }
                Optional<URIDistance> optional = maxDistsPerURI.stream().max((URIDistance o1, URIDistance o2) ->
                                                                             Integer.compare(o1.distance, o2.distance));
                return optional.isPresent() ? optional.get() : null;
            }
        };
    }
    /** Ritorna a mappa delle distanze di un dato uri se è stata calcolata da un precedente task, altrimenti null
     * @throws IllegalStateException se il SiteCrawler è in esplorazione
     * @return la mappa delle distanze di uri o null */
    public Map<URI, Integer> getMapOrNull(URI uri) {
        if( backEnd.getWorker().isRunning() ) {
            throw new IllegalStateException("l'esplorazione è ancora in corso");
        }
        return distanceMap.get(uri);
    }

    /** Ritorna un Callable che calcola la mappa delle distanze di un dato uri.
     * Questo metodo è scritto per essere chiamato da un Task.
     * @param uri l'uri di cui calcolare la mappa delle distanze
     * @param caller il task chiamante
     * @return la mappa delle distanze di uri*/
    private Callable<Map<URI, Integer>> getDistancesCalculationCallable(URI uri, Task<?> caller) {
        return () -> {
            SiteCrawler crawler = backEnd.getCrawler();
            URI domain = backEnd.getDomain();
            // Un ringrazziamento al prof Pellacini per l'algoritmo di visita BFS!
            // http://pellacini.di.uniroma1.it/teaching/fondamenti14/lectures/lecture20/notes.html
            Set<URI> visited = new HashSet<>();
            Stack<URI> active = new Stack<>();
            visited.add(uri);
            active.add(uri);
            Map<URI, Integer> dist = new HashMap<>();
            dist.put(uri, 0);
            while ( !active.isEmpty() && !caller.isCancelled() ) {
                Stack<URI> newActive = new Stack<>();
                while ( !active.isEmpty() && !caller.isCancelled() ) {
                    URI visitingURI = active.pop();
                    if ( crawler.getLoaded().contains(visitingURI)
                          || crawler.getErrors().contains(visitingURI) )
                    {
                        crawler.get(visitingURI).links.stream()// Stream di tutti i link del visitingURI
                                .filter((u) -> SiteCrawler.checkSeed(domain, u) && (
                                                crawler.getLoaded().contains(u) || crawler.getLoaded().contains(u))
                                ).forEach((u) -> {// e per ognuno
                                    if ( !visited.contains(u) ) {// se non è già stato visitato
                                        visited.add(u);// lo visita
                                        newActive.add(u);
                                        dist.put(u, dist.get(visitingURI) + 1);
                                    }
                                });
                    }
                }
                active = newActive;
            }
            if ( !caller.isCancelled() ) {
                distanceMap.put(uri, dist);
            }
            return caller.isCancelled() ? null : dist;
        };
    }
}
