package org.icarus.tingere.config;

import lombok.RequiredArgsConstructor;
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
import org.icarus.tingere.model.CustomRecipe;
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
import java.util.stream.Stream;

@RequiredArgsConstructor
public class RecipeLoader {
    private final Tingere plugin;
    // 存储配方 key -> 结果物品（克隆）
    private final Map<String, ItemStack> recipeResults = new HashMap<>();
    private final Map<String, List<ItemStack>> recipeIngredients = new HashMap<>();
    private final Map<String, SpecialRecipeInfo> specialRecipes = new HashMap<>();

    // 存储配方 key -> 合成期需要覆盖的结果（仅转化配方会用到）
    private final Map<String, ResultOverride> resultOverrides = new HashMap<>();

    // 提供公共访问方法
    public SpecialRecipeInfo getSpecialRecipeInfo(String fullKey) {
        return specialRecipes.get(fullKey);
    }

    public List<ItemStack> getIngredients(String key) { // 新增
        return recipeIngredients.get(key);
    }

    public ResultOverride getResultOverride(String fullKey) {
        return resultOverrides.get(fullKey);
    }

    private final Set<NamespacedKey> registeredKeys = new HashSet<>();

    // 获取已注册的 keys（只读）
    public Set<NamespacedKey> getRegisteredKeys() {
        return Collections.unmodifiableSet(registeredKeys);
    }

    // 在成功注册配方后调用
    private void addRegisteredKey(NamespacedKey key) {
        registeredKeys.add(key);
    }

