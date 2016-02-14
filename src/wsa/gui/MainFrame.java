package wsa.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Rappresenta la finestra principale del WebSiteAnalyser. Racchiude i {@link wsa.gui.FrontEnd}
 * delle varie esplorazioni in corso e li mostra in un TabPane. Fornisce inoltre un metodo per accedere
 * ai {@link wsa.gui.BackEnd} delle esplorazioni*/
public class MainFrame {
    /* Instance Fields */
    private byte debug = 0;

    private final ObservableList<BackEnd> backEndList = FXCollections.observableArrayList();

    private final TabPane tabPane = new TabPane() {
        {
            getStyleClass().addAll("site-tab-pane", TabPane.STYLE_CLASS_FLOATING);
            //setTabClosingPolicy(TabClosingPolicy.UNAVAILABLE);
        }
    };

    /* Constructors */
    /** Metodo costruttore */
    public MainFrame() {
        Node addButton = new Button() {
            {
                setText("add");
                setOnAction(e -> newDialog());
            }
        };
        Node restoreButton = new Button() {
            {
                setText("restore");
                setOnAction(e -> restoreDialog());
            }
        };
        Node toolBar = new HBox(addButton, restoreButton);
        Tab tab = new Tab("settings", toolBar);
        tab.setClosable(false);// la tab delle impostazioni non può essere chiusa
        tabPane.getTabs().add(tab);
    }


    /* Instance Methods */
    /** Quando si chiama questo metodo viene aperto un Dialog che
     * permette di iniziare l'esplorazione di un nuovo sito */
    public void newDialog() {
        // Rappresenta i parametri per la costruzione di un BackEnd
        class Params {
            final URI domain;
            final Path directory;
            Params(URI dom, Path dir) {
                domain = dom;
                directory = dir;
            }
        }
        // Un Dialog che chiede all'utente i dati per costruire un nuovo BackEnd
        Dialog<Params> siteDialog = new Dialog<Params>() {
            private final CheckBox doArchiveCheckbox = new CheckBox() {
                {
                    setText("Archivia esplorazione");
                    setAllowIndeterminate(false);
                    setSelected(true);
                }
            };
            private final TextField domainField = new TextField() {
                {
                    setPromptText("inserisci uri");
                }
            };
            private final TextField dirField = new TextField() {
                {
                    setPromptText("inserisci directory");
                }
            };
            { // Dialog Initializer
                setHeaderText("Aggiungi un nuovo dominio");
                Node content = new VBox() {
                    {
                        setSpacing(5);
                        Node dirButton = new Button() {
                            { // Bottone che aiuta a selezionare la directory di archiviazione
                                setText("Esplora");
                                setOnAction( (e) -> {
                                    DirectoryChooser directoryChooser = new DirectoryChooser();
                                    File dir = directoryChooser.showDialog( getScene().getWindow() );
                                    dirField.setText( dir.toString() );
                                });
                            }
                        };
                        Node dirBox = new HBox() {
                            {
                                HBox.setHgrow(dirField, Priority.ALWAYS);
                                getChildren().addAll(dirField, dirButton);
                                visibleProperty().bind(doArchiveCheckbox.selectedProperty());
                            }
                        };
                        getChildren().addAll(doArchiveCheckbox, domainField, dirBox);
                    }
                };
                final DialogPane dialogPane = getDialogPane();
                dialogPane.setContent(content);
                dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
                // Ricava i parametri per costruire un nuovo BackEnd in base agli input dell'utente
                setResultConverter( (bt) -> {
                    Params params = null;
                    if (bt == ButtonType.OK) {
                        try {
                            URI domain = new URI( domainField.getText() );
                            boolean doArchive = doArchiveCheckbox.isSelected();
                            Path dir = doArchive ? Paths.get( dirField.getText() ) : null;
                            params = new Params(domain, dir);
                        } catch (URISyntaxException | InvalidPathException exc) {
                            Alert alert = new Alert( Alert.AlertType.ERROR, exc.toString() );
                            alert.showAndWait();
                        }
                    }
                    return params;
                });

            }
        };
        Optional<Params> optional = siteDialog.showAndWait();
        try {
            Params params = optional.get();
            BackEnd be = new BackEnd(params.domain, params.directory, this);
            backEndList.add(be);
            addSite(be);
        } catch (NoSuchElementException exc) {
            if( debug > 0 ) {
                System.out.println("Dialog per nuovo dominio cancellato");
            }
        } catch (IllegalArgumentException | IOException exc) {
            Alert alert = new Alert( Alert.AlertType.ERROR, exc.toString() );
            alert.showAndWait();
        }
    }

