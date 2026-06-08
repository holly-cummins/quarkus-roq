package io.quarkus.tools.migration;

import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@TemplateExtension
public class JekyllFiltersExtension {

    /**
     * Jekyll's "where" filter: select items from an array where a property matches a value.
     * Usage in Qute: {myArray.where("key", "value")}
     */
    static JsonArray where(JsonArray array, String property, String value) {
        JsonArray result = new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            Object item = array.getValue(i);
            if (item instanceof JsonObject obj) {
                Object propValue = obj.getValue(property);
                if (propValue != null && propValue.toString().equals(value)) {
                    result.add(obj);
                }
            }
        }
        return result;
    }

    /**
     * Get the first element of a JsonArray.
     * Usage in Qute: {myArray.first}
     */
    static Object first(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        return array.getValue(0);
    }

    /**
     * Get the last element of a JsonArray.
     * Usage in Qute: {myArray.last}
     */
    static Object last(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        return array.getValue(array.size() - 1);
    }

    /**
     * Get the size of a JsonArray.
     * Usage in Qute: {myArray.size}
     */
    static int size(JsonArray array) {
        return array == null ? 0 : array.size();
    }
}
