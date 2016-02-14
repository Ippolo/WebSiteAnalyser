package wsa.gui.util;

import javafx.concurrent.Task;
import wsa.web.CrawlerResult;
import wsa.web.SiteCrawler;

import java.net.URI;
import java.util.*;

/**
 * Classe di utilità che contiene metodi che ritornano Task che possono essere utili ad un WSA per calcolare delle
 * informazioni, mantenendo la gui reattiva durante il calcolo
 */
public class TaskFactory {
    /** Ritorna un task che calcola i link di un SiteCrawler verso un altro dominio
     * @param crawler il crawler da cui prendere i dati
     * @param crawlerDomain il dominio di crawler
     * @param domain il dominio dei link della lista ritornata
     * @return la lista dei link di crawler verso domain*/
    public static Task<Set<URI>> linksToDomainTask(SiteCrawler crawler, URI crawlerDomain, URI domain) {
        return new Task<Set<URI>>() {
            @Override
            protected Set<URI> call() throws Exception {
                // Non prende i link da getLoaded e getErrors perchè il crawler potrebbe
                // essere in pausa,ma non aver ancora terminato l'esplorazione
                Set<URI> linkToDomainSet = new HashSet<>();
                crawler.getLoaded().stream()
                        .filter( (u) -> SiteCrawler.checkSeed(crawlerDomain, u) )
                        .forEach( (u_) -> {
                            CrawlerResult result = crawler.get(u_);
                            result.links.stream()
                                    .filter( (u) -> SiteCrawler.checkSeed(domain, u) )
                                    .forEach(linkToDomainSet::add);
                        });
                return  linkToDomainSet;
            }
        };
    }
}
