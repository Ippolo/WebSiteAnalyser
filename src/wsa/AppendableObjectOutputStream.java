package wsa;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * Un ObjectOutputStream che serve ad aggiungere nuovi oggetti ad uno stream già esistente
 * NON DEVE ESSERE USATO PER CREARE NUOVI FILE, MA SOLO PER AGGIUNGERE ELEMENTI A FILE GIÀ ESISTENTI!
 * implementazione copiata da http://stackoverflow.com/questions/1194656/appending-to-an-objectoutputstream*/
public class AppendableObjectOutputStream extends ObjectOutputStream {

    /** Metodo costruttore */
    public AppendableObjectOutputStream(OutputStream out) throws IOException  {
        super(out);
    }

    @Override
    protected void writeStreamHeader() throws IOException {
        reset();
    }
}
