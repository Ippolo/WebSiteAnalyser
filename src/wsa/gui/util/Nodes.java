package wsa.gui.util;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Callback;
import wsa.gui.BackEnd;
import wsa.gui.scene.InfoPane;
import wsa.web.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

/** Una classe di utilità che fornisce metodi che ritornano Node che visualizzano informazioni utili riguardo
 * l'esplorazione di un dominio*/
public class Nodes {
    /* Static Methods */
    /** Ritorna un Pane che funge da molla occupando tutto lo spazio disponibile tra
     * due nodi
     * @return un Pane che funge da molla*/
    public static Node getSplitPane() {
        Pane spring = new Pane();
        HBox.setHgrow(spring, Priority.ALWAYS);
        return spring;
    }
    /** Ritorna un box che contiene un titolo e, a richiesta, si espande e mostra un ListView.
     * Il titolo può essere aggiornato. Se gli elementi di {@link Parent#getChildrenUnmodifiable()}
     * sono più di uno la listView è visibile, altrimenti no.
     * @param title StringProperty che indica il titolo del box
     * @param list ObservableList dai cui prendere gli elementi da visualizzare nella ListView
     * @param cellFactory la cellFactory per la ListView o null se si vuole quella di default
     * @return un box con un titolo che può mostrare una ListView a richiesta*/
    public static <T> Parent getViewBox( StringProperty title,
                                         ObservableList<T> list,
                                         Callback<ListView<T>,ListCell<T>> cellFactory ) {
        VBox viewBox = new VBox() {
            {// Il nodo che dovrà essere ritornato!
                setStyle("-fx-border-color: #346CDF;");
            }
        };
        ListView<T> listView = new ListView<T>() {
            {// View degli elementi da visualizzare
                setItems(list);
                setStyle("-fx-focus-color: transparent;");
                if (cellFactory != null) {
                    setCellFactory(cellFactory);
                }
                // Aggiusta l'altezza asseconda degli elementi da visualizzare
                final double CELL_SIZE = 24;
                double prefHeight = list.size() * CELL_SIZE;
                setPrefHeight(prefHeight <= CELL_SIZE * 10 ? prefHeight : CELL_SIZE * 10);
                list.addListener((ListChangeListener.Change<? extends T> c) -> {
                    double height = list.size() * CELL_SIZE;
                    setPrefHeight(height <= CELL_SIZE * 10 ? height : CELL_SIZE * 10);
                });
            }
        };
        Node titleBox = new HBox() {
            {// Titolo e bottone per mostrare/nascondere la listView
                Node titleLabel = new Label() {
                    {// Il testo del titolo
                        textProperty().bind(title);
                    }
                };
                Node showButton = new Button() {
                    {// Se cliccato mostra o nasconde la listView
                        setPadding(Insets.EMPTY);
                        Image downIcon = new Image(Nodes.class.getResource("down.png").toString());
                        Node downGraphic = new ImageView(downIcon);
                        Image upIcon = new Image(Nodes.class.getResource("up.png").toString());
                        Node upGraphic = new ImageView(upIcon);
                        setGraphic(downGraphic);
                        setOnAction((e) -> {
                            if (viewBox.getChildren().size() > 1) {// Se la ListView è visibile
                                setGraphic(downGraphic);
                                viewBox.getChildren().remove(listView);
                            } else {
                                setGraphic(upGraphic);
                                viewBox.getChildren().add(listView);
                            }

                        });
                    }
                };
                setAlignment(Pos.CENTER);
                setPadding(new Insets(3, 3, 3, 3));
                setMinHeight(25);
                getChildren().addAll(titleLabel, getSplitPane(), showButton);
            }
        };
        viewBox.getChildren().addAll(titleBox);
        return viewBox;
    }

    /* Instance Fields */
    private final BackEnd backEnd;
    private final InfoPane infoPane;
    private byte DEBUG = 0;

    /* Constructors */
    /** Metodo costruttore
     * @param be il BackEnd che rappresenta l'esplorazione di un dominio
     * @param ip un InfoPane con cui interfacciarsi*/
    public Nodes(BackEnd be, InfoPane ip) {
        backEnd = be;
        infoPane = ip;
    }

