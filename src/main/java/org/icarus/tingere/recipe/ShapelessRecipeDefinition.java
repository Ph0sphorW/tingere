package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.icarus.tingere.Tingere;

import java.util.List;

public record ShapelessRecipeDefinition(@JsonProperty(required = true) String id,
                                        @JsonProperty(required = true) List<Ingredient> ingredients,
                                        @JsonProperty(required = true) Ingredient result,
                                        SpecialDefinition special) implements RecipeDefinition {

    public ShapelessRecipeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing required field 'id'");
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("'ingredients' must not be empty");
        }
        if (result == null) {
            throw new IllegalArgumentException("missing required field 'result'");
        }
    }

    @Override
    public Recipe toBukkitRecipe(Tingere plugin) {
        ShapelessRecipe recipe = new ShapelessRecipe(
                new NamespacedKey(plugin, id), result.toItemStack(plugin, "result"));

        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            recipe.addIngredient(ingredient.matchesExactly()
                    ? new RecipeChoice.ExactChoice(ingredient.toItemStack(plugin, "ingredient_" + i))
                    : new RecipeChoice.MaterialChoice(ingredient.material()));
        }
        return recipe;
    }

    @Override
    public List<Ingredient> flattenedIngredients() {
        return ingredients;
    }
}
