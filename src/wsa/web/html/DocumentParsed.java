package wsa.web.html;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.function.Consumer;

/**
 * Rappresenta l'albero di analisi sintattica (parsing) di una pagina web
 */
class DocumentParsed implements Parsed{
    /**Crea l'albero di parsing di una pagina web a partire da un Document
     * @param doc  il document che rappresenta la pagina web*/
    DocumentParsed(Document doc){
        Set<Node> nodeSet = new HashSet<>();
        fillNodeSet(nodeSet, doc);
        this.nodeSet = nodeSet;
    }
    /** Esegue la visita dell'intero albero di parsing
     * @param visitor  visitatore invocato su ogni nodo dell'albero */
    @Override
    public void visit (Consumer< Node > visitor) {
        nodeSet.stream().forEach(visitor::accept);
    }
    /** Ritorna la lista (possibilmente vuota) dei links contenuti nella pagina
     * @return la lista dei links (mai null) */
    @Override
    public List<String> getLinks () {
        List<String> linkList = new ArrayList<>();
        nodeSet.stream().filter(node -> node.tag != null && node.tag.equals("A"))
                        .forEach(node -> linkList.add(node.attr.get("href")));
        return linkList;
    }
    /** Ritorna la lista (possibilmente vuota) dei nodi con lo specificato tag
     * @param tag  un nome di tag
     * @return la lista dei nodi con il dato tag (mai null) */
    @Override
    public List<Node> getByTag (String tag){
        List<Node> nodeList = new ArrayList<>();
        nodeSet.stream().filter(node -> node.tag != null && node.tag.equals(tag))
                        .forEach(nodeList::add);
        return nodeList;
    }
    /** riempie il nodeSet con l'insieme dei nodi di t
     * @param nodeSet  insieme in cui verranno inseriti i nodi di t
     * @param t la radice di un albero albero org.w3c.dom.Node*/
    private static void fillNodeSet(Set<Node> nodeSet, org.w3c.dom.Node t) {
        String tag = null;
        String content = null;
        Map<String, String> attributes = null;
        if (t.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            tag = t.getNodeName();
            attributes = new HashMap<>();
            NamedNodeMap nodeMap = t.getAttributes();
            for (int i = 0; i < nodeMap.getLength(); i++) {
                org.w3c.dom.Node attrNode = nodeMap.item(i);
                String key = attrNode.getNodeName();
                String value = attrNode.getNodeValue();
                attributes.put(key, value);
            }
        } else if (t.getNodeType() == org.w3c.dom.Node.TEXT_NODE) content = t.getNodeValue();
        nodeSet.add(new Parsed.Node(tag, attributes, content));
        NodeList list = t.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            org.w3c.dom.Node n = list.item(i);
            fillNodeSet(nodeSet, n);
        }
    }

    private final Set<Node> nodeSet;
}