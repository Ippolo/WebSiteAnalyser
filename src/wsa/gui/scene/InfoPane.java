package wsa.gui.scene;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebView;
import wsa.gui.BackEnd;
import wsa.gui.util.*;
import wsa.web.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Rappresenta un pannello su cui vengono mostrate sia informazioni generali riguardo il dominio
 * di un {@link wsa.gui.BackEnd}, sia informazioni specifiche riguardo le singole pagine del dominio
 * che sono state esplorate*/
public class InfoPane  {
    /* Nested Classes */
    /** Una classe interna che gestisce la visualizzazione del nodo che mostra le info generali
     * riguardanti il dominio che si sta esplorando */
    private class GeneralInfoNode {

        private Node node = null;

        /** @return il Node che visualizza le informazioni generali del dominio del BackEnd */
        Node get() {
            if (node == null) {
                node = new VBox() {
                    {
                        getStyleClass().add("infoPane-box");
                        setVgrow(this, Priority.ALWAYS);
                        Label domineName = new Label(backEnd.getDomain().toString());
                        getChildren().addAll( domineName,
                                              getNumberOfVisitedURIsBox(),
                                              getURIWithMaxLinks(),
                                              getURIWithMaxPointings(),
                                              getMaxDistancesBox(),
                                              getLinksToOtherSitesBox(),
                                              getGraphic()
                        );
                    }
                };
            }
            return node;
        }
        /**@return un istogramma che mostra la distribuzione dei link tra le varie pagine */
        private Node getGraphic() {
            CategoryAxis xAxis = new CategoryAxis();
            NumberAxis yAxis = new NumberAxis();
            BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
            barChart.setTitle("Distribuzione dei link nelle pagine");
            xAxis.setLabel("links");
            yAxis.setLabel("pagine");
            // Crea le classi di equivalenza delle pagine in base al numero dei link
            XYChart.Data<String, Number> class0_5 = new XYChart.Data<>("0-5", 0);
            XYChart.Data<String, Number> class6_15 = new XYChart.Data<>("6-15", 0);
            XYChart.Data<String, Number> class16_30 = new XYChart.Data<>("16-30", 0);
            XYChart.Data<String, Number> class31_50 = new XYChart.Data<>("31-50", 0);
            XYChart.Data<String, Number> class51_80 = new XYChart.Data<>("51-80", 0);
            XYChart.Data<String, Number> class81_100 = new XYChart.Data<>("81-100", 0);
            XYChart.Data<String, Number> class101_150 = new XYChart.Data<>("101-150", 0);
            XYChart.Data<String, Number> class151_300 = new XYChart.Data<>("151-300", 0);
            XYChart.Data<String, Number> class301_500 = new XYChart.Data<>("301-500", 0);
            XYChart.Data<String, Number> class501_1000 = new XYChart.Data<>("501-1000", 0);
            XYChart.Data<String, Number> classOver1000 = new XYChart.Data<>("1000+", 0);
            // Assegna ogni CrawlerResult alla sua classe di equivalenza
            Consumer<CrawlerResult> consumer = (cr) -> {
                if (cr.links.size() <= 5) {
                    class0_5.setYValue(class0_5.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 15) {
                    class6_15.setYValue(class6_15.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 30) {
                    class16_30.setYValue(class16_30.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 50) {
                    class31_50.setYValue(class31_50.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 80) {
                    class51_80.setYValue(class51_80.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 100) {
                    class81_100.setYValue(class81_100.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 150) {
                    class101_150.setYValue(class101_150.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 300) {
                    class151_300.setYValue(class151_300.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 500) {
                    class301_500.setYValue(class301_500.getYValue().intValue() + 1);
                } else if (cr.links.size() <= 1000) {
                    class501_1000.setYValue(class501_1000.getYValue().intValue() + 1);
                } else {
                    classOver1000.setYValue(classOver1000.getYValue().intValue() + 1);
                }
            };
            backEnd.resultObservableList().stream()
                    .filter( (cr) -> cr.linkPage )
                    .forEach(consumer);
            backEnd.resultObservableList().addListener( (ListChangeListener.Change<? extends CrawlerResult> c) -> {
                while ( c.next() ) {
                    c.getAddedSubList().stream()
                            .filter( (cr) -> cr.linkPage )
                            .forEach(consumer);
                }
            });
            XYChart.Series<String, Number> series1 = new XYChart.Series<>();
            series1.getData().addAll(class0_5, class6_15, class16_30, class31_50, class51_80, class81_100, class101_150,
                                     class151_300, class301_500, class501_1000, classOver1000);
            barChart.getData().add(series1);
            return barChart;
        }

        /** @return  un Node che visualizza il numero di uri scaricati */
        private Node getNumberOfVisitedURIsBox() {
            return new Label() {
                private ListChangeListener<CrawlerResult> listener;
                {
                    getStyleClass().add("information-box");
                    String text = "URI scaricati: ";
                    int visitedNumber = backEnd.resultObservableList().size();
                    setText(text + visitedNumber);
                    // Aggiorna il testo dopo ogni nuovo scaricamento
                    listener = (ListChangeListener.Change<? extends CrawlerResult> c) -> {
                        int visitedURIsNumber = backEnd.resultObservableList().size();
                        if (node.isManaged()) {
                            Platform.runLater(() -> setText(text + visitedURIsNumber));
                        } else {
                            setText(text + visitedURIsNumber);
                        }
                    };
                    backEnd.resultObservableList().addListener( new WeakListChangeListener<>(listener) );
                }
            };
        }
        /** @return  un Node che visualizza l'uri con più link di tutti */
        private Node getURIWithMaxLinks() {
            return new VBox() {
                ChangeListener<CrawlerResult> maxLinkResultListener;
                {
                    getStyleClass().add("information-box");
                    CrawlerResult result = maxLinkResultProperty.getValue();
                    String text = "URI con massimo numero di link: ";
                    String nLinks = result.uri == null ? "none" : String.valueOf(result.links.size());
                    Label numberOfMostLinksLabel = new Label(text + nLinks);
                    Label uriWithMaxLinksLabel = new Label();
                    if (result.uri != null) {
                        Node grapic = new HBox( nodes.getViewButton(result), nodes.getGoButton(result) );
                        uriWithMaxLinksLabel.setGraphic(grapic);
                        uriWithMaxLinksLabel.setText(result.uri.toString());
                    }
                    // Mantiene aggiornati i Label
                    maxLinkResultListener = (o, ov, nv) -> {
                        Node grapic = new HBox( nodes.getViewButton(nv), nodes.getGoButton(nv) );
                        Platform.runLater(() -> {
                            numberOfMostLinksLabel.setText(text + nv.links.size());
                            uriWithMaxLinksLabel.setGraphic(grapic);
                            uriWithMaxLinksLabel.setText(nv.uri.toString());
                        });
                    };
                    maxLinkResultProperty.addListener(new WeakChangeListener<>(maxLinkResultListener));
                    getChildren().addAll(numberOfMostLinksLabel, uriWithMaxLinksLabel);
                }
            };
        }
        /** @return  un Node che visualizza l'uri a cui puntano più pagine */
        private Node getURIWithMaxPointings() {
            return new VBox() {
                private ChangeListener<URI> uriListener;
                private ChangeListener<Number> numListener;
                {
                    getStyleClass().add("information-box");
                    String text = "URI a cui puntano il maggior numero di pagine: ";
                    Integer nPointings = pointings.maxPointingsProperty.getValue();
                    Label numberOfMostPointingsLabel = new Label(text + nPointings.toString());
                    URI uri = pointings.maxPointingURIProperty.getValue();
                    Label uriWithMaxPointingsLabel = new Label();
                    if (uri != null) {
                        Node graphic = nodes.getButtonsOrListen(uri, false);
                        uriWithMaxPointingsLabel.setGraphic(graphic);
                        uriWithMaxPointingsLabel.setText(uri.toString());
                    }
                    // Aggiorna i Label dopo ogni cambiamento
                    uriListener = (o, ov, nv) -> {
                        Node graphic = nodes.getButtonsOrListen(nv, false);
                        String uriString = nv.toString();
                        Platform.runLater(() -> {
                            uriWithMaxPointingsLabel.setGraphic(graphic);
                            uriWithMaxPointingsLabel.setText(uriString);
                        });
                    };
                    pointings.maxPointingURIProperty.addListener(new WeakChangeListener<>(uriListener));
                    numListener = (o, ov, nv) -> {
                        String pointingsNumber = nv.toString();
                        Platform.runLater( () ->
                                numberOfMostPointingsLabel.setText(text + pointingsNumber) );
                    };
                    pointings.maxPointingsProperty.addListener( new WeakChangeListener<>(numListener) );
                    getChildren().addAll(numberOfMostPointingsLabel, uriWithMaxPointingsLabel);
                }
            };
        }
        /** @return  un Node che permette di visualizzare la coppia di URI con la massima distanza*/
        private Node getMaxDistancesBox() {
            return new VBox() {
                private ChangeListener<Boolean> runningPropertyListener;
                private Task<Distances.URIDistance> task = null;
                {
                    Button maxDistanceCompuationButton = new Button("calcola massima distanza");
                    getChildren().add(maxDistanceCompuationButton);
                    // Quando il bottone viene premuto inizia a calcolare la massima distanza
                    maxDistanceCompuationButton.setOnAction( (e) -> {
                        task = distances.newMaxURIDistanceTask();
                        task.stateProperty().addListener( (o, ov, nv) -> {
                            if (nv == Worker.State.RUNNING) {
                                getChildren().clear();
                                getChildren().addAll(
                                        new Label("calcolo in corso..."),
                                        new Button() {
                                            {
                                                setText("cancel");
                                                setOnAction( (e) -> task.cancel() );
                                            }
                                        }
                                );
                            } else if (nv == Worker.State.SUCCEEDED) {
                                Distances.URIDistance uriDist = task.getValue();
                                getChildren().clear();
                                getChildren().add( new VBox() {
                                    {
                                        getStyleClass().add("information-box");
                                        getChildren().addAll(
                                                new Label("Massima distanza: " + uriDist.distance),
                                                new HBox( new Label("da: " + uriDist.from),
                                                          Nodes.getSplitPane(),
                                                          nodes.getGoButton(backEnd.getCrawler().get(uriDist.from)) ),
                                                new HBox( new Label("a:  " + uriDist.to),
                                                          Nodes.getSplitPane(),
                                                          nodes.getGoButton(backEnd.getCrawler().get(uriDist.to)) )
                                        );
                                    }
                                });
                            } else if (nv == Worker.State.CANCELLED) {
                                getChildren().clear();
                                getChildren().add(maxDistanceCompuationButton);
                            } else if (nv == Worker.State.FAILED) {
                                Throwable exc = task.getException();
                                getChildren().add(new Label(exc.toString()));
                                exc.printStackTrace();

                            }
                        });
                        Thread th = new Thread(task);
                        th.setDaemon(true);
                        th.start();
                    });
                    // se l'esplorazione viene cominciata o ripresa ripropone il bottone per effettuare il calcolo
                    runningPropertyListener = (o, ov, nv) -> {
                        if (nv) {
                            if (task != null) {
                                task.cancel();
                            }
                            getChildren().clear();
                            getChildren().add(maxDistanceCompuationButton);
                        }
                    };
                    backEnd.getWorker().runningProperty().addListener( new WeakChangeListener<>(runningPropertyListener) );
                    // Questo box può essere visibile solo quando l'esplorazione è ferma
                    //ed è stato scaricato almeno un risultato
                    visibleProperty().bind(Bindings.createBooleanBinding(() -> {
                        boolean hasLoadedSomething = !backEnd.getCrawler().getLoaded().isEmpty()
                                                     || !backEnd.getCrawler().getErrors().isEmpty();
                        return !backEnd.getWorker().isRunning() && hasLoadedSomething;
                    }, backEnd.getWorker().runningProperty()));
                }
            };
        }
        /** @return un node che permette di calcolare i link di un dominio verso un altro dominio */
        private Node getLinksToOtherSitesBox() {
            return new VBox() {
                {
                    getStyleClass().add("information-box");
                    Label valueLabel = new Label("link verso altri domini");
                    getChildren().addAll(
                            new ComboBox<URI>() {
                                {
                                    setEditable(true);
                                    ObservableList<BackEnd> sites = backEnd.getFrame().getSites();
                                    // Gli elementi sono tutti gli altri domini aperti
                                    sites.stream().forEach( (be) -> {
                                        URI dom = be.getDomain();
                                        URI mydom = backEnd.getDomain();
                                        if ( !dom.equals(mydom) && !getItems().contains(dom) ) {
                                            getItems().add(dom);
                                        }
                                    });
                                    sites.addListener( (ListChangeListener.Change<? extends BackEnd> c) -> {
                                        getItems().clear();
                                        sites.stream().forEach( (be) -> {
                                            URI dom = be.getDomain();
                                            URI mydom = backEnd.getDomain();
                                            if ( !dom.equals(mydom) && !getItems().contains(dom) ) {
                                                getItems().add(dom);
                                            }
                                        });
                                    });
                                    setOnAction( (e) -> {
                                        try {
                                            String stringURI = getEditor().getText();
                                            URI uri = new URI(stringURI);
                                            Task<Set<URI>> task = TaskFactory.linksToDomainTask( backEnd.getCrawler(),
                                                                                                  backEnd.getDomain(),
                                                                                                  uri                 );
                                            task.stateProperty().addListener( (o, ov, nv) -> {
                                                if (nv == Worker.State.RUNNING) {
                                                    valueLabel.setText("calcolo...");
                                                } else if (nv == Worker.State.SUCCEEDED) {
                                                    int value = task.getValue().size();
                                                    valueLabel.setText("link verso questo dominio: " + value);
                                                } else {
                                                    System.out.println(nv);
                                                }
                                            });
                                            Thread th = new Thread(task);
                                            th.setDaemon(true);
                                            th.start();
                                        } catch (URISyntaxException exc){
                                            valueLabel.setText("indirizzo invalido!!!");
                                        }
                                    });
                                }
                            },
                            valueLabel
                    );
                }
            };
        }
    }
    /** Una classe interna che gestisce la visualizzazione delle informazioni riguardanti un singolo URI che è stato
     * scaricato */
    private class ResultNode {
        /** @return il node che visualizza le informazioni riguardanti una singola pagina esplorata */
        Node get(CrawlerResult cr) {
            return new VBox() {
                {
                    getStyleClass().add("infoPane-box");
                    setVgrow(this, Priority.ALWAYS);
                    if( cr.exc != null ) {
                        this.setStyle("-fx-background-color: #FF0000;");
                    }
                    Node downloadStatus = new Label(cr.exc == null ? "scaricato correttamente" : cr.exc.toString());
                    Node uriLabel = new Label(cr.uri.toString());
                    getChildren().addAll( downloadStatus,
                                          uriLabel           );
                    if( cr.linkPage && cr.exc == null ) {
                        getChildren().addAll( getLinksBox(cr),
                                getNonURILinksBox(cr),
                                getPointingURIsBox(cr),
                                getDistanceCalculationField(cr),
                                getExtraInfoBox(cr) );
                    } else {
                        getChildren().add(getPointingURIsBox(cr));
                    }
                }
            };
        }
        /** @return un Box che, a richiesta, visualizza i link che puntano all'URI di cr */
        private Node getPointingURIsBox(CrawlerResult cr) {
            ObservableList<CrawlerResult> pointingURIsList = pointings.getPointingObservableList(cr.uri);
            StringProperty pointingURIsBoxTitle =
                    new SimpleStringProperty("URI che puntano a questa pagina: " + pointingURIsList.size());
            pointingURIsList.addListener((ListChangeListener.Change<? extends CrawlerResult> c) -> {
                Platform.runLater(() ->
                                pointingURIsBoxTitle.setValue("URI che puntano a questa pagina: " + pointingURIsList.size())
                );
            });
            return Nodes.getViewBox( pointingURIsBoxTitle,
                                     pointingURIsList.sorted( (o1, o2) -> o1.uri.compareTo(o2.uri) ),
                                     (lv) -> new ListCell<CrawlerResult>() {
                                         @Override
                                         protected void updateItem(CrawlerResult item, boolean empty) {
                                             super.updateItem(item, empty);
                                             Platform.runLater(() -> setText(item == null ? "" : item.uri.toString()));
                                         }
                                     } );
        }
        /** @return un Box che, a richiesta, visualizza i link estratti */
        private Node getLinksBox(CrawlerResult cr) {
            StringProperty title = new SimpleStringProperty( "link estratti: " + cr.links.size() );
            ObservableList<URI> links = FXCollections.observableArrayList(cr.links);
            return Nodes.getViewBox( title,
                                     links.sorted(URI::compareTo),
                                     (lv) -> new ListCell<URI>() {
                                         @Override protected void updateItem(URI item, boolean empty) {
                                             super.updateItem(item, empty);
                                             Node graphic = nodes.getButtonsOrListen(item, true);
                                             setGraphic(graphic);
                                             setText(item == null ? "" : item.toString());
                                         }
                                     } );
        }
        /** @return un Node che a richiesta mostra i link estratti che non sono uri */
        private Node getNonURILinksBox(CrawlerResult cr) {
            StringProperty errorLinksTitle =
                    new SimpleStringProperty("link che non sono uri: " + cr.errRawLinks.size());
            ObservableList<String> errorLinksList = FXCollections.observableArrayList(cr.errRawLinks);
            return Nodes.getViewBox( errorLinksTitle,
                                     errorLinksList.sorted( (o1, o2) -> {
                                         if (o1 == null) {
                                             o1 = "null";
                                         }
                                         if (o2 == null) {
                                             o2 = "null";
                                         }
                                         return o1.compareTo(o2);
                                     }),
                                     null );
        }
        /** @return un box che permette di calcolare la distanza tra il risultato visualizzato ed un altro uri interno*/
        private Node getDistanceCalculationField(CrawlerResult cr) {
            return new VBox() {
                {
                    getStyleClass().add("information-box");
                    if ( !backEnd.getWorker().isRunning() ) {
                        getChildren().add( getURIDistanceComputationBox(cr.uri) );
                    }
                    backEnd.getWorker().runningProperty().addListener( (o, ov, isRunning) -> {
                        boolean hasLoadedSomething = !backEnd.getCrawler().getLoaded().isEmpty()
                                                     || !backEnd.getCrawler().getErrors().isEmpty();
                        if ( !isRunning && hasLoadedSomething ) {
                            setVisible(true);
                            getChildren().clear();
                            getChildren().add( getURIDistanceComputationBox(cr.uri) );
                        } else {
                            setVisible(false);
                            getChildren().clear();
                        }
                    });
                    boolean hasLoadedSomething = !backEnd.getCrawler().getLoaded().isEmpty()
                                                 || !backEnd.getCrawler().getErrors().isEmpty();
                    setVisible( !backEnd.getWorker().isRunning() && hasLoadedSomething );
                }
            };
        }
        /** Helper di #getDistanceCalculationField */
        private Node getURIDistanceComputationBox(URI uri) {
            // Crea un box che può contenere il valore della distanza
            // e un bottone per fermare il calcolo quando è in corso
            String distanceString = "Distanza da qui a questa pagina:  ";
            Text distanceLabel = new Text(distanceString);
            Button stopTaskButton = new Button("Stop");
            stopTaskButton.setVisible(false);
            Node valueAndStopBox = new HBox(distanceLabel, stopTaskButton);
            Node uriComboBox = new ComboBox<URI>() {
                Task<Map<URI, Integer>> task = null;
                {
                    stopTaskButton.setOnAction( (e) -> task.cancel() );
                    setPromptText("Scrivi l'URI o selezionalo");
                    setEditable(true);
                    // Permette di selezionare un uri interno al dominio
                    getItems().addAll(
                            Stream.concat( backEnd.getCrawler().getLoaded().stream(),
                                           backEnd.getCrawler().getErrors().stream() )
                                    .filter( (u) -> SiteCrawler.checkSeed(backEnd.getDomain(), u) )
                                    .sorted()
                                    .toArray(URI[]::new) );
                    setOnAction((e) -> {
                        // Calcola la distanza dall'uri selezionato
                        try {
                            String targetURIString = getEditor().getText();
                            URI targetURI = new URI(targetURIString);
                            boolean hasBeenLoaded = backEnd.getCrawler().getLoaded().contains(targetURI)
                                                    || backEnd.getCrawler().getErrors().contains(targetURI);
                            if ( !hasBeenLoaded || !SiteCrawler.checkSeed(backEnd.getDomain(), targetURI) ) {
                                throw new IllegalArgumentException( "L'uri selezionato non appartiene al dominio" +
                                                                                    "o non è stato ancora esplorato");
                            }
                            Map<URI, Integer> distanceMap = distances.getMapOrNull(uri);
                            // Se la mappa delle distanze non è ancora stata calcolata
                            // la calcola e mostra la distanza dall'uri selezionato
                            if (distanceMap == null) {
                                if (task != null) {
                                    task.cancel();
                                }
                                task = distances.newDistanceTask(uri);
                                task.stateProperty().addListener( (o, ov, nv) -> {
                                    if (nv == Worker.State.RUNNING) {
                                        distanceLabel.setText("calcolo mappa delle distanze...");
                                        stopTaskButton.setVisible(true);
                                    } else if (nv == Worker.State.SUCCEEDED) {
                                        stopTaskButton.setVisible(false);
                                        Map<URI, Integer> map = task.getValue();
                                        Integer dist = map.get(targetURI);
                                        String value = dist == null ? "non raggiungibile" : dist.toString();
                                        distanceLabel.setText( distanceString + value );
                                    } else if (nv == Worker.State.CANCELLED) {
                                        stopTaskButton.setVisible(false);
                                        getEditor().setText(null);
                                        distanceLabel.setText(distanceString);
                                    } else if (nv == Worker.State.FAILED) {
                                        stopTaskButton.setVisible(false);
                                        Throwable exc = task.getException();
                                        exc.printStackTrace();
                                        distanceLabel.setText(exc.toString());
                                    }
                                });
                                Thread th = new Thread(task);
                                th.setDaemon(true);
                                th.start();
                            } else {
                                // Se è già stata calcolata la mappa tra le distanze
                                // mostra la distanza dall'uri selezionato
                                Integer dist = distanceMap.get(targetURI);
                                String value = dist == null ? "non raggiungibile" : dist.toString();
                                distanceLabel.setText( distanceString + value );
                            }

                        } catch (URISyntaxException | IllegalArgumentException exc) {
                            distanceLabel.setText("avete scritto un indirizzo invalido");
                        }
                    });
                }
            };
            return new VBox() {
                {
                    setSpacing(5);
                    setAlignment(Pos.CENTER_LEFT);
                    getChildren().addAll(uriComboBox, valueAndStopBox);
                }
            };
        }
        /** @return un node che permette, riscaricando una pagina, di calcolare quanti nodi e quante immagini sono
         * presenti */
        private Node getExtraInfoBox(CrawlerResult cr) {
            Extras.ExtraInfo extraInfo = extras.getExtraInfoOrNull(cr.uri);
            if (extraInfo != null) {
                return getExtraInfos(extraInfo);
            } else {
                return new VBox() {
                    {
                        Node downloadFailed = new Label("Lo scaricamento è fallito :(");// viene visualizato solo se il download fallisce
                        Button downloadButton = new Button("scarica altre informazioni");
                        downloadButton.setOnAction((e) -> {
                            getChildren().remove(downloadFailed);
                            getChildren().remove(downloadButton);
                            Label labelDownloadInProgress = new Label("scaricamento in corso...");
                            getChildren().add(labelDownloadInProgress);
                            Task<Extras.ExtraInfo> task = extras.newExtraInfoTask(cr.uri);
                            task.stateProperty().addListener((o, ov, nv) -> {
                                Extras.ExtraInfo info = task.getValue();
                                if (nv == Worker.State.SUCCEEDED && info != null) {
                                    getChildren().remove(labelDownloadInProgress);
                                    getChildren().add(getExtraInfos(info));
                                } else if ( nv == Worker.State.FAILED
                                            || nv == Worker.State.SUCCEEDED && info == null)
                                {
                                    getChildren().remove(labelDownloadInProgress);
                                    getChildren().add(downloadFailed);
                                    getChildren().add(downloadButton);
                                } else if (nv == Worker.State.CANCELLED) {// Non accadrà mai, ma non si sa mai
                                    getChildren().add(downloadButton);
                                }
                            });
                            Thread thread = new Thread(task);
                            thread.setDaemon(true);
                            thread.start();
                        });
                        getChildren().add(downloadButton);
                    }
                };
            }
        }
        /** Helper di getExtraInfoBox */
        private Node getExtraInfos(Extras.ExtraInfo extraInfo){
            return new VBox() {
                {
                    getStyleClass().add("information-box");
                    Text numberOfNodes = new Text("Numero di nodi: " + extraInfo.numberOfNodes);
                    Text numberOfImages = new Text("Numero di immagini: " + extraInfo.numberOfImages);
                    getChildren().addAll(numberOfNodes, numberOfImages);
                }
            };
        }
    }

    /* Instance Fields */
    private byte debug = 0;

    private final BackEnd backEnd;

    private final ObjectProperty<CrawlerResult> showingResultProperty = new SimpleObjectProperty<>();
    private final BooleanProperty viewVisibleProperry = new SimpleBooleanProperty();

    private final ObjectProperty<CrawlerResult> maxLinkResultProperty = new SimpleObjectProperty<>(
                                                                             new CrawlerResult( null,
                                                                                                false,
                                                                                                new ArrayList<>(),
                                                                                                null,
                                                                                                null) );

    private final GeneralInfoNode generalInfoNode = new GeneralInfoNode();
    private final ResultNode resultNode = new ResultNode();

    private final Distances distances;
    private final Extras extras;
    private final Nodes nodes;
    private final Pointings pointings;

    private final VBox page = new VBox(){{setVgrow(this, Priority.ALWAYS);}};
    private final VBox node;

    /* Constructors */
    /** Costruisce l'oggetto e mostra immediatamente le informazioni
     * relative al dominio del BackEnd.
     * @param owner il BackEnd a cui appartiene questo InfoPane */
    public InfoPane(BackEnd owner) {
        backEnd = owner;
        distances = new Distances(backEnd);
        extras = new Extras();
        nodes = new Nodes(backEnd, this);
        pointings = new Pointings(backEnd);

        HBox addressBar = new HBox(
                new Button() {
                    {
                        setText("Home");
                        setOnAction( (e) -> showSiteGeneralInfo() );
                    }
                },
                new TextField() {
                    {
                        HBox.setHgrow(this, Priority.ALWAYS);
                        setOnAction( (e) -> {
                            String text = getText();
                            if ( text.equals("domain") ) {
                                showSiteGeneralInfo();
                            } else {
                                try {
                                    URI uri = new URI(text);
                                    CrawlerResult result = backEnd.getCrawler().get(uri);// can throw IllegalArgumentException
                                    showResultInfo(result);
                                } catch (URISyntaxException | IllegalArgumentException exc) {
                                    new Alert( Alert.AlertType.ERROR, "indirizzo invalido" ).showAndWait();
                                }
                            }
                        });
                        showingResultProperty.addListener( (o, ov, nv) -> {
                            String text = nv == null ? "domain" : nv.uri.toString();
                            setText(text);
                        });
                    }
                },
                new Button() {
                    {
                        managedProperty().bind(visibleProperty());
                        visibleProperty().bind( Bindings.createBooleanBinding(
                                () -> showingResultProperty.getValue() != null,
                                showingResultProperty                          ));
                        textProperty().bind( Bindings.createStringBinding(
                                () -> {
                                    boolean isShowingView = viewVisibleProperry.getValue();
                                    return isShowingView ? "info" : "view";
                                },
                                viewVisibleProperry                                       )   );
                        setOnAction( (e) -> {
                            boolean isShowingView = viewVisibleProperry.getValue();
                            CrawlerResult showingResult = showingResultProperty.getValue();
                            if (isShowingView) {
                                showResultInfo(showingResult);
                            } else {
                                showView(showingResult);
                            }
                        });
                    }
        }
        );
        addressBar.getStyleClass().add("toolbar");
        addressBar.setStyle("-fx-background-color: #989898");
        node = new VBox(addressBar, page);

        synchronized ( backEnd.resultObservableList() ) {
            // Calcola il Risultato che ha più link di tutti, se è presente
            Optional<CrawlerResult> optional = backEnd.resultObservableList().stream()
                    .filter( (cr) -> cr.links != null && cr.exc == null )
                    .max( (o1, o2) -> Integer.compare(o1.links.size(), o2.links.size()) );
            if (optional.isPresent()) {
                maxLinkResultProperty.setValue(optional.get());
            }
            // Listener che mantiene aggiornati tutti i valori
            backEnd.resultObservableList().addListener( (ListChangeListener.Change<? extends CrawlerResult> c) -> {
                while ( c.next() ) {
                    c.getAddedSubList().forEach( (cr) -> {
                        if (cr.links != null) {
                            // Mantiene aggiornato il risultato che ha più link di tutti
                            if ( cr.links.size() > maxLinkResultProperty.getValue().links.size() ) {
                                maxLinkResultProperty.setValue(cr);
                            }
                        }
                    });
                }
            });
        }
        // Mostra le informazioni generali sul dominio
        showSiteGeneralInfo();
    }

    /* Instance Methods */
    /** Mostra sul pannello le informazioni generali relative al dominio che si sta esplorando */
    public void showSiteGeneralInfo() {
        page.getChildren().clear();
        page.getChildren().add( generalInfoNode.get() );
        showingResultProperty.setValue(null);
    }

    /** Mostra sul pannello le informazioni specifiche relative ad una singola pagina
     * del dominio che è stata esplorata
     * @param cr il CrawlerResult che rappresenta la pagina di cui si vogliono mostrare le informazioni*/
    public void showResultInfo(CrawlerResult cr) {
        page.getChildren().clear();
        page.getChildren().add(resultNode.get(cr));
        viewVisibleProperry.setValue(false);
        showingResultProperty.setValue(cr);
    }

    /** Mostra sul pannello la visualizzazione di una pagina del dominio che è stata esplorata
     * @param cr il CrawlerResult che rappresenta la pagina che si vuole visualizzare */
    public void showView(CrawlerResult cr) {
        WebView webView = new WebView();
        webView.getEngine().load(cr.uri.toString());
        VBox.setVgrow(webView, Priority.ALWAYS);
        page.getChildren().clear();
        page.getChildren().add(webView);
        viewVisibleProperry.setValue(true);
        showingResultProperty.setValue(cr);
    }

    /** Ritorna il Node che visualizza il pannello
     * @return il Node che visualizza il pannello */
    public Parent getNode() {
        return node;
    }
}