    /* Instance Methods */
    /** Ritorna un bottone che se premuto visualizza un Dialog che permettere di aggiungere un nuovo URI seed
     * all'esplorazione di un dominio */
    public Node getAddSeedButton(byte debug) {
        return new Button() {
            {
                Dialog<String> addSeedDialog = new TextInputDialog() {
                    {
                        setGraphic(null);
                        setTitle("Aggiungi seed URI");
                        setHeaderText("Inserisci l'indirizzo del nuovo seed uri");
                    }
                };
                setText("addSeed");
                setOnAction(e -> {
                    Optional<String> result = addSeedDialog.showAndWait();
                    if (result.isPresent()) {
                        try {
                            String uriString = result.get();
                            URI uri = new URI(uriString);
                            if( backEnd.getCrawler().getLoaded().contains(uri)
                                    || backEnd.getCrawler().getToLoad().contains(uri)
                                    || backEnd.getCrawler().getErrors().contains(uri) )
                            {
                                new Alert(Alert.AlertType.WARNING, "URI già presente").showAndWait();
                            } else {
                                backEnd.getCrawler().addSeed(uri);
                                if (debug > 0) {
                                    System.out.println("aggiunto " + uri + " ai seed");
                                }
                            }
                        } catch (URISyntaxException exc) {
                            new Alert(Alert.AlertType.ERROR, "URI invalido!").showAndWait();
                        } catch (IllegalArgumentException exc) {
                            new Alert(Alert.AlertType.ERROR, "questo URI non appartiene ad dominio!").showAndWait();
                        }
                    } else {
                        if (debug > 0) {
                            System.out.println("no result");
                        }
                    }
                });
            }
        };
    }

    /** Ritorna un bottone che se premuto fa partire l'esplorazione del dominio, o la mette in pausa se è già in corso*/
    public Node getStartOrStopButton() {
        return new Button() {
            {
                textProperty().bind(Bindings.createStringBinding(
                        () -> backEnd.getWorker().isRunning() ? "Stop" : "Start",// func
                        backEnd.getWorker().runningProperty()));// dependencies
                setOnAction(e -> {
                    if (backEnd.getWorker().isRunning()) {
                        backEnd.getWorker().cancel();
                    } else {
                        backEnd.startCrawling();
                    }
                });
            }
        };
    }

    public Node getCancelButton() {
        return new Button() {
            {
                setText("cancel");
                setOnAction(e -> {
                    URL url = null;
                    try {
                        url = new URL("file:/Users/RIK/DIDATTICA/METODOLOGIEPROG2014_15/Esami/Homeworks/hw3_files/testSiteCrawler/pages/SiteDir/p0001.html");
                    } catch (MalformedURLException exc) {
                        System.out.println("cazzo");
                    }
                    System.out.println(backEnd.getCrawler().getToLoad());
                });
            }
        };
    }



