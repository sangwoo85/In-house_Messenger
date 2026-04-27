package com.company.messenger.domain.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonTestUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonTestUtils() {
    }

    public static String readJson(String json, String jsonPath) throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        String[] paths = jsonPath.replace("$.", "").split("\\.");
        for (String path : paths) {
            node = node.get(path);
        }
        return node.asText();
    }
}

