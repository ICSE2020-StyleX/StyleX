package com.stylex.rintegration;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class REngineUtil {

    public static <T> String getDataFrameDeclarationFromMap(Map<String, T> map) {
        StringBuilder builder = new StringBuilder();
        builder.append("data.frame(");
        for (Iterator<String> iterator = map.keySet().iterator(); iterator.hasNext(); ) {
            String key = iterator.next();
            T value = map.get(key);
            builder.append(key).append("=");
            if (value instanceof Collection<?>) {
                builder.append("c(");
                Collection<?> collection = (Collection<?>) value;
                for (Iterator<?> valueCollectionIterator = collection.iterator(); valueCollectionIterator.hasNext(); ) {
                    Object o = valueCollectionIterator.next();
                    if (o == null) {
                        builder.append("NA");
                    } else if (o instanceof Number) {
                        builder.append(o);
                    } else {
                        try {
                            float d = Float.parseFloat(o.toString());
                            builder.append(String.format("%f", d));
                        } catch (NumberFormatException nfe) {
                            builder.append("\"").append(o).append("\"");
                        }
                    }
                    if (valueCollectionIterator.hasNext()) {
                        builder.append(",");
                    }
                }
                builder.append(")"); // c(
            } else {
                builder.append(value);
            }

            if (iterator.hasNext()) {
                builder.append(",");
            }
        }
        builder.append(")"); // data.frame(
        return builder.toString();
    }

}