    /** Ritorna un bottone che, se cliccato, mostra sull'InfoPane le info su una pagina scaricata
     * @param cr il CrawlerResult che rappresenta la pagina scaricata
     * @return  il bottone per mostrare il risultato*/
    public Node getGoButton(CrawlerResult cr) {
        return new Button() {
            {
                Image icon = new Image(getClass().getResource("goButton.jpg").toString());
                setGraphic(new ImageView(icon));
                setPadding(Insets.EMPTY);
                setOnAction((e) -> {
                    if (DEBUG > 0)
                        System.out.println("carico il risultato di " + cr.uri.toString());
                    infoPane.showResultInfo(cr);
                });
            }
        };
    }
    /** Ritorna un bottone che, se cliccato, mostra sull'InfoPane le info su cr. Da
     * usare nel caso in cui il CrawlerResult di uri non sia disponibile al momento
     * della creazione. Si assume comunque che quando il bottone sarà cliccato il
     * CrawlerResult di uri sarà acquisibile col metodo {@link wsa.web.SiteCrawler#get(URI)}
     * @param uri l'uri di cui mostrare il risultato
     * @return  il bottone per mostrare il risultato*/
    public Node getGoButton(URI uri) {
        return new Button() {
            {
                Image backIcon = new Image(getClass().getResource("goButton.jpg").toString());
                setGraphic(new ImageView(backIcon));
                setPadding(Insets.EMPTY);
                setOnAction((e) -> {
                    if (DEBUG > 0)
                        System.out.println("carico il risultato di " + uri.toString());
                    infoPane.showResultInfo(backEnd.getCrawler().get(uri));
                });
            }
        };
    }
    /** Ritorna un bottone, che se cliccato, mostra su infoPane la View di cr
     * @param cr il risultato da mostrare
     * @return  il bottone per mostrare il risultato*/
    public Node getViewButton(CrawlerResult cr) {
        return new Button() {
            {
                Image icon = new Image(getClass().getResource("viewButton.jpg").toString());
                setGraphic(new ImageView(icon));
                setPadding(Insets.EMPTY);
                setOnAction((e) -> {
                    if (DEBUG > 0)
                        System.out.println("carico la view di " + cr.uri.toString());
                    infoPane.showView(cr);
                });
            }
        };
    }
    /** Ritorna un bottone, che se cliccato, mostra sull'InfoPane la View del risultato
     * dell'esplorazione di uri. Da usare nel caso in cui il CrawlerResult di uri non
     * sia disponibile al momento della creazione. Si assume comunque che quando il
     * bottone sarà cliccato il CrawlerResult di uri sarà acquisibile col metodo
     * {@link wsa.web.SiteCrawler#get(URI)}
     * @param uri l'uri di cui mostrare il risultato
     * @return  il bottone per mostrare il risultato*/
    public Node getViewButton(URI uri) {
        return new Button() {
            {
                Image icon = new Image(getClass().getResource("viewButton.jpg").toString());
                setGraphic(new ImageView(icon));
                setPadding(Insets.EMPTY);
                setOnAction((e) -> {
                    if (DEBUG > 0)
                        System.out.println("visualizzando " + uri.toString());
                    infoPane.showView(backEnd.getCrawler().get(uri));
                });
            }
        };
    }
    /** Ritorna un nodo che, se uri è stato scaricato, mostra i goButton e viewButton,
     * altrimenti gli viene attaccato un listener che li mostra appena l'uri viene
     * scaricato
     * @param uri l'uri a cui corrispondono i bottoni
     * @param showError true se si vuole mostrare anche un'icona in caso di errore
     * @return nodo che mostra goButton e viewButton appena sono disponibili*/
    public Node getButtonsOrListen(URI uri, boolean showError) {
        return new HBox() {
            private ListChangeListener<CrawlerResult> listener;
            {// Initializer HBox
                setSpacing(5);
                SiteCrawler crawler = backEnd.getCrawler();
                Node error = new Text("|E|");
                if ( showError ) {
                    error.setVisible(false);
                    getChildren().add(error);
                }
                Node goButton = getGoButton(uri);
                Node viewButton = getViewButton(uri);
                getChildren().addAll(viewButton, goButton);
                // se non è ancora stato scaricato nasconde i bottoni e li mostra quando lo diventa
                boolean isLoaded = crawler.getLoaded().contains(uri) || crawler.getErrors().contains(uri);
                if (!isLoaded) {
                    viewButton.setVisible(false);
                    goButton.setVisible(false);
                    listener = (ListChangeListener.Change<? extends CrawlerResult> c) -> {
                        while ( c.next() ) {
                            c.getAddedSubList().forEach( (cr) -> {
                                if( cr.uri.equals(uri) ) {
                                    if( showError && cr.exc != null)
                                        error.setVisible(true);
                                    viewButton.setVisible(true);
                                    goButton.setVisible(true);
                                    // Si autocancella dopo la prima chiamata
                                    listener = null;
                                }
                            });
                        }
                    };
                    backEnd.resultObservableList().addListener( new WeakListChangeListener<>(listener) );
                } else if (showError) {
                    CrawlerResult result = backEnd.getCrawler().get(uri);
                    if( result.exc != null )
                        error.setVisible(true);
                }
            }
        };

    }
}
