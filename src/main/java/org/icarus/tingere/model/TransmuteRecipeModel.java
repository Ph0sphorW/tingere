package org.icarus.tingere.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.TransmuteRecipe;
import org.icarus.tingere.Tingere;

@RequiredArgsConstructor
public class TransmuteRecipeModel implements CustomRecipeInterface {

    @Getter
    private final String key;
    private final ItemStack mainItemStack;
    private final ItemStack materialItemStack;
    private final ItemStack result;

    @Override
    public Recipe toBukkitRecipe() {
        NamespacedKey namespacedKey = new NamespacedKey(Tingere.getInstance(), key);
        return new TransmuteRecipe(
                namespacedKey,
                result.getType(),
                new RecipeChoice.MaterialChoice(mainItemStack.getType()),
                new RecipeChoice.ExactChoice(materialItemStack)
        );
    }
}
