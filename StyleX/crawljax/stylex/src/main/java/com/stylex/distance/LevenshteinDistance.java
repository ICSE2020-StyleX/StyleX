package com.stylex.distance;

import com.crawljax.util.DomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class LevenshteinDistance implements Distance {

    private static final Logger LOGGER = LoggerFactory.getLogger(LevenshteinDistance.class);

    private org.apache.commons.text.similarity.LevenshteinDistance levenshteinDistance =
            new org.apache.commons.text.similarity.LevenshteinDistance();

    @Override
    public float getDOMDistance(Document document1, Document document2) {
        LOGGER.info("Started computing string edit distance");
        String domString1 = DomUtils.getDocumentToString(document1);
        String domString2 = DomUtils.getDocumentToString(document2);
        Integer stringDistance = levenshteinDistance.apply(domString1, domString2);
        LOGGER.info("Finished computing string edit distance");
        return stringDistance;
    }
}
