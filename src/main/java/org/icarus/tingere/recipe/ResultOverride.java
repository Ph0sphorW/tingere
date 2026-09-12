package org.icarus.tingere.recipe;

import com.fasterxml.jackson.databind.JsonNode;

public record ResultOverride(JsonNode components, int amount) {
}
