package org.icarus.tingere;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.icarus.tingere.command.RecipeCommand;
import org.icarus.tingere.config.RecipeLoader;
import org.icarus.tingere.listener.CraftListener;

import java.util.logging.Level;

@Getter
public final class Tingere extends JavaPlugin {

    @Getter
    private static Tingere instance;

    private RecipeLoader recipeLoader;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("配方插件已启动，正在加载自定义配方...");

        // 初始化配方加载器（配方来自插件数据目录下的 recipes 文件夹）
        this.recipeLoader = new RecipeLoader(this);

        // 注册命令
        var command = getCommand("tingere");
        if (command != null) {
            command.setExecutor(new RecipeCommand(this));
        }

        // 初次加载所有配方
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        reloadRecipes();
    }

    @Override
    public void onDisable() {
        if (recipeLoader != null) {
            recipeLoader.removeAllPluginRecipes();
        }
        getLogger().info("配方插件已卸载");
    }

    /**
     * 重载所有配方（先清理再加载）
     */
    public void reloadRecipes() {
        try {
            long start = System.currentTimeMillis();
            recipeLoader.removeAllPluginRecipes();  // 先移除所有本插件配方
            int count = recipeLoader.loadAllRecipes(); // 重新加载
            long time = System.currentTimeMillis() - start;
            getLogger().info("成功重载 " + count + " 个自定义配方，耗时 " + time + "ms");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "重载配方时发生错误", e);
        }
    }
}
