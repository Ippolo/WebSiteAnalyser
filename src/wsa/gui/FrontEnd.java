package wsa.gui;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import wsa.gui.scene.*;

/** Una classe che rappresenta l'interfaccia grafica che visualizza l'esplorazione di un dominio e con cui l'utente
 * può interagire per avviarla, metterla in pausa, aggiungere seed o visualizzare informazioni relativa ad una pagina
 * esplorata o al dominio in generale */
public class FrontEnd extends VBox {
    public FrontEnd(BackEnd backEnd) {
        // Aggiunge una tool bar all'interfaccia
        Node toolBar = new ToolBar(backEnd).getNode();
        // Aggiunge un InfoPane
        InfoPane infoPane = new InfoPane(backEnd);
        // Aggiunge una lista che visualizza tutti gli uri esplorati
        PageList pageList = new PageList(backEnd, infoPane);
        SplitPane splitPane = new SplitPane(pageList.getNode(), infoPane.getNode());
        setVgrow(splitPane, Priority.ALWAYS);
        getChildren().addAll(toolBar, splitPane);
    }
}
