package wsa.web;

import java.net.URL;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Un AsyncLoader che per scaricare le pagine usa
 * esclusivamente {@link wsa.web.Loader} forniti da {@link wsa.web.WebFactory#getLoader()}.
 */
class SimpleAsyncLoader implements AsyncLoader {
    /** Costruisce un SimpleAsyncLoader */
    SimpleAsyncLoader() {
        for (int i = 0; i <= NUM_OF_WORKERS; i++) {
            loadersQueue.add(WebFactory.getLoader());
        }
    }
    /** Sottomette il downloading della pagina dello specificato URL e ritorna
     * un Future per ottenere il risultato in modo asincrono.
     * @param url  un URL di una pagina web
     * @throws IllegalStateException se il loader è chiuso
     * @return Future per ottenere il risultato in modo asincrono */
    @Override
    public Future<LoadResult> submit(URL url) {
        if (this.isShutdown())
            throw new IllegalStateException("Il loader è chiuso");
        return executor.submit( () -> {
            Loader loader = loadersQueue.poll();
            while (loader == null) {
                Thread.sleep(50);
                loader = loadersQueue.poll();
            }
            LoadResult result = loader.load(url);
            loadersQueue.add(loader);
            return result;
        });
    }
    /** Chiude il loader e rilascia tutte le risorse. Dopo di ciò non può più
     * essere usato. */
    @Override
    public void shutdown() {
        executor.shutdown();
        loadersQueue = null;
        executor = null;
    }
    /** Ritorna true se è chiuso.
     * @return true se è chiuso */
    @Override
    public boolean isShutdown(){
        return (executor == null);
    }

    private Queue<Loader> loadersQueue = new ConcurrentLinkedQueue<>();

    private ExecutorService executor = Executors.newFixedThreadPool(NUM_OF_WORKERS, (runnable) -> {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    private static final int NUM_OF_WORKERS = 55;
}
