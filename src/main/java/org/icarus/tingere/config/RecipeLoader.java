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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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

    private final Map<String, Set<String>> fileRecipeIds = new HashMap<>();

    public RecipeLoader(Tingere plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public record ReloadReport(String relative, int removed, int loaded) {
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

    public List<String> listRecipeFiles() {
        Path base = recipesDirectory();
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(base)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(RecipeLoader::isYamlFile)
                    .map(path -> toRelative(base, path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to list recipe files", e);
            return List.of();
        }
    }

    private void removeAllQuiet() {
        List<NamespacedKey> toRemove = new ArrayList<>();
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof Keyed keyed
                    && keyed.getKey().getNamespace().equals(namespace())) {
                toRemove.add(keyed.getKey());
            }
        }
        toRemove.forEach(key -> Bukkit.removeRecipe(key, false));

        registeredKeys.clear();
        fileRecipeIds.clear();
        recipeResults.clear();
        recipeIngredients.clear();
        specialRecipes.clear();
        resultOverrides.clear();
    }

    public int reloadAll() {
        removeAllQuiet();

        Path base = recipesDirectory();
        if (!Files.isDirectory(base)) {
            if (base.toFile().mkdirs()) {
                logger.info("Created recipe folder: " + base.toAbsolutePath());
            } else {
                logger.warning("Recipe folder is not a directory: " + base.toAbsolutePath());
            }
            return 0;
        }

        int count = 0;
        for (String relative : listRecipeFiles()) {
            count += loadRecipesFromFile(base.resolve(relative), relative);
        }

        resendRecipes();
        return count;
    }

    public ReloadReport reloadFile(String rawRelative) throws IOException {
        String relative = unquote(rawRelative).replace('\\', '/');
        Path file = resolveRecipeFile(relative);

        if (!Files.isRegularFile(file)) {
            throw new NoSuchFileException(relative);
        }
        if (!isYamlFile(file)) {
            throw new NoSuchFileException(relative + " (not a .yml or .yaml file)");
        }

        int removed = unregisterFile(relative);
        int loaded = loadRecipesFromFile(file, relative);

        resendRecipes();
        return new ReloadReport(relative, removed, loaded);
    }

    public static String unquote(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("\"")) {
            value = value.substring(1);
        }
        if (value.length() > 1 && value.endsWith("\"")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Path recipesDirectory() {
        return plugin.getDataFolder().toPath().resolve(RECIPES_FOLDER).toAbsolutePath().normalize();
    }

    private Path resolveRecipeFile(String relative) throws IOException {
        Path base = recipesDirectory();
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("'" + relative + "' points outside the recipes folder");
        }
        return resolved;
    }

    private int unregisterFile(String relative) {
        Set<String> ids = fileRecipeIds.remove(relative);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        for (String id : ids) {
            NamespacedKey key = new NamespacedKey(plugin, id);
            Bukkit.removeRecipe(key, false);
            registeredKeys.remove(key);
            recipeResults.remove(id);
            recipeIngredients.remove(id);
            specialRecipes.remove(key.toString());
            resultOverrides.remove(key.toString());
        }
        return ids.size();
    }

    private static String toRelative(Path base, Path path) {
        return base.relativize(path).toString().replace('\\', '/');
    }

    private String namespace() {
        return plugin.getName().toLowerCase(Locale.ROOT);
    }

    private void resendRecipes() {
        Bukkit.updateRecipes();
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

        Set<String> ids = new HashSet<>();
        int loaded = 0;
        if (root.has("type")) {
            loaded += tryRegister(root, relative, RecipeParser.labelOf(root, relative), ids) ? 1 : 0;
        } else {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                loaded += tryRegister(entry.getValue(), relative, entry.getKey(), ids) ? 1 : 0;
            }
        }

        if (!ids.isEmpty()) {
            fileRecipeIds.put(relative, ids);
        }
        if (loaded > 0) {
            logger.info("Loaded " + loaded + " recipe(s) from " + relative + ".");
        }
        return loaded;
    }

    private boolean tryRegister(JsonNode node, String relative, String label, Set<String> ids) {
        try {
            register(parser.bind(node), ids);
            return true;
        } catch (Exception e) {
            logger.warning("Skipped recipe '" + label + "' in " + relative + ": " + describeException(e));
            return false;
        }
    }

    private void register(RecipeDefinition definition, Set<String> ids) {
        String id = definition.id();
        NamespacedKey namespacedKey = new NamespacedKey(plugin, id);

        Bukkit.addRecipe(definition.toBukkitRecipe(plugin), false);
        registeredKeys.add(namespacedKey);
        ids.add(id);

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
                    + (special.sourceCharacter() != null ? ", sourceChar=" + special.sourceCharacter() : "")
                    + (special.sourceSlot() != null ? ", sourceSlot=" + special.sourceSlot() : ""));
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
