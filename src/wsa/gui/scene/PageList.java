package wsa.gui.scene;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.*;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import wsa.gui.BackEnd;
import wsa.web.CrawlerResult;
import wsa.web.SiteCrawler;

/** /** Una componente grafica per un {@link BackEnd} che mostra
 * gli URI scaricati e dialoga con un {@link InfoPane} chiedendogli
 * di mostrare informazioni relative ad essi */
public class PageList {
    /* Instance Fields */
    private final InfoPane infoPane;

    private final Node node;

    /* Constructors */
    /** Metodo costruttore
     * @param owner il BackEnd di cui mostrare gli URI */
    public PageList(BackEnd owner, InfoPane ip) {
        infoPane = ip;
        synchronized ( owner.resultObservableList() ) {
            // Crea la Tab degli uri interni al dominio
            ObservableList<CrawlerResult> internalURIs = new FilteredList<>( owner.resultObservableList(),
                                                                             (cr) -> cr.linkPage           );
            Tab internalURIsTab = newResultsTab("Interni: ", internalURIs);
            // Crea la Tab degli uri esterni al dominio
            ObservableList<CrawlerResult> externalURIs = new FilteredList<>( owner.resultObservableList(),
                                                                             (cr) -> !cr.linkPage          );
            Tab externalURIsTab = newResultsTab("Esterni: ", externalURIs);
            // Crea la Tab degli uri che non è stato possibile scaricare
            ObservableList<CrawlerResult> errors = new FilteredList<>( owner.resultObservableList(),
                                                                       (cr) -> cr.exc != null        );
            Tab errorsTab = newResultsTab("Errori: ", errors);
            // Visualizza tutti i tabs in un TabPane
            node = new TabPane() {
                {
                    getStyleClass().add(TabPane.STYLE_CLASS_FLOATING);
                    getTabs().addAll( internalURIsTab, externalURIsTab, errorsTab );
                    setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
                    setTabMinHeight(20);
                }
            };
        }
    }

    /* Instance Methods */
    /** Ritorna il {@link javafx.scene.Node} che mostra questa componente grafica
     * @return il Node che mostra la PageList */
    public Node getNode(){
        return node;
    }

    /** Genera un Tab che visualizza in una ListView tutti i CrawlerResult in observableList e da la possibilità
     * di visualizzarli sull'InfoPane
     * @param tabName il nome con cui visualizzare questo tab
     * @param observableList ObservableList dei risultati da mostrare nel tab
     * @return un Tab che visualizza gli uri in observableList */
    private Tab newResultsTab(String tabName, ObservableList<CrawlerResult> observableList) {
        Node listView = new ListView<CrawlerResult>() {
            {
                setItems(observableList.sorted((o1, o2) -> o1.uri.compareTo(o2.uri)));
                setCellFactory(lv -> new ListCell<CrawlerResult>() {
                    @Override
                    protected void updateItem(CrawlerResult item, boolean empty) {
                        super.updateItem(item, empty);
                        Platform.runLater(() -> setText(item == null ? "" : item.uri.toString()));
                    }
                });
                getFocusModel().focusedItemProperty().addListener( (o, ov, nv) -> {
                    if (nv != null) {
                        infoPane.showResultInfo(nv);
                    }
                });
            }
        };
        return new Tab() {
            {
                setText(tabName + observableList.size());
                // Aggiorna il titolo ogni volta che cambiano gli elementi da visualizzare
                observableList.addListener( (ListChangeListener.Change<? extends CrawlerResult> c) ->
                        Platform.runLater(() -> setText(tabName + observableList.size())) );

                selectedProperty().addListener(
                        (o, ov, isSelected) -> setContent(isSelected ? listView : null)
                );
            }
        };
    }
}