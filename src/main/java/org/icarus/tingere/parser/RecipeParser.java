package org.icarus.tingere.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.bukkit.Material;
import org.icarus.tingere.recipe.RecipeDefinition;
import org.icarus.tingere.recipe.ShapedRecipeDefinition;
import org.icarus.tingere.recipe.ShapelessRecipeDefinition;
import org.icarus.tingere.recipe.TransmuteRecipeDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class RecipeParser {

    private final ObjectMapper mapper;

    public RecipeParser() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Material.class, new MaterialDeserializer());

        this.mapper = YAMLMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
                .addModule(module)
                .build();
    }

    public JsonNode read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            JsonNode root = mapper.readTree(input);
            return root == null ? mapper.getNodeFactory().missingNode() : root;
        }
    }

    public RecipeDefinition bind(JsonNode node) throws IOException {
        if (!node.isObject()) {
            throw new IllegalArgumentException("a recipe must be a mapping, got " + node.getNodeType());
        }

        JsonNode typeNode = node.get("type");
        if (typeNode == null || typeNode.isNull()) {
            throw new IllegalArgumentException("missing required field 'type'");
        }

        String type = typeNode.asText();
        Class<? extends RecipeDefinition> target = switch (type.toLowerCase(Locale.ROOT)) {
            case "shaped" -> ShapedRecipeDefinition.class;
            case "shapeless" -> ShapelessRecipeDefinition.class;
            case "transmute" -> TransmuteRecipeDefinition.class;
            default -> throw new IllegalArgumentException(
                    "unknown recipe type '" + type + "' (expected shaped, shapeless or transmute)");
        };

        ObjectNode payload = node.deepCopy();
        payload.remove("type");
        return mapper.treeToValue(payload, target);
    }

    public static String labelOf(JsonNode node, String fallback) {
        JsonNode key = node.get("key");
        return key == null || key.isNull() ? fallback : key.asText();
    }
}
