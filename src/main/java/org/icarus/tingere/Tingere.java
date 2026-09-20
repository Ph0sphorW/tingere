package org.icarus.tingere;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.icarus.tingere.command.TingereCommandManager;
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
        new TingereCommandManager(this).register();
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        reloadRecipes();
    }

    public void reloadRecipes() {
        try {
            long start = System.currentTimeMillis();
            int count = recipeLoader.reloadAll();
            long time = System.currentTimeMillis() - start;
            logger.info("Successfully reloaded " + count + " recipes in " + time + "ms");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An error occurred while trying to reload the recipes:", e);
        }
    }
}