    // 移除本插件所有配方
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
        registeredKeys.clear(); // 清空记录
    }

    /**
     * 扫描插件数据目录下的 recipes 文件夹，加载其中所有 YAML 配方并注册到服务器。
     * 文件夹不存在时会自动创建，方便用户放置配置文件。
     *
     * @return 成功加载的配方数量
     */
    public int loadAllRecipes() {
        recipeResults.clear();
        recipeIngredients.clear();
        resultOverrides.clear();
        specialRecipes.clear();
        registeredKeys.clear();

        File recipesDir = new File(plugin.getDataFolder(), "recipes");
        if (!recipesDir.isDirectory()) {
            if (recipesDir.mkdirs()) {
                plugin.getLogger().info("已创建配方文件夹: " + recipesDir.getAbsolutePath());
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
            plugin.getLogger().log(Level.SEVERE, "扫描配方文件夹失败: " + recipesDir.getAbsolutePath(), e);
            return 0;
        }

        int count = 0;
        for (Path recipePath : recipePaths) {
            File file = recipePath.toFile();
            try {
                int loaded = loadRecipesFromFile(file);
                if (loaded > 0) {
                    String relative = recipesDir.toPath().relativize(recipePath).toString().replace('\\', '/');
                    plugin.getLogger().info("已从 " + relative + " 加载 " + loaded + " 个配方");
                }
                count += loaded;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "加载配方文件 " + file.getName() + " 时出错", e);
            }
        }
        return count;
    }

    private static boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    /**
     * 从单个 YAML 文件加载配方（支持一个文件包含多个配方）
     */
    private int loadRecipesFromFile(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;

        // 如果文件直接是一个配方（顶层有 type 字段）
        if (config.contains("type")) {
            if (registerRecipe(config)) {
                loaded++;
            }
        } else {
            // 否则认为文件包含多个配方，每个顶级键为一个配方
            for (String key : config.getKeys(false)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null && registerRecipe(section)) {
                    loaded++;
                }
            }
        }
        return loaded;
    }

    /**
     * 根据配置段注册单个配方
     *
     * @return 是否成功注册
     */
    private boolean registerRecipe(ConfigurationSection section) {
        try {
            String type = section.getString("type");
            if (type == null) {
                plugin.getLogger().warning("跳过配方：缺少 type 字段");
                return false;
            }

            String key = section.getString("key");
            if (key == null) {
                plugin.getLogger().warning("跳过配方：缺少 key 字段");
                return false;
            }

            // 解析 result
            ItemStack result = parseItemStack(section.getConfigurationSection("result"));
            if (result == null) {
                plugin.getLogger().warning("跳过配方 " + key + "：结果物品无效");
                return false;
            }

            CustomRecipe recipeModel;
            if ("shaped".equalsIgnoreCase(type)) {
                List<String> pattern = section.getStringList("pattern");
                if (pattern.isEmpty()) {
                    plugin.getLogger().warning("跳过有序配方 " + key + "：pattern 不能为空");
                    return false;
                }

                ConfigurationSection ingredientsSection = section.getConfigurationSection("ingredients");
                if (ingredientsSection == null) {
                    plugin.getLogger().warning("跳过有序配方 " + key + "：缺少 ingredients");
                    return false;
                }

                Map<Character, ShapedRecipeModel.IngredientInfo> ingredients = new HashMap<>();
                for (String charKey : ingredientsSection.getKeys(false)) {
                    if (charKey.length() != 1) {
                        plugin.getLogger().warning("跳过有序配方 " + key + "：原料键必须是单个字符，但得到 " + charKey);
                        return false;
                    }
                    char symbol = charKey.charAt(0);
                    ConfigurationSection ingSec = ingredientsSection.getConfigurationSection(charKey);
                    if (ingSec == null) {
                        plugin.getLogger().warning("跳过有序配方 " + key + "：原料 " + charKey + " 配置无效");
                        return false;
                    }

                    ItemStack ing = parseItemStack(ingSec);
                    if (ing == null) {
                        plugin.getLogger().warning("跳过有序配方 " + key + "：原料 " + charKey + " 无效");
                        return false;
                    }

                    // 读取 match-mode，默认为 true（精确匹配）
                    String matchMode = ingSec.getString("match-mode", "exact");
                    boolean exactMatch = !"material".equalsIgnoreCase(matchMode); // 如果不是 material，则为 exact

                    ingredients.put(symbol, new ShapedRecipeModel.IngredientInfo(ing, exactMatch));
                }

                recipeModel = new ShapedRecipeModel(key, pattern, ingredients, result);
            } else if ("shapeless".equalsIgnoreCase(type)) {
                // 解析无序配方：使用 getMapList() 获取原料列表
                List<Map<?, ?>> ingMaps = section.getMapList("ingredients");
                if (ingMaps.isEmpty()) {
                    plugin.getLogger().warning("跳过无序配方 " + key + "：ingredients 不能为空");
                    return false;
                }

                List<ItemStack> ingredients = new ArrayList<>();
                for (Map<?, ?> map : ingMaps) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stringMap = (Map<String, Object>) map;
                    // 将 Map 转换为临时 ConfigurationSection，以便复用 parseItemStack
                    ConfigurationSection ingSec = new MemoryConfiguration().createSection("temp", stringMap);
                    ItemStack ing = parseItemStack(ingSec);
                    if (ing == null) {
                        plugin.getLogger().warning("跳过无序配方 " + key + "：某个原料无效");
                        return false;
                    }
                    ingredients.add(ing);
                }

                recipeModel = new ShapelessRecipeModel(key, ingredients, result);
            } else if ("transmute".equalsIgnoreCase(type)) {
                // === 新增：转化配方 ===
                ConfigurationSection inputSection = section.getConfigurationSection("input");
                ConfigurationSection materialSection = section.getConfigurationSection("material");

                if (inputSection == null || materialSection == null) {
                    plugin.getLogger().warning("跳过转化配方 " + key + "：缺少 input 或 material");
                    return false;
                }

                ItemStack input = parseItemStack(inputSection);
                ItemStack material = parseItemStack(materialSection);

                if (input == null || material == null) {
                    plugin.getLogger().warning("跳过转化配方 " + key + "：input 或 material 无效");
                    return false;
                }

                NamespacedKey fullKey = new NamespacedKey(plugin, key);

                // 结果组件与数量无法在注册阶段生效（TransmuteRecipe 只接受 Material、数量恒为 1），
                // 因此记录下来供 CraftListener 在合成期覆盖
                ConfigurationSection resultComponents = section.getConfigurationSection("result.components");
                if (resultComponents != null || result.getAmount() != 1) {
                    resultOverrides.put(fullKey.toString(), new ResultOverride(resultComponents, result.getAmount()));
                }

                recipeModel = new TransmuteRecipeModel(key, input, material, result);

            } else {
                plugin.getLogger().warning("跳过配方 " + key + "：未知类型 " + type);
                return false;
            }
            NamespacedKey namespacedKey = new NamespacedKey(plugin, key);
            // 注册到 Bukkit
            Bukkit.addRecipe(recipeModel.toBukkitRecipe());
            addRegisteredKey(namespacedKey);
            // 成功注册后，存储结果物品（克隆一份，防止后续被修改）
            recipeResults.put(key, result.clone());
            // 根据配方类型生成原料列表
            List<ItemStack> ingredients = new ArrayList<>();

            if ("shaped".equalsIgnoreCase(type)) {
                // 有序配方：按 pattern 顺序（从左到右、从上到下）收集非空格原料
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
                // 无序配方：直接使用解析时的原料列表（已存储在 recipeModel 中？）
                // 但我们在解析时已经生成了 ingredients 列表，需要复用或重新解析。
                // 由于在解析无序配方时我们已有 List<ItemStack>，可以在那时就存入映射。
                // 这里我们重新解析一次，保持统一逻辑。
                List<Map<?, ?>> ingMaps = section.getMapList("ingredients");
                for (Map<?, ?> map : ingMaps) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> stringMap = (Map<String, Object>) map;
                    ConfigurationSection ingSec = new MemoryConfiguration().createSection("temp", stringMap);
                    ItemStack ing = parseItemStack(ingSec);
                    if (ing != null) ingredients.add(ing.clone());
                }
            } else if ("transmute".equalsIgnoreCase(type)) {
                // 转化配方：input 和 material
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
                        // 配置错误在加载期就报出来，否则合成期每次矩阵变化都会刷屏。
                        // 此处配方已注册，故只是忽略 special 段，让退化为普通转化配方。
                        plugin.getLogger().warning("配方 " + key + " 的 special.target-material 无效: " + targetMatStr + "，已忽略 special 段");
                    } else {
                        SpecialRecipeInfo info = new SpecialRecipeInfo();
                        info.setTargetMaterial(targetMaterial);
                        info.setCopyInput(specialSec.getBoolean("copy-input", true));
                        info.setComponents(specialSec.getConfigurationSection("components"));
                        // 原版 TransmuteRecipe 的结果数量恒为 1，真实数量只能在这里记下来
                        info.setAmount(result.getAmount());

                        // 解析 source-character
                        if (specialSec.contains("source-character")) {
                            String charStr = specialSec.getString("source-character");
                            if (charStr != null && charStr.length() == 1) {
                                info.setSourceCharacter(charStr.charAt(0));
                            }
                        }
                        // 解析 source-slot
                        if (specialSec.contains("source-slot")) {
                            int slot = specialSec.getInt("source-slot");
                            if (slot >= 0 && slot <= 8) {
                                info.setSourceSlot(slot);
                            } else {
                                plugin.getLogger().warning("特殊配方 " + key + " 的 source-slot " + slot + " 无效，必须 0-8");
                            }
                        }

                        NamespacedKey fullKey = new NamespacedKey(plugin, key);
                        specialRecipes.put(fullKey.toString(), info);
                        plugin.getLogger().info("已存储特殊配方: " + fullKey + " -> " + info.getTargetMaterial() + " x" + info.getAmount() +
                                (info.getSourceCharacter() != null ? ", sourceChar=" + info.getSourceCharacter() : "") +
                                (info.getSourceSlot() != null ? ", sourceSlot=" + info.getSourceSlot() : ""));
                    }
                }
            }
            recipeIngredients.put(key, ingredients);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "解析配方时发生异常", e);
            return false;
        }
    }

    public ItemStack getResultItem(String key) {
        return recipeResults.get(key);
    }

    public Set<String> getAllRecipeKeys() {
        return recipeResults.keySet();
    }

    /**
     * 解析一个物品的配置节，返回带有组件的 ItemStack
     * 配置格式示例：
     * material: DIAMOND_SWORD
     * amount: 1
     * components:
     * custom-model-data: 1001
     * display-name: "红剑"
     * lore:
     * - "第一行"
     * enchantments:
     * - sharpness:5
     * unbreakable: true
     */
    private ItemStack parseItemStack(ConfigurationSection section) {
        if (section == null) return null;

        String materialName = section.getString("material");
        if (materialName == null) return null;

        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null) return null;

        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(material, amount);

        // 组件应用统一交给 ItemComponents，与 CraftListener 共用同一套实现
        return ItemComponents.apply(plugin, item, section.getConfigurationSection("components"), "gen");
    }
}
