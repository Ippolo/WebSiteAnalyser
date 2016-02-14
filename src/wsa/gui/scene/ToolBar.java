package wsa.gui.scene;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import wsa.gui.BackEnd;
import wsa.gui.util.Nodes;

/**
 * Rappresenta una tool bar che permette di effettuare operazioni specifiche su un dominio della WSA, come
 * aggiungere un URI seed, iniziare o fermare l'esplorazione. Permette in oltre di aggiungere rapidamente un
 * nuovo dominio alla WSA.
 */
public class ToolBar {
    /* Instance Fields */
    private final BackEnd backEnd;

    private final Node node;

    private byte debug = 0;

    /* Constructors */
    /** Metodo costruttore
     * @param owner il {@link wsa.gui.BackEnd} a cui corrisponde il dominio a cui si riferirà questo oggetto*/
    public ToolBar(BackEnd owner) {
        backEnd = owner;
        node = new HBox() {
            {
                getStyleClass().add("toolbar");
                Nodes nodes = new Nodes(backEnd, null);
                Node newSiteButton = new Button() {
                    {
                        setText("add");
                        setOnAction((e) -> backEnd.getFrame().newDialog());
                    }
                };
                Node restoreButton = new Button() {
                    {
                        setText("restore");
                        setOnAction(e -> backEnd.getFrame().restoreDialog());
                    }
                };
                getChildren().addAll( nodes.getAddSeedButton(debug),
                                      nodes.getStartOrStopButton(),
                                      nodes.getCancelButton(),
                                      Nodes.getSplitPane(),//split
                                      newSiteButton,
                                      restoreButton
                );
            }
        };
    }

    /*Instance Method */
    /** Ritorna il Node che visualizza la tool bar
     * @return il Node che visualizza la tool bar */
    public Node getNode() {
        return node;
    }
}
