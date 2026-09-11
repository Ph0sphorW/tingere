package org.icarus.tingere.config;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.icarus.tingere.Tingere;
import org.icarus.tingere.model.CustomRecipeInterface;
import org.icarus.tingere.model.ShapedRecipeModel;
import org.icarus.tingere.model.ShapelessRecipeModel;
import org.icarus.tingere.model.TransmuteRecipeModel;
import org.icarus.tingere.util.ItemComponents;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class RecipeLoader {
    private final Tingere plugin;
    private final Map<String, ItemStack> recipeResults = new HashMap<>();
    private final Map<String, List<ItemStack>> recipeIngredients = new HashMap<>();
    private final Map<String, SpecialRecipeInfo> specialRecipes = new HashMap<>();
    private final Map<String, ResultOverride> resultOverrides = new HashMap<>();
    private final Set<NamespacedKey> registeredKeys = new HashSet<>();
    private final Logger logger;

    private void addRegisteredKey(NamespacedKey key) {
        registeredKeys.add(key);
    }

    public RecipeLoader(Tingere plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public SpecialRecipeInfo getSpecialRecipeInfo(String fullKey) {
        return specialRecipes.get(fullKey);
    }

    public ResultOverride getResultOverride(String fullKey) {
        return resultOverrides.get(fullKey);
    }

    public List<ItemStack> getIngredients(String key) { // 新增
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
                if (key.getNamespace().equals(plugin.getName().toLowerCase())) {
                    toRemove.add(key);
                }
            }
        }
        for (NamespacedKey key : toRemove) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
    }

    public int loadAllRecipes() {
        recipeResults.clear();
        recipeIngredients.clear();
        resultOverrides.clear();
        specialRecipes.clear();
        registeredKeys.clear();

        File recipesDir = new File(plugin.getDataFolder(), "recipes");
        if (!recipesDir.isDirectory()) {
            if (recipesDir.mkdirs()) {
                logger.info("Created recipe folder: " + recipesDir.getAbsolutePath());
            }
            return 0;
        }

        List<Path> recipePaths;
        try (Stream<Path> paths = Files.walk(recipesDir.toPath())) {
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
            File file = recipePath.toFile();
            try {
                int loaded = loadRecipesFromFile(file);
                if (loaded > 0) {
                    String relative = recipesDir.toPath().relativize(recipePath).toString().replace('\\', '/');
                    logger.info("Loaded " + loaded + " recipe(s) from " + relative + ".");
                }
                count += loaded;
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error while trying to parse file " + file.getName() + ":", e);
            }
        }
        return count;
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private int loadRecipesFromFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;

        if (config.contains("type")) {
            if (registerRecipe(config)) {
                loaded++;
            }
        } else {
            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null && registerRecipe(section)) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    private boolean registerRecipe(ConfigurationSection section) {
        try {
            String type = section.getString("type");
            if (type == null) {
                logger.warning("Skipped registration for missing recipe type");
                return false;
            }

            String key = section.getString("key");
            if (key == null) {
                logger.warning("Skipped registration for missing recipe key");
                return false;
            }

            ItemStack result = parseItemStack(section.getConfigurationSection("result"));
            if (result == null) {
                logger.warning("Skipped recipe " + key + " for invalid result item");
                return false;
            }

            CustomRecipeInterface recipeModel;
            if ("shaped".equalsIgnoreCase(type)) {
                List<String> pattern = section.getStringList("pattern");
                if (pattern.isEmpty()) {
                    logger.warning("Skipped ordered recipe " + key + " for an empty pattern");
                    return false;
                }

                ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
                if (ingredientsSection == null) {
                    logger.warning("Skipped ordered recipe " + key + " for missing ingredients");
                    return false;
                }

                Map<Character, ShapedRecipeModel.IngredientInfo> ingredients = new HashMap<>();
                for (String charKey : ingredientsSection.getKeys(false)) {
                    if (charKey.length() != 1) {
                        logger.warning("Skipped ordered recipe " + key + " for multiplied char key: " + charKey);
                        return false;
                    }
                    char symbol = charKey.charAt(0);
                    ConfigurationSection ingSec = ingredientsSection.getConfigurationSection(charKey);
                    if (ingSec == null) {
                        logger.warning("Skipped ordered recipe " + key + " for having a invalid ingredient " + charKey);
                        return false;
                    }

                    ItemStack ing = parseItemStack(ingSec);
                    if (ing == null) {
                        logger.warning("Skipped ordered recipe " + key + " for invalid char key: " + charKey);
                        return false;
                    }

                    String matchMode = ingSec.getString("match-mode", "exact");
                    boolean exactMatch = !"material".equalsIgnoreCase(matchMode);

                    ingredients.put(symbol, new ShapedRecipeModel.IngredientInfo(ing, exactMatch));
                }

                recipeModel = new ShapedRecipeModel(key, pattern, ingredients, result);
            } else if ("shapeless".equalsIgnoreCase(type)) {
                List<Map<?, ?>> ingMaps = section.getMapList("ingredients");
                if (ingMaps.isEmpty()) {
                    logger.warning("Skipped shapeless recipe " + key + " for missing ingredients");
                    return false;
                }

                List<ItemStack> ingredients = new ArrayList<>();
                for (Map<?, ?> map : ingMaps) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stringMap = (Map<String, Object>) map;
                    ConfigurationSection ingSec = new MemoryConfiguration().createSection("temp", stringMap);
                    ItemStack ing = parseItemStack(ingSec);
                    if (ing == null) {
                        logger.warning("Skipped shapeless recipe " + key + " for invalid ingredients");
                        return false;
                    }
                    ingredients.add(ing);
                }

                recipeModel = new ShapelessRecipeModel(key, ingredients, result);
            } else if ("transmute".equalsIgnoreCase(type)) {
                ConfigurationSection inputSection = section.getConfigurationSection("input");
                ConfigurationSection materialSection = section.getConfigurationSection("material");

                if (inputSection == null || materialSection == null) {
                    logger.warning("Skipped transmute recipe " + key + " for missing input or material");
                    return false;
                }

                ItemStack input = parseItemStack(inputSection);
                ItemStack material = parseItemStack(materialSection);

                if (input == null || material == null) {
                    logger.warning("Skipped transmute recipe " + key + " for invalid input or material");
                    return false;
                }

                NamespacedKey fullKey = new NamespacedKey(plugin, key);

                ConfigurationSection resultComponents = section.getConfigurationSection("result.components");
                if (resultComponents != null || result.getAmount() != 1) {
                    resultOverrides.put(fullKey.toString(), new ResultOverride(resultComponents, result.getAmount()));
                }

                recipeModel = new TransmuteRecipeModel(key, input, material, result);

            } else {
                logger.warning("Skipped recipe " + key + " for invalid type " + type);
                return false;
            }
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            Bukkit.addRecipe(recipeModel.toBukkitRecipe());
            addRegisteredKey(namespacedKey);
            recipeResults.put(key, result.clone());
            List<ItemStack> ingredients = new ArrayList<>();

            if ("shaped".equalsIgnoreCase(type)) {
                List<String> pattern = section.getStringList("pattern");
                ConfigurationSection ingredientsSec = section.getConfigurationSection("ingredients");
                if (ingredientsSec != null) {
                    for (String row : pattern) {
                        for (char c : row.toCharArray()) {
                            if (c != ' ') {
                                ConfigurationSection ingSec = ingredientsSec.getConfigurationSection(String.valueOf(c));
                                if (ingSec != null) {
                                    ItemStack ing = parseItemStack(ingSec);
                                    if (ing != null) {
                                        ingredients.add(ing.clone());
                                    }
                                }
                            }
                        }
                    }
                }
            } else if ("shapeless".equalsIgnoreCase(type)) {
                List<Map<?, ?>> ingMaps = section.getMapList("ingredients");
                for (Map<?, ?> map : ingMaps) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stringMap = (Map<String, Object>) map;
                    ConfigurationSection ingSec = new MemoryConfiguration().createSection("temp", stringMap);
                    ItemStack ing = parseItemStack(ingSec);
                    if (ing != null) ingredients.add(ing.clone());
                }
            } else if ("transmute".equalsIgnoreCase(type)) {
                ConfigurationSection inputSec = section.getConfigurationSection("input");
                ConfigurationSection materialSec = section.getConfigurationSection("material");
                if (inputSec != null) {
                    ItemStack input = parseItemStack(inputSec);
                    if (input != null) ingredients.add(input.clone());
                }
                if (materialSec != null) {
                    ItemStack material = parseItemStack(materialSec);
                    if (material != null) ingredients.add(material.clone());
                }
            }

            if (section.contains("special")) {
                ConfigurationSection specialSec = section.getConfigurationSection("special");
                if (specialSec != null) {
                    String targetMatStr = specialSec.getString("target-material");
                    Material targetMaterial = targetMatStr == null ? null : Material.getMaterial(targetMatStr.toUpperCase());
                    if (targetMaterial == null || targetMaterial.isAir()) {
                        logger.warning("Recipe " + key + " has an invalid special.target-material: " + targetMatStr + ", ignored");
                    } else {
                        SpecialRecipeInfo info = new SpecialRecipeInfo();
                        info.setTargetMaterial(targetMaterial);
                        info.setCopyInput(specialSec.getBoolean("copy-input", true));
                        info.setComponents(specialSec.getConfigurationSection("components"));
                        info.setAmount(result.getAmount());

                        if (specialSec.contains("source-character")) {
                            String charStr = specialSec.getString("source-character");
                            if (charStr != null && charStr.length() == 1) {
                                info.setSourceCharacter(charStr.charAt(0));
                            }
                        }
                        if (specialSec.contains("source-slot")) {
                            int slot = specialSec.getInt("source-slot");
                            if (slot >= 0 && slot <= 8) {
                                info.setSourceSlot(slot);
                            } else {
                                logger.warning("Special recipe " + key + " has an invalid source-slot " + slot + ": should be a number between 0 and 8");
                            }
                        }

                        NamespacedKey fullKey = new NamespacedKey(plugin, key);
                        specialRecipes.put(fullKey.toString(), info);
                        logger.info("Stored special recipe: " + fullKey + " -> " + info.getTargetMaterial() + " x" + info.getAmount() +
                                (info.getSourceCharacter() != null ? ", sourceChar=" + info.getSourceCharacter() : "") +
                                (info.getSourceSlot() != null ? ", sourceSlot=" + info.getSourceSlot() : ""));
                    }
                }
            }
            recipeIngredients.put(key, ingredients);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "An error occurred while trying to parse a recipe", e);
            return false;
        }
    }

    private ItemStack parseItemStack(ConfigurationSection section) {
        if (section == null) return null;

        String materialName = section.getString("material");
        if (materialName == null) return null;

        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null) return null;

        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(material, amount);

        return ItemComponents.apply(plugin, item, section.getConfigurationSection("components"), "gen");
    }
}
