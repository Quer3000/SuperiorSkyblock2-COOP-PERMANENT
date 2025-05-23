package com.bgsoftware.superiorskyblock.island.signs;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.Materials;
import com.bgsoftware.superiorskyblock.core.ObjectsPools;
import com.bgsoftware.superiorskyblock.core.events.args.PluginEventArgs;
import com.bgsoftware.superiorskyblock.core.events.plugin.PluginEvent;
import com.bgsoftware.superiorskyblock.core.events.plugin.PluginEventsFactory;
import com.bgsoftware.superiorskyblock.core.formatting.Formatters;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.island.IslandUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.util.List;

public class IslandSigns {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private IslandSigns() {

    }

    public static Result handleSignPlace(SuperiorPlayer superiorPlayer, Location warpLocation, String[] warpLines,
                                         boolean sendMessage) {
        Island island = plugin.getGrid().getIslandAt(warpLocation);
        if (island == null)
            return new Result(Reason.NOT_IN_ISLAND, false);

        superiorPlayer.runIfOnline(player -> {
            try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
                warpLocation.setYaw(player.getLocation(wrapper.getHandle()).getYaw());
            }
        });

        if (isWarpSign(warpLines[0])) {
            Reason reason = handleWarpSignPlace(superiorPlayer, island, warpLocation, warpLines, sendMessage);
            return new Result(reason, true);
        } else if (isVisitorsSign(warpLines[0])) {
            return handleVisitorsSignPlace(superiorPlayer, island, warpLocation, warpLines, sendMessage);
        }