    /** Quando si chiama questo metodo viene aperto un dialog che permette di
     * ripristinare un'esplorazione già cominciata e archiviata in una cartella */
    public void restoreDialog() {
        Dialog<Path> siteDialog = new Dialog<Path>() {
            private final TextField dirField = new TextField() {
                {
                    setPromptText("inserisci directory");
                }
            };
            { // Dialog Initializer
                setHeaderText("Ripristina una precedente esplorazione");
                Node content = new VBox() {
                    {
                        setSpacing(5);
                        Node dirButton = new Button() {
                            { // Bottone che aiuta a selezionare la directory di archiviazione
                                setText("Esplora");
                                setOnAction( (e) -> {
                                    DirectoryChooser directoryChooser = new DirectoryChooser();
                                    File dir = directoryChooser.showDialog( getScene().getWindow() );
                                    if (dir != null) {
                                        dirField.setText(dir.toString());
                                    }
                                });
                            }
                        };
                        Node dirBox = new HBox() {
                            {
                                HBox.setHgrow(dirField, Priority.ALWAYS);
                                getChildren().addAll(dirField, dirButton);
                            }
                        };
                        getChildren().add(dirBox);
                    }
                };
                final DialogPane dialogPane = getDialogPane();
                dialogPane.setContent(content);
                dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
                // Ricava i parametri per costruire un nuovo BackEnd in base agli input dell'utente
                setResultConverter( (bt) -> {
                    Path path = null;
                    if (bt == ButtonType.OK) {
                        try {
                            path = Paths.get( dirField.getText() );
                        } catch (InvalidPathException exc) {
                            Alert alert = new Alert( Alert.AlertType.ERROR, exc.toString() );
                            alert.showAndWait();
                        }
                    }
                    return path;
                });
            }
        };
        Optional<Path> optional = siteDialog.showAndWait();
        try {
            Path dir = optional.get();
            BackEnd be = new BackEnd(null, dir, this);
            backEndList.add(be);
            addSite(be);
        } catch (NoSuchElementException exc) {
            if( debug > 0 ) {
                System.out.println("Dialog per nuovo dominio cancellato");
            }
        } catch (IllegalArgumentException | IOException exc) {
            new Alert( Alert.AlertType.ERROR, exc.toString() ).showAndWait();
        }

    }

    /** Ritorna il Parent che visualizza la finestra principale
     * @return  il Parent che visualizza la finestra principale*/
    public Parent getNode() {
        return tabPane;
    }

    /** Ritorna la ObservableList dei BackEnd delle esplorazioni in corso
     * @return  la ObservableList dei BackEnd delle esplorazioni in corso */
    public ObservableList<BackEnd> getSites() {
        return backEndList;
    }

    /** Aggiunge una nuova esplorazione */
    private void addSite(BackEnd be) {
        String domainString = be.getDomain().toString();
        String tabTitle = domainString;
        if (tabTitle.length() > 20) {
            tabTitle = tabTitle.substring(0, 19) + "...";
        }
        Node fe = new FrontEnd(be);
        Tab tab = new Tab(tabTitle, fe);
        tab.setTooltip( new Tooltip(domainString) );
        tab.setOnCloseRequest( (e) -> {
            Alert alert = new Alert( Alert.AlertType.CONFIRMATION,
                                     "Stai cancellando definitivamente l'esplorazione del dominio:\n" + domainString
                                     + "\n<se hai impostato l'archiviazione rimarrà una copia sul disco");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                be.cancel();
            } else {
                e.consume();
            }
        });
        tabPane.getTabs().add(tab);
    }
}

