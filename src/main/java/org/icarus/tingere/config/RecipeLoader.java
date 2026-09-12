package org.icarus.tingere.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.parser.RecipeParser;
import org.icarus.tingere.recipe.Ingredient;
import org.icarus.tingere.recipe.RecipeDefinition;
import org.icarus.tingere.recipe.ResultOverride;
import org.icarus.tingere.recipe.SpecialDefinition;
import org.icarus.tingere.recipe.TransmuteRecipeDefinition;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class RecipeLoader {

    private static final String RECIPES_FOLDER = "recipes";

    private final Tingere plugin;
    private final Logger logger;
    private final RecipeParser parser = new RecipeParser();

    private final Map<String, ItemStack> recipeResults = new HashMap<>();
    private final Map<String, List<ItemStack>> recipeIngredients = new HashMap<>();
    private final Map<String, SpecialDefinition> specialRecipes = new HashMap<>();
    private final Map<String, ResultOverride> resultOverrides = new HashMap<>();
    private final Set<NamespacedKey> registeredKeys = new HashSet<>();

    public RecipeLoader(Tingere plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public SpecialDefinition getSpecialRecipeInfo(String fullKey) {
        return specialRecipes.get(fullKey);
    }

    public ResultOverride getResultOverride(String fullKey) {
        return resultOverrides.get(fullKey);
    }

    public List<ItemStack> getIngredients(String key) {
        return recipeIngredients.get(key);
    }

    public Set<NamespacedKey> getRegisteredKeys() {
        return Collections.unmodifiableSet(registeredKeys);
    }

    public Set<String> getAllRecipeKeys() {
        return recipeResults.keySet();
    }

    public ItemStack getResultItem(String key) {
        return recipeResults.get(key);
    }

    public void removeAllPluginRecipes() {
        List<NamespacedKey> toRemove = new ArrayList<>();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof Keyed keyed) {
                NamespacedKey key = keyed.getKey();
                if (key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) {
                    toRemove.add(key);
                }
            }
        }
        toRemove.forEach(Bukkit::removeRecipe);
        registeredKeys.clear();
    }

    public int loadAllRecipes() {
        recipeResults.clear();
        recipeIngredients.clear();
        resultOverrides.clear();
        specialRecipes.clear();
        registeredKeys.clear();

        File recipesDir = new File(plugin.getDataFolder(), RECIPES_FOLDER);
        if (!recipesDir.isDirectory()) {
            if (recipesDir.mkdirs()) {
                logger.info("Created recipe folder: " + recipesDir.getAbsolutePath());
            }
            return 0;
        }

        Path base = recipesDir.toPath();
        List<Path> recipePaths;
        try (Stream<Path> paths = Files.walk(base)) {
            recipePaths = paths
                    .filter(Files::isRegularFile)
                    .filter(RecipeLoader::isYamlFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to scan recipe folder: " + recipesDir.getAbsolutePath(), e);
            return 0;
        }

        int count = 0;
        for (Path recipePath : recipePaths) {
            count += loadRecipesFromFile(recipePath, base.relativize(recipePath).toString().replace('\\', '/'));
        }
        return count;
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private int loadRecipesFromFile(Path path, String relative) {
        JsonNode root;
        try {
            root = parser.read(path);
        } catch (Exception e) {
            logger.warning("Failed to parse " + relative + ": " + describeException(e));
            return 0;
        }

        if (root.isMissingNode() || root.isNull()) {
            logger.warning("Ignored empty recipe file: " + relative);
            return 0;
        }
        if (!root.isObject()) {
            logger.warning("Ignored " + relative + ": the root must be a mapping");
            return 0;
        }

        int loaded = 0;
        if (root.has("type")) {
            loaded += tryRegister(root, relative, RecipeParser.labelOf(root, relative)) ? 1 : 0;
        } else {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                loaded += tryRegister(entry.getValue(), relative, entry.getKey()) ? 1 : 0;
            }
        }

        if (loaded > 0) {
            logger.info("Loaded " + loaded + " recipe(s) from " + relative + ".");
        }
        return loaded;
    }

    private boolean tryRegister(JsonNode node, String relative, String label) {
        try {
            register(parser.bind(node));
            return true;
        } catch (Exception e) {
            logger.warning("Skipped recipe '" + label + "' in " + relative + ": " + describeException(e));
            return false;
        }
    }

    private void register(RecipeDefinition definition) {
        String id = definition.id();
        NamespacedKey namespacedKey = new NamespacedKey(plugin, id);

        Bukkit.addRecipe(definition.toBukkitRecipe(plugin));
        registeredKeys.add(namespacedKey);

        recipeResults.put(id, definition.result().toItemStack(plugin, "result"));

        List<Ingredient> flattened = definition.flattenedIngredients();
        List<ItemStack> ingredients = new ArrayList<>(flattened.size());
        for (int i = 0; i < flattened.size(); i++) {
            ingredients.add(flattened.get(i).toItemStack(plugin, "ingredient_" + i));
        }
        recipeIngredients.put(id, ingredients);

        SpecialDefinition special = definition.special();
        int amount = definition.result().amountOrDefault();
        boolean needsOverriding = definition.resultComponents() != null || amount != Ingredient.DEFAULT_AMOUNT;
        if (needsOverriding && (special != null || definition instanceof TransmuteRecipeDefinition)) {
            resultOverrides.put(namespacedKey.toString(), new ResultOverride(definition.resultComponents(), amount));
        }

        if (special != null) {
            specialRecipes.put(namespacedKey.toString(), special);
            logger.info("Stored special recipe: " + namespacedKey + " -> " + special.targetMaterial() + " x" + amount
                    + ", sourceChar=" + special.sourceCharacter()
                    + ", sourceSlot=" + special.sourceSlot());
        }
    }

    private static String describeException(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.replaceAll("\\s+at \\[Source:.*$", "").trim();
    }
}
