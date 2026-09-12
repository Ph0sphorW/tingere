package org.icarus.tingere.recipe;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.parser.ComponentParser;
import org.jetbrains.annotations.NotNull;

public record Ingredient(@NotNull Material material,
                         Integer amount,
                         String matchMode,
                         JsonNode components) {

    public static final int DEFAULT_AMOUNT = 1;

    public Ingredient {
        if (material.isAir()) {
            throw new IllegalArgumentException("'material' must not be air");
        }
    }

    public int amountOrDefault() {
        return amount == null ? DEFAULT_AMOUNT : amount;
    }

    public boolean matchesExactly() {
        return !"material".equalsIgnoreCase(matchMode);
    }

    public ItemStack toItemStack(Tingere plugin, String keyPrefix) {
        return ComponentParser.apply(plugin, new ItemStack(material, amountOrDefault()), components, keyPrefix);
    }
}
