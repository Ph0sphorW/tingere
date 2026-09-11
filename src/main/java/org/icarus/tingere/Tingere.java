package org.icarus.tingere;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.icarus.tingere.command.TingereCommandExecutor;
import org.icarus.tingere.config.RecipeLoader;
import org.icarus.tingere.listener.CraftListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Tingere extends JavaPlugin {

    @Getter private final RecipeLoader recipeLoader = new RecipeLoader(this);
    private final Logger logger = this.getLogger();

    @Override
    public void onEnable() {
        logger.info("Tingere - Custom recipe loader");
        logger.info("Made by Ph0sphorW & Annieawa");
        var command = getCommand("tingere");
        if (command != null) {
            command.setExecutor(new TingereCommandExecutor(this));
        }
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        reloadRecipes();
    }

    public void reloadRecipes() {
        try {
            long start = System.currentTimeMillis();

            recipeLoader.removeAllPluginRecipes();
            int count = recipeLoader.loadAllRecipes();

            long time = System.currentTimeMillis() - start;
            logger.info("Successfully reloaded " + count + " recipes in " + time + "ms");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An error occurred while trying to reload the recipes:", e);
        }
    }

    public static Tingere getInstance() {
        return getPlugin(Tingere.class);
    }
}