        return new Result(Reason.SUCCESS, false);
    }

    public static Result handleSignBreak(SuperiorPlayer superiorPlayer, Sign sign) {
        try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
            Location signLocation = sign.getLocation(wrapper.getHandle());
            Island island = plugin.getGrid().getIslandAt(signLocation);

            if (island == null)
                return new Result(Reason.NOT_IN_ISLAND, false);

            IslandWarp islandWarp = island.getWarp(signLocation);

            if (islandWarp != null) {
                if (!PluginEventsFactory.callIslandDeleteWarpEvent(island, superiorPlayer, islandWarp))
                    return new Result(Reason.EVENT_CANCELLED, true);

                island.deleteWarp(superiorPlayer, signLocation);
            } else {
                if (sign.getLine(0).equalsIgnoreCase(plugin.getSettings().getVisitorsSign().getActive())) {
                    if (!PluginEventsFactory.callIslandRemoveVisitorHomeEvent(island, superiorPlayer))
                        return new Result(Reason.EVENT_CANCELLED, true);

                    island.setVisitorsLocation(null);
                }
            }
        }

        return new Result(Reason.SUCCESS, false);
    }

    private static Reason handleWarpSignPlace(SuperiorPlayer superiorPlayer, Island island, Location warpLocation,
                                              String[] signLines, boolean sendMessage) {
        if (island.getIslandWarps().size() >= island.getWarpsLimit()) {
            if (sendMessage)
                Message.NO_MORE_WARPS.send(superiorPlayer);

            return Reason.LIMIT_REACHED;
        }

        String warpName = Formatters.STRIP_COLOR_FORMATTER.format(signLines[1].trim());
        boolean privateFlag = signLines[2].equalsIgnoreCase("private");

        Reason result = Reason.SUCCESS;

        if (warpName.isEmpty()) {
            if (sendMessage)
                Message.WARP_ILLEGAL_NAME.send(superiorPlayer);
            result = Reason.ILLEGAL_NAME;
        } else if (island.getWarp(warpName) != null) {
            if (sendMessage)
                Message.WARP_ALREADY_EXIST.send(superiorPlayer);
            result = Reason.ALREADY_EXIST;
        } else if (!IslandUtils.isWarpNameLengthValid(warpName)) {
            if (sendMessage)
                Message.WARP_NAME_TOO_LONG.send(superiorPlayer);
            result = Reason.NAME_TOO_LONG;
        }

        if (!PluginEventsFactory.callIslandCreateWarpEvent(island, superiorPlayer, warpName, warpLocation, !privateFlag, null))
            result = Reason.EVENT_CANCELLED;

        if (result != Reason.SUCCESS)
            return result;

        List<String> signWarp = plugin.getSettings().getSignWarp();

        for (int i = 0; i < signWarp.size(); i++)
            signLines[i] = signWarp.get(i).replace("{0}", warpName);

        IslandWarp islandWarp = island.createWarp(warpName, warpLocation, null);
        islandWarp.setPrivateFlag(privateFlag);
        if (sendMessage)
            Message.SET_WARP.send(superiorPlayer, Formatters.LOCATION_FORMATTER.format(warpLocation));

        return Reason.SUCCESS;
    }

    private static Result handleVisitorsSignPlace(SuperiorPlayer superiorPlayer, Island island, Location visitorsLocation,
                                                  String[] warpLines, boolean sendMessage) {
        if (island.getIslandWarps().size() >= island.getWarpsLimit()) {
            if (sendMessage)
                Message.NO_MORE_WARPS.send(superiorPlayer);

            return new Result(Reason.LIMIT_REACHED, true);
        }

        PluginEvent<PluginEventArgs.IslandSetVisitorHome> setVisitorHomeEvent =
                PluginEventsFactory.callIslandSetVisitorHomeEvent(island, superiorPlayer, visitorsLocation);

        if (setVisitorHomeEvent.isCancelled())
            return new Result(Reason.EVENT_CANCELLED, false);

        StringBuilder descriptionBuilder = new StringBuilder();

        for (int i = 1; i < 4; i++) {
            String line = warpLines[i];
            if (!line.isEmpty())
                descriptionBuilder.append("\n").append(ChatColor.RESET).append(line);
        }

        String description = descriptionBuilder.length() < 1 ? "" : descriptionBuilder.substring(1);

        warpLines[0] = plugin.getSettings().getVisitorsSign().getActive();

        for (int i = 1; i <= 3; i++)
            warpLines[i] = Formatters.COLOR_FORMATTER.format(warpLines[i]);

        Location islandVisitorsLocation = island.getVisitorsLocation((Dimension) null /* unused */);
        Block oldWelcomeSignBlock = islandVisitorsLocation == null ? null : islandVisitorsLocation.getBlock();

        if (oldWelcomeSignBlock != null && Materials.isSign(oldWelcomeSignBlock.getType())) {
            Sign oldWelcomeSign = (Sign) oldWelcomeSignBlock.getState();
            oldWelcomeSign.setLine(0, plugin.getSettings().getVisitorsSign().getInactive());
            oldWelcomeSign.update();
        }

        island.setVisitorsLocation(setVisitorHomeEvent.getArgs().islandVisitorHome);

        PluginEvent<PluginEventArgs.IslandChangeDescription> changeDescriptionEvent =
                PluginEventsFactory.callIslandChangeDescriptionEvent(island, superiorPlayer, description);

        if (!changeDescriptionEvent.isCancelled())
            island.setDescription(changeDescriptionEvent.getArgs().description);

        if (sendMessage)
            Message.SET_WARP.send(superiorPlayer, Formatters.LOCATION_FORMATTER.format(visitorsLocation));

        return new Result(Reason.SUCCESS, true);
    }

    private static boolean isWarpSign(String firstSignLine) {
        return firstSignLine.equalsIgnoreCase(plugin.getSettings().getSignWarpLine());
    }

    private static boolean isVisitorsSign(String firstSignLine) {
        return firstSignLine.equalsIgnoreCase(plugin.getSettings().getVisitorsSign().getLine());
    }

    public enum Reason {

        NOT_IN_ISLAND,
        ILLEGAL_NAME,
        ALREADY_EXIST,
        NAME_TOO_LONG,
        LIMIT_REACHED,
        EVENT_CANCELLED,
        SUCCESS

    }

    public static class Result {

        private final Reason reason;
        private final boolean cancelEvent;

        public Result(Reason reason, boolean cancelEvent) {
            this.reason = reason;
            this.cancelEvent = cancelEvent;
        }

        public Reason getReason() {
            return reason;
        }

        public boolean isCancelEvent() {
            return cancelEvent;
        }

    }

}
