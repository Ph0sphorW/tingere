package org.icarus.tingere.nms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.storage.WorldData;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftBlastingRecipe;
import org.bukkit.craftbukkit.inventory.CraftFurnaceRecipe;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapedRecipe;
import org.bukkit.craftbukkit.inventory.CraftShapelessRecipe;
import org.bukkit.craftbukkit.inventory.CraftSmithingTransformRecipe;
import org.bukkit.craftbukkit.inventory.CraftSmokingRecipe;
import org.bukkit.craftbukkit.inventory.CraftStonecuttingRecipe;
import org.bukkit.craftbukkit.inventory.CraftTransmuteRecipe;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.icarus.tingere.Tingere;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RecipeTransaction implements AutoCloseable {

    private static final String FEATURE_FLAGS_FIELD = "featureflagset";

    private static Field featureFlagsField;

    private static boolean degradeReported;

    private final Session session;

    private RecipeTransaction(Session session) {
        this.session = session;
    }

    public static RecipeTransaction begin(Tingere plugin) {
        if (!Bukkit.isPrimaryThread()) {
            return new RecipeTransaction(null);
        }
        try {
            return new RecipeTransaction(new Session());
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportDegrade(plugin.getLogger(), e);
            return new RecipeTransaction(null);
        }
    }

    public boolean add(Recipe recipe) {
        return this.session == null ? Bukkit.addRecipe(recipe) : this.session.add(recipe);
    }

    public boolean remove(NamespacedKey key) {
        return this.session == null ? Bukkit.removeRecipe(key) : this.session.remove(key);
    }

    @Override
    public void close() {
        if (this.session != null) {
            this.session.close();
        }
    }

    private static void reportDegrade(Logger logger, Exception cause) {
        if (degradeReported) {
            return;
        }
        degradeReported = true;
        logger.log(Level.WARNING, "Batched recipe sync is unavailable on this server build, "
                + "falling back to one client update per recipe: " + cause, cause);
    }

    private static void setFeatureFlags(RecipeManager manager, FeatureFlagSet value)
            throws ReflectiveOperationException {
        Field field = featureFlagsField;
        if (field == null) {
            field = RecipeManager.class.getDeclaredField(FEATURE_FLAGS_FIELD);
            field.setAccessible(true);
            featureFlagsField = field;
        }
        field.set(manager, value);
    }

    private static CraftRecipe asCraftRecipe(Recipe recipe) {
        if (recipe instanceof CraftRecipe craft) {
            return craft;
        }
        return switch (recipe) {
            case ShapedRecipe r -> CraftShapedRecipe.fromBukkitRecipe(r);
            case ShapelessRecipe r -> CraftShapelessRecipe.fromBukkitRecipe(r);
            case FurnaceRecipe r -> CraftFurnaceRecipe.fromBukkitRecipe(r);
            case BlastingRecipe r -> CraftBlastingRecipe.fromBukkitRecipe(r);
            case SmokingRecipe r -> CraftSmokingRecipe.fromBukkitRecipe(r);
            case StonecuttingRecipe r -> CraftStonecuttingRecipe.fromBukkitRecipe(r);
            case SmithingTransformRecipe r -> CraftSmithingTransformRecipe.fromBukkitRecipe(r);
            case TransmuteRecipe r -> CraftTransmuteRecipe.fromBukkitRecipe(r);
            case null, default -> null;
        };
    }

    private static final class Session {

        private final RecipeManager recipeManager;
        private final PlayerList playerList;
        private final FeatureFlagSet savedFeatureFlags;

        private boolean closed;

        private Session() throws ReflectiveOperationException {
            MinecraftServer server = MinecraftServer.getServer();
            this.recipeManager = server.getRecipeManager();
            this.playerList = server.getPlayerList();

            // 提前返回以加速
            WorldData worldData = server.getWorldData();
            this.savedFeatureFlags = worldData.enabledFeatures();
            setFeatureFlags(this.recipeManager, null);
        }

        private boolean add(Recipe recipe) {
            CraftRecipe craft = asCraftRecipe(recipe);
            if (craft == null) {
                return Bukkit.addRecipe(recipe);
            }
            craft.addToCraftingManager();
            return true;
        }

        private boolean remove(NamespacedKey key) {
            return this.recipeManager.removeRecipe(CraftRecipe.toMinecraft(key));
        }

        private void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;

            try {
                if (this.savedFeatureFlags != null) {
                    this.recipeManager.finalizeRecipeLoading(this.savedFeatureFlags);
                }
            } finally {
                restoreFeatureFlags();
            }
            this.playerList.reloadRecipes();
        }

        private void restoreFeatureFlags() {
            if (this.savedFeatureFlags == null) {
                return;
            }
            try {
                setFeatureFlags(this.recipeManager, this.savedFeatureFlags);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to restore RecipeManager#" + FEATURE_FLAGS_FIELD, e);
            }
        }
    }
}
