package org.icarus.tingere.model;

import org.bukkit.inventory.Recipe;

public interface CustomRecipeInterface {
    Recipe toBukkitRecipe() throws IllegalArgumentException;
}
