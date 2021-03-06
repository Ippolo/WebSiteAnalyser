package wsa.gui.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javafx.beans.binding.ListBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.collections.transformation.SortedList;
import wsa.gui.BackEnd;
import wsa.web.CrawlerResult;

/** Una classe di utilità che monitora le pagine che puntano ad ogni URI esplorato da un BackEnd.
 * Tiene traccia dell'URI a cui puntano più pagine (e quante),  permette di ricavare la lista
 * delle pagine che puntano ad ogni URI sotto forma di CrawlerResult*/
public class Pointings {
    /* Instance Fields */
    public final IntegerProperty maxPointingsProperty = new SimpleIntegerProperty(0);
    public final ObjectProperty<URI> maxPointingURIProperty = new SimpleObjectProperty<>(null);
    private final Map<URI, ObservableSet<CrawlerResult>> map = new HashMap<URI, ObservableSet<CrawlerResult>>() {
        @Override public ObservableSet<CrawlerResult> get(Object key) {
            ObservableSet<CrawlerResult> value = super.get(key);
            if (value == null) {
                value = FXCollections.observableSet();
                put( (URI)key, value );
            }
            return value;
        }
    };

    /* Constructors */
    /** Metodo costruttore
     * @param owner il BackEnd che rappresenta l'esplorazione di un dominio */
    public Pointings(BackEnd owner) {
        synchronized ( owner.resultObservableList() ) {
            // Calcola la mappa di tutti i link che puntano ad ogni URI
            owner.resultObservableList().stream()
                    .filter((cr) -> cr.links != null && cr.exc == null)
                    .forEach((cr) ->
                                    cr.links.stream().forEach((u) ->
                                            map.get(u).add(cr))
                    );
            // Calcola il risultato a cui puntano più link, se è presente
            Optional<CrawlerResult> optional = owner.resultObservableList().stream().max((o1, o2) -> {
                int linksPointingToO1 = map.get(o1.uri).size();
                int linksPointingToO2 = map.get(o2.uri).size();
                return Integer.compare(linksPointingToO1, linksPointingToO2);
            });
            if (optional.isPresent()) {
                maxPointingURIProperty.setValue(optional.get().uri);
            }
            // Listener che mantiene aggiornati tutti i valori
            owner.resultObservableList().addListener( (ListChangeListener.Change<? extends CrawlerResult> c) -> {
                while ( c.next() ) {
                    c.getAddedSubList().forEach( (cr) -> {
                        if (cr.links != null) {
                            // Mantiene aggiornata la mappa di tutti i link che puntano ad ogni URI
                            cr.links.stream().forEach( (u) ->
                                    addPointer(u, cr) );
                        }
                    });
                }
            });
        }
    }

    /* Instance Methods */
    /** Ritorna un ObservableList che si aggiorna costantemente da sola in tempo reale e contiene tutti i CrawlerResult
     * dell'esplorazione che hanno un certo uri tra i links
     * @param uri l'uri a cui puntano tutte le pagine della lista ritornata
     * @return un'ObservableList di tutte le pagine che puntano ad uri*/
    public ObservableList<CrawlerResult> getPointingObservableList(URI uri) {
        return new ListBinding<CrawlerResult>() {
            private final ObservableList<CrawlerResult> backEndList;
            private final SetChangeListener<CrawlerResult> listener;
            { // Initializer block
                ObservableList<CrawlerResult> list = FXCollections.observableArrayList();
                listener = (SetChangeListener.Change<? extends CrawlerResult> c) -> {
                    if ( c.wasAdded() )
                        list.add( c.getElementAdded() );
                    else
                        list.remove( c.getElementRemoved() );
                };
                ObservableSet<CrawlerResult> items = map.get(uri);
                synchronized (items) {
                    list.addAll(items);
                    items.addListener( new WeakSetChangeListener<>(listener) );
                }
                backEndList = new SortedList<>(list, (o1, o2) -> o1.uri.compareTo(o2.uri));
                bind(items);
            }
            @Override
            protected ObservableList<CrawlerResult> computeValue() {
                return backEndList;
            }
        };
    }

    /** Metodo helper del costruttore */
    private void addPointer(URI uri, CrawlerResult pointer) {
        Set<CrawlerResult> pointings = map.get(uri);
        pointings.add(pointer);
        if ( pointings.size() > maxPointingsProperty.getValue() ) {
            maxPointingsProperty.setValue( pointings.size() );
            maxPointingURIProperty.setValue(uri);
        }
    }
}
