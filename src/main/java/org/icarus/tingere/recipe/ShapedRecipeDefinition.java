package org.icarus.tingere.recipe;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.icarus.tingere.Tingere;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ShapedRecipeDefinition(@JsonProperty(required = true) String id,
                                     @JsonProperty(required = true) List<String> pattern,
                                     @JsonProperty(required = true) Map<Character, Ingredient> ingredients,
                                     @JsonProperty(required = true) Ingredient result,
                                     SpecialDefinition special) implements RecipeDefinition {

    private static final int MAX_SIZE = 3;

    public ShapedRecipeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing required field 'id'");
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("'pattern' must not be empty");
        }
        if (pattern.size() > MAX_SIZE) {
            throw new IllegalArgumentException("'pattern' must have at most " + MAX_SIZE + " rows");
        }
        int width = pattern.getFirst().length();
        if (width == 0 || width > MAX_SIZE) {
            throw new IllegalArgumentException("'pattern' rows must be 1-" + MAX_SIZE + " characters wide");
        }
        for (String row : pattern) {
            if (row.length() != width) {
                throw new IllegalArgumentException("'pattern' rows must all have the same width (" + width + ")");
            }
        }
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("'ingredients' must not be empty");
        }
        if (result == null) {
            throw new IllegalArgumentException("missing required field 'result'");
        }

        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                if (symbol != ' ' && !ingredients.containsKey(symbol)) {
                    throw new IllegalArgumentException("no ingredient defined for pattern character '" + symbol + "'");
                }
            }
        }
    }

    @Override
    public Recipe toBukkitRecipe(Tingere plugin) {
        ShapedRecipe recipe = new ShapedRecipe(
                new NamespacedKey(plugin, id), result.toItemStack(plugin, "result"));
        recipe.shape(pattern.toArray(String[]::new));

        ingredients.forEach((symbol, ingredient) -> recipe.setIngredient(symbol, toChoice(plugin, ingredient, symbol)));
        return recipe;
    }

    private static RecipeChoice toChoice(Tingere plugin, Ingredient ingredient, char symbol) {
        return ingredient.matchesExactly()
                ? new RecipeChoice.ExactChoice(ingredient.toItemStack(plugin, "ingredient_" + symbol))
                : new RecipeChoice.MaterialChoice(ingredient.material());
    }

    @Override
    public List<Ingredient> flattenedIngredients() {
        List<Ingredient> flattened = new ArrayList<>();
        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                if (symbol == ' ') {
                    continue;
                }
                Ingredient ingredient = ingredients.get(symbol);
                if (ingredient != null) {
                    flattened.add(ingredient);
                }
            }
        }
        return flattened;
    }
}
