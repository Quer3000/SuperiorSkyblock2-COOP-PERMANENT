package com.bgsoftware.superiorskyblock.commands.command;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPermission;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.utils.StringUtils;
import com.bgsoftware.superiorskyblock.wrappers.SSuperiorPlayer;
import com.bgsoftware.superiorskyblock.Locale;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.commands.ICommand;
import com.bgsoftware.superiorskyblock.hooks.EconomyHook;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CmdDeposit implements ICommand {

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("deposit");
    }

    @Override
    public String getPermission() {
        return "superior.island.deposit";
    }

    @Override
    public String getUsage() {
        return "deposit <" + Locale.COMMAND_ARGUMENT_AMOUNT.getMessage() + ">";
    }

    @Override
    public String getDescription() {
        return Locale.COMMAND_DESCRIPTION_DEPOSIT.getMessage();
    }

    @Override
    public int getMinArgs() {
        return 2;
    }

    @Override
    public int getMaxArgs() {
        return 2;
    }

    @Override
    public boolean canBeExecutedByConsole() {
        return false;
    }

    @Override
    public void execute(SuperiorSkyblockPlugin plugin, CommandSender sender, String[] args) {
        SuperiorPlayer superiorPlayer = SSuperiorPlayer.of(sender);
        Island island = superiorPlayer.getIsland();

        if(!EconomyHook.isVaultEnabled()){
            Locale.sendMessage(superiorPlayer, "&cServer doesn't have vault installed so island banks are disabled.");
            return;
        }

        if(island == null){
            Locale.INVALID_ISLAND.send(superiorPlayer);
            return;
        }

        if(!superiorPlayer.hasPermission(IslandPermission.DEPOSIT_MONEY)){
            Locale.NO_DEPOSIT_PERMISSION.send(superiorPlayer, island.getRequiredPlayerRole(IslandPermission.DEPOSIT_MONEY));
            return;
        }

        double amount = -1;

        try{
            amount = Double.parseDouble(args[1]);
        }catch(IllegalArgumentException ignored){}

        if(amount < 0){
            Locale.INVALID_AMOUNT.send(superiorPlayer, args[1]);
            return;
        }

        if(EconomyHook.getMoneyInBank(superiorPlayer.asPlayer()) < amount){
            Locale.NOT_ENOUGH_MONEY_TO_DEPOSIT.send(superiorPlayer, amount);
            return;
        }

        island.depositMoney(amount);
        EconomyHook.withdrawMoney(superiorPlayer.asPlayer(), amount);

        if(!Locale.DEPOSIT_ANNOUNCEMENT.isEmpty())
            island.sendMessage(Locale.DEPOSIT_ANNOUNCEMENT.getMessage(superiorPlayer.getName(), StringUtils.format(amount)));
    }

    @Override
    public List<String> tabComplete(SuperiorSkyblockPlugin plugin, CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
