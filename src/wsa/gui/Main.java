package wsa.gui;

import java.io.IOException;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Una classe per lanciare l'esecuzione di un WebSiteAnalyser
 */
public class Main extends Application {
    public static void main(String... args){
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException{
        MainFrame mainFrame = new MainFrame();
        Scene scene = new Scene(mainFrame.getNode(),  800, 600);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
