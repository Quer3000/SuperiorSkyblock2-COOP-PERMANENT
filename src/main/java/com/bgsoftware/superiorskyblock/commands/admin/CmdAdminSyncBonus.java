package com.bgsoftware.superiorskyblock.commands.admin;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.events.IslandChangeLevelBonusEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandChangeWorthBonusEvent;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.key.Key;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.commands.CommandTabCompletes;
import com.bgsoftware.superiorskyblock.commands.IAdminIslandCommand;
import com.bgsoftware.superiorskyblock.core.events.args.PluginEventArgs;
import com.bgsoftware.superiorskyblock.core.events.plugin.PluginEvent;
import com.bgsoftware.superiorskyblock.core.events.plugin.PluginEventsFactory;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.world.Dimensions;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CmdAdminSyncBonus implements IAdminIslandCommand {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("syncbonus");
    }

    @Override
    public String getPermission() {
        return "superior.admin.syncbonus";
    }

    @Override
    public String getUsage(java.util.Locale locale) {
        return "admin syncbonus <" +
                Message.COMMAND_ARGUMENT_PLAYER_NAME.getMessage(locale) + "/" +
                Message.COMMAND_ARGUMENT_ISLAND_NAME.getMessage(locale) + "/" +
                Message.COMMAND_ARGUMENT_ALL_ISLANDS.getMessage(locale) + "> <worth/level>";
    }

    @Override
    public String getDescription(java.util.Locale locale) {
        return Message.COMMAND_DESCRIPTION_ADMIN_SYNC_BONUS.getMessage(locale);
    }

    @Override
    public int getMinArgs() {
        return 4;
    }

    @Override
    public int getMaxArgs() {
        return 4;
    }

    @Override
    public boolean canBeExecutedByConsole() {
        return true;
    }

    @Override
    public boolean supportMultipleIslands() {
        return true;
    }

    @Override
    public void execute(SuperiorSkyblockPlugin plugin, CommandSender sender, @Nullable SuperiorPlayer targetPlayer, List<Island> islands, String[] args) {
        boolean isWorthBonus = !args[3].equalsIgnoreCase("level");

        int islandsChangedCount = 0;

        for (Island island : islands) {
            BigDecimal currentBonus = isWorthBonus ? island.getBonusWorth() : island.getBonusLevel();
            BigDecimal newBonus = calculateValue(island, isWorthBonus);
            if (!newBonus.equals(currentBonus)) {
                if (isWorthBonus) {
                    PluginEvent<PluginEventArgs.IslandChangeWorthBonus> event = PluginEventsFactory.callIslandChangeWorthBonusEvent(
                            island, sender, IslandChangeWorthBonusEvent.Reason.COMMAND, newBonus);
                    if (!event.isCancelled()) {
                        island.setBonusWorth(event.getArgs().worthBonus);
                        ++islandsChangedCount;
                    }
                } else {
                    PluginEvent<PluginEventArgs.IslandChangeLevelBonus> event = PluginEventsFactory.callIslandChangeLevelBonusEvent(
                            island, sender, IslandChangeLevelBonusEvent.Reason.COMMAND, newBonus);
                    if (!event.isCancelled()) {
                        island.setBonusLevel(event.getArgs().levelBonus);
                        ++islandsChangedCount;
                    }
                }
            }
        }

        if (islandsChangedCount <= 0)
            return;

        if (islandsChangedCount > 1)
            Message.BONUS_SYNC_ALL.send(sender);
        else if (targetPlayer == null)
            Message.BONUS_SYNC_NAME.send(sender, islands.get(0).getName());
        else
            Message.BONUS_SYNC.send(sender, targetPlayer.getName());
    }

    @Override
    public List<String> adminTabComplete(SuperiorSkyblockPlugin plugin, CommandSender sender, Island island, String[] args) {
        return args.length == 4 ? CommandTabCompletes.getCustomComplete(args[3], "worth", "level") : Collections.emptyList();
    }

    private static BigDecimal calculateValue(Island island, boolean calculateWorth) {
        BigDecimal value = BigDecimal.ZERO;

        String generatedSchematic = island.getSchematicName();

        for (Dimension dimension : Dimension.values()) {
            if (island.wasSchematicGenerated(dimension)) {
                String suffix = dimension == Dimensions.NORMAL ? "" : "_" + dimension.getName().toLowerCase(Locale.ENGLISH);
                Schematic schematic = plugin.getSchematics().getSchematic(generatedSchematic + suffix);
                if (schematic != null) {
                    value = value.add(_calculateValues(schematic.getBlockCounts(), calculateWorth));
                }
            }
        }

        return value.negate();
    }

    private static BigDecimal _calculateValues(Map<Key, Integer> blockCounts, boolean calculateWorth) {
        BigDecimal value = BigDecimal.ZERO;

        for (Map.Entry<Key, Integer> blockEntry : blockCounts.entrySet()) {
            BigDecimal blockValue = calculateWorth ? plugin.getBlockValues().getBlockWorth(blockEntry.getKey()) :
                    plugin.getBlockValues().getBlockLevel(blockEntry.getKey());
            if (blockValue.compareTo(BigDecimal.ZERO) > 0) {
                value = value.add(blockValue.multiply(new BigDecimal(blockEntry.getValue())));
            }
        }

        return value;
    }

}
