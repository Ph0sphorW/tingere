package org.icarus.tingere.parser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.bukkit.Material;

import java.io.IOException;

public class MaterialDeserializer extends JsonDeserializer<Material> {

    @Override
    public Material deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return (Material) context.handleWeirdStringValue(Material.class, raw, "material name must not be blank");
        }
        Material material = Material.matchMaterial(raw.trim());
        if (material == null) {
            return (Material) context.handleWeirdStringValue(Material.class, raw, "unknown material");
        }
        return material;
    }
}
