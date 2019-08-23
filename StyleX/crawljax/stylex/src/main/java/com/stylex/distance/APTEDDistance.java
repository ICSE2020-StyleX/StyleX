package com.stylex.distance;

import costmodel.CostModel;
import costmodel.StringUnitCostModel;
import distance.APTED;
import node.StringNodeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class APTEDDistance implements Distance {

    private static final Logger LOGGER = LoggerFactory.getLogger(APTEDDistance.class);

    private final APTED<? extends CostModel<StringNodeData>, StringNodeData> apted;

    public APTEDDistance() {
        apted = new APTED<>(new StringUnitCostModel());
    }

    @Override
    public float getDOMDistance(Document document1, Document document2) {
        node.Node<StringNodeData> t1 = getAPTEDTreeFromDocument(document1);
        node.Node<StringNodeData> t2 = getAPTEDTreeFromDocument(document2);
        LOGGER.info("Started computing tree edit distance");
        float distance = apted.computeEditDistance(t1, t2);
        LOGGER.info("Finished computing tree edit distance");
        return distance;
    }

    private node.Node<StringNodeData> getAPTEDTreeFromDocument(Node node) {
        node.Node<StringNodeData> root = new node.Node<>(new StringNodeData(getNodeStringRepresentation(node)));
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            root.addChild(getAPTEDTreeFromDocument(item));
        }
        return root;
    }

    private String getNodeStringRepresentation(Node node) {
        /*
         * The APTED algorithm works on labeled trees.
         * The question is, what is a good label for a node?
         * For the moment, we just keep the node name,
         * and assume that the algorithm is robust enough.
         * This can be changed later is we find the assumption incorrect
         */
		/*if (node instanceof TextImpl) {
			TextImpl textImpl = (TextImpl) node;
			return textImpl.getWholeText().replace("{", " <$< ").replace("}", " >$> ");
		} else {*/
        return node.getNodeName();
        /*}*/
    }
}
