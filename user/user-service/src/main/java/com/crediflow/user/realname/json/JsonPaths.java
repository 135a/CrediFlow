package com.crediflow.user.realname.json;

import com.fasterxml.jackson.databind.JsonNode;

public final class JsonPaths {

    private JsonPaths() {}

    public static JsonNode at(JsonNode root, String dottedPath) {
        if (root == null || dottedPath == null || dottedPath.isBlank()) {
            return null;
        }
        JsonNode cur = root;
        for (String seg : dottedPath.split("\\.")) {
            if (cur == null) {
                return null;
            }
            cur = cur.get(seg);
        }
        return cur;
    }

    public static boolean asBoolean(JsonNode n, boolean defaultValue) {
        if (n == null || n.isNull()) {
            return defaultValue;
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        if (n.isTextual()) {
            return Boolean.parseBoolean(n.asText());
        }
        if (n.isNumber()) {
            return n.intValue() != 0;
        }
        return defaultValue;
    }

    public static String asText(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText(null);
    }
}
