package com.mgaray.oktaapp.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> parse(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> convertToMap(Object object) {
        return objectMapper.convertValue(object, new TypeReference<Map<String, Object>>() {});
    }

    public static <T> T convertToPojo(Map<String, Object> map, Class<T> clazz) {
        return objectMapper.convertValue(map, clazz);
    }

    public static Map<String, Object> getNestedMap(Map<String, Object> objectMap, String... path) {
        Map<String, Object> nestedMap = getNestedField(objectMap, path);
        return (nestedMap != null) ? nestedMap : Map.of();
    }

    public static <T> T getNestedField(String jsonString, String... path) {
        Map<String, Object> objectMap = parse(jsonString);
        return getNestedField(objectMap, path);
    }

    @SuppressWarnings("unchecked")
    public static <T>  T getNestedField(Map<String, Object> objectMap, String... path) {
        try {
            for (int i = 0; i < path.length - 1; i++) {
                objectMap = (Map<String, Object>) objectMap.get(path[i]);
            }
            return (T) objectMap.get(path[path.length - 1]);
        } catch (ClassCastException | NullPointerException e) { //recall, type cast to (T) would not be caught here
            return null; //key not available on objectMap
        }
    }

    @SuppressWarnings("unchecked")
    public static <T>  T getNestedField(Map<String, Object> objectMap, Class<T> type, String... path) {
        try {
            for (int i = 0; i < path.length - 1; i++) {
                objectMap = (Map<String, Object>) objectMap.get(path[i]);
            }
            return type.cast(objectMap.get(path[path.length - 1]));
        } catch (ClassCastException | NullPointerException e) {
            return null; //key not available on objectMap
        }
    }



}
