package com.github.bunnyi116.bedrockminer.command.commands;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.I18n;
import com.github.bunnyi116.bedrockminer.command.CommandBase;
import com.github.bunnyi116.bedrockminer.command.argument.BlockPosArgumentType;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.config.ConfigManager;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import com.github.bunnyi116.bedrockminer.task.TaskRegion;
import com.github.bunnyi116.bedrockminer.util.MessageUtils;
import com.github.bunnyi116.bedrockminer.util.StringReaderUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class TaskCommand extends CommandBase {

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public void build(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        builder
                .then(literal("limitMax")
                        .then(argument("int", IntegerArgumentType.integer(1, 10))
                                .executes(context -> {
                                    int limitMax = IntegerArgumentType.getInteger(context, "int");
                                    MessageUtils.addMessage(Component.literal("Concurrent task limit set to: " + limitMax));
                                    Config.getInstance().limitMax = limitMax;
                                    Config.getInstance().save();
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("short")
                        .then(argument("bool", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean b = BoolArgumentType.getBool(context, "bool");
                                    if (b) {
                                        MessageUtils.addMessage(I18n.COMMAND_TASK_SHORT_WAIT_SHORT);
                                    } else {
                                        MessageUtils.addMessage(I18n.COMMAND_TASK_SHORT_WAIT_NORMAL);
                                    }
                                    Config.getInstance().shortTsk = b;
                                    Config.getInstance().save();
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("outOfRangeCooldown")
                        .then(argument("ticks", IntegerArgumentType.integer(0, 200))
                                .executes(context -> {
                                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                    Config.getInstance().outOfRangeCooldownTicks = ticks;
                                    Config.getInstance().save();
                                    if (ticks == 0) {
                                        MessageUtils.addMessage(Component.literal("Out-of-range cooldown disabled (tasks cycle immediately)"));
                                    } else {
                                        MessageUtils.addMessage(Component.literal("Out-of-range cooldown set to " + ticks + " ticks"));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("range")
                        .then(argument("blocks", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(context -> {
                                    double blocks = DoubleArgumentType.getDouble(context, "blocks");
                                    Config.getInstance().interactionRangeOverride = blocks;
                                    Config.getInstance().save();
                                    if (blocks <= 0.0) {
                                        MessageUtils.addMessage(Component.literal("Interaction range override disabled (using vanilla)"));
                                    } else {
                                        MessageUtils.addMessage(Component.literal("Interaction range override set to " + blocks + " blocks" + (blocks > 6 ? ". (requires a reach mod for server-side effect)" : ".")));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("add")
                        .then(argument("name", StringArgumentType.string())
                                .then(argument("blockPos1", BlockPosArgumentType.blockPos())
                                        .then(argument("blockPos2", BlockPosArgumentType.blockPos())
                                                .executes(context -> addRegionTask(context, false))
                                        )
                                )
                        )
                )
                .then(literal("addToConfig")
                        .then(argument("name", StringArgumentType.string())
                                .then(argument("blockPos1", BlockPosArgumentType.blockPos())
                                        .then(argument("blockPos2", BlockPosArgumentType.blockPos())
                                                .executes(context -> addRegionTask(context, true))
                                        )
                                )
                        )
                )
                .then(literal("remove")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((context, suggestionsBuilder) -> {
                                    StringReader reader = new StringReader(suggestionsBuilder.getInput());
                                    reader.setCursor(suggestionsBuilder.getStart());
                                    String input = StringReaderUtils.readUnquotedString(reader);
                                    for (TaskRegion item : Config.getInstance().ranges) {
                                        if (item.name.contains(input)) {
                                            suggestionsBuilder.suggest(StringArgumentType.escapeIfRequired(item.name));
                                        }
                                    }
                                    for (TaskRegion item : TaskManager.getInstance().getPendingRegionTasks()) {
                                        if (item.name.contains(input)) {
                                            suggestionsBuilder.suggest(StringArgumentType.escapeIfRequired(item.name));
                                        }
                                    }
                                    return suggestionsBuilder.buildFuture();
                                })
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    @Nullable TaskRegion range = null;
                                    for (TaskRegion item : Config.getInstance().ranges) {
                                        if (item.name.equals(name)) {
                                            range = item;
                                            break;
                                        }
                                    }
                                    if (range == null) {
                                        for (TaskRegion item : TaskManager.getInstance().getPendingRegionTasks()) {
                                            if (item.name.equals(name)) {
                                                range = item;
                                                break;
                                            }
                                        }
                                    }
                                    if (range != null) {
                                        Config.getInstance().ranges.remove(range);
                                        Config.getInstance().save();
                                        MessageUtils.addMessage(Component.literal("Removed: " + name));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("clear").executes(context -> {
                    TaskManager.getInstance().removeBlockTaskAll();
                    TaskManager.getInstance().removeRegionTaskAll();
                    return Command.SINGLE_SUCCESS;
                }));

    }

    private int addRegionTask(CommandContext<FabricClientCommandSource> context, boolean isConfig) {
        String name = StringArgumentType.getString(context, "name");
        BlockPos blockPos1 = BlockPosArgumentType.getBlockPos(context, "blockPos1");
        BlockPos blockPos2 = BlockPosArgumentType.getBlockPos(context, "blockPos2");
        boolean b = true;
        if (isConfig) {
            for (TaskRegion item : Config.getInstance().ranges) {
                if (item.name.equals(name)) {
                    b = false;
                    break;
                }
            }
            if (b) {
                TaskRegion range = new TaskRegion(name, BedrockMiner.world, blockPos1, blockPos2);
                ConfigManager.getInstance().getConfig().ranges.add(range);
                ConfigManager.getInstance().getConfig().save();
                MessageUtils.addMessage(Component.literal("Added to config: " + name));
            }
        } else {
            for (TaskRegion item : TaskManager.getInstance().getPendingRegionTasks()) {
                if (item.name.equals(name)) {
                    b = false;
                    break;
                }
            }
            if (b) {
                TaskRegion range = new TaskRegion(name, BedrockMiner.world, blockPos1, blockPos2);
                TaskManager.getInstance().getPendingRegionTasks().add(range);
                MessageUtils.addMessage(Component.literal("Added: " + name));
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}
