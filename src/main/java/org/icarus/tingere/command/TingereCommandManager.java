package org.icarus.tingere.command;

import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.PredicatePermission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.icarus.tingere.Tingere;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TingereCommandManager {

    private static final String ROOT = "tingere";
    private static final String ADMIN_PERMISSION = "tingere.admin";

    private final Tingere plugin;

    private PaperCommandManager<Source> commandManager;
    private MinecraftHelp<Source> help;

    public TingereCommandManager(Tingere plugin) {
        this.plugin = plugin;
    }

    private SuggestionProvider<Source> recipeKeySuggestions() {
        return SuggestionProvider.blockingStrings((context, input) -> {
            String typed = input.remainingInput().toLowerCase(Locale.ROOT);
            return plugin.getRecipeLoader().getAllRecipeKeys().stream()
                    .filter(key -> key.toLowerCase(Locale.ROOT).startsWith(typed))
                    .sorted()
                    .toList();
        });
    }

    public void register() {
        this.commandManager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
                .buildOnEnable(plugin);
        this.help = MinecraftHelp.create("/" + ROOT + " help", commandManager, Source::source);

        registerExceptionHandlers();
        registerRootCommand();
        registerReloadCommand();
        registerGetCommands();
    }

    private void registerRootCommand() {
        commandManager.command(root()
                .permission(adminPermission())
                .handler(context -> help.queryCommands("", context.sender())));
        commandManager.command(root()
                .literal("help", Description.of("显示命令帮助"))
                .optional("query", StringParser.greedyStringParser())
                .permission(adminPermission())
                .handler(context -> help.queryCommands(context.getOrDefault("query", ""), context.sender())));
    }

    private void registerReloadCommand() {
        commandManager.command(root()
                .literal("reload", Description.of("重新加载全部配方文件"))
                .permission(adminPermission())
                .handler(context -> {
                    CommandSender sender = sender(context);
                    long start = System.currentTimeMillis();
                    plugin.reloadRecipes();
                    sender.sendRichMessage("<green>配方重载完成，耗时 " + (System.currentTimeMillis() - start) + "ms");
                }));
    }

    private void registerGetCommands() {
        commandManager.command(root()
                .literal("get", Description.of("获取配方内容用于调试"))
                .literal("result", Description.of("获取配方的结果物品"))
                .required("key", StringParser.stringParser(), recipeKeySuggestions())
                .optional("amount", IntegerParser.integerParser(1, 64))
                .permission(adminPermission())
                .handler(context -> {
                    CommandSender sender = sender(context);
                    if (sender instanceof Player player) {
                        giveResultItem(player, context.get("key"), context.getOrDefault("amount", 1));
                    } else {
                        sender.sendRichMessage("<red>该命令只能由玩家执行");
                    }
                }));

        // 取某个原料
        commandManager.command(root()
                .literal("get", Description.of("获取配方内容用于调试"))
                .literal("ingredient", Description.of("获取配方的某个原料"))
                .required("key", StringParser.stringParser(), recipeKeySuggestions())
                .optional("index", IntegerParser.integerParser(1, Integer.MAX_VALUE))
                .permission(adminPermission())
                .handler(context -> {
                    CommandSender sender = sender(context);
                    if (sender instanceof Player player) {
                        giveIngredient(player, context.get("key"), context.getOrDefault("index", 1));
                    } else {
                        sender.sendRichMessage("<red>该命令只能由玩家执行");
                    }
                }));

        commandManager.command(root()
                .literal("get", Description.of("获取配方内容用于调试"))
                .literal("recipes", Description.of("为玩家解锁配方"))
                .required("player", PlayerParser.playerParser())
                .required("pattern", StringParser.greedyStringParser(), SuggestionProvider.suggestingStrings("*"))
                .permission(adminPermission())
                .handler(context -> unlockRecipes(
                        sender(context),
                        context.get("player"),
                        context.get("pattern"))));
    }

    private void giveResultItem(Player player, String key, int amount) {
        ItemStack result = plugin.getRecipeLoader().getResultItem(key);
        if (result == null) {
            player.sendRichMessage("<red>未找到配方: " + key);
            return;
        }

        ItemStack item = result.clone();
        item.setAmount(amount);
        give(player, item);
        player.sendRichMessage("<green>已获取配方 <yellow>" + key + "</yellow> <green>的结果物品 x" + amount);
    }

    private void giveIngredient(Player player, String key, int index) {
        List<ItemStack> ingredients = plugin.getRecipeLoader().getIngredients(key);
        if (ingredients == null || ingredients.isEmpty()) {
            player.sendRichMessage("<red>未找到配方或该配方没有原料: " + key);
            return;
        }
        if (index > ingredients.size()) {
            player.sendRichMessage("<red>序号无效，有效范围 1 ~ " + ingredients.size());
            return;
        }

        ItemStack ingredient = ingredients.get(index - 1).clone();
        ingredient.setAmount(1);
        give(player, ingredient);
        player.sendRichMessage("<green>已获取配方 <yellow>" + key + "</yellow> <green>的第 " + index + " 个原料");
    }

    private void unlockRecipes(CommandSender sender, Player target, String pattern) {
        Set<NamespacedKey> allKeys = plugin.getRecipeLoader().getRegisteredKeys();
        if (allKeys.isEmpty()) {
            sender.sendRichMessage("<red>当前没有已加载的自定义配方");
            return;
        }

        Set<NamespacedKey> filtered = new HashSet<>();
        if ("*".equals(pattern)) {
            filtered.addAll(allKeys);
        } else {
            String lowerPattern = pattern.toLowerCase(Locale.ROOT);
            for (NamespacedKey key : allKeys) {
                if (key.toString().toLowerCase(Locale.ROOT).contains(lowerPattern)) {
                    filtered.add(key);
                }
            }
        }

        if (filtered.isEmpty()) {
            sender.sendRichMessage("<red>没有找到匹配的配方");
            return;
        }

        Set<NamespacedKey> toUnlock = new HashSet<>(filtered);
        toUnlock.removeAll(target.getDiscoveredRecipes());
        if (toUnlock.isEmpty()) {
            sender.sendRichMessage("<green>玩家 " + target.getName() + " 已经解锁了所有匹配的配方");
            return;
        }

        target.discoverRecipes(toUnlock);
        sender.sendRichMessage("<green>已为玩家 " + target.getName() + " 解锁 " + toUnlock.size() + " 个新配方");
        if (!sender.equals(target)) {
            target.sendRichMessage("<green>管理员已为你解锁 " + toUnlock.size() + " 个新自定义配方");
        }
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
    }

    private void registerExceptionHandlers() {
        commandManager.exceptionController().registerHandler(NoPermissionException.class,
                context -> sender(context.context()).sendRichMessage("<red>你没有权限执行此命令"));
        commandManager.exceptionController().registerHandler(NoSuchCommandException.class,
                context -> sender(context.context()).sendRichMessage("<red>未知命令，可用: /" + ROOT + " help"));
        commandManager.exceptionController().registerHandler(InvalidSyntaxException.class,
                context -> sender(context.context()).sendRichMessage("<red>参数不完整，可用: /" + ROOT + " help"));
        commandManager.exceptionController().registerHandler(ArgumentParseException.class,
                context -> sender(context.context()).sendRichMessage("<red>参数无效: " + describe(context.exception())));
    }

    // 过编译用的
    private static String describe(@NotNull Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return throwable.getClass().getSimpleName();
    }

    private Command.Builder<Source> root() {
        return commandManager.commandBuilder(ROOT, Description.of("Tingere 配方管理"), "tg", "trecipes");
    }

    private static PredicatePermission<Source> adminPermission() {
        return PredicatePermission.of(source -> source.source() instanceof ConsoleCommandSender
                || source.source().hasPermission(ADMIN_PERMISSION));
    }

    private static CommandSender sender(CommandContext<Source> context) {
        return context.sender().source();
    }
}
