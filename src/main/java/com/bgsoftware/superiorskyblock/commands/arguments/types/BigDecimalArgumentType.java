package com.bgsoftware.superiorskyblock.commands.arguments.types;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.commands.CommandContext;
import com.bgsoftware.superiorskyblock.api.commands.CommandSyntaxException;
import com.bgsoftware.superiorskyblock.api.commands.arguments.ArgumentsReader;
import com.bgsoftware.superiorskyblock.api.commands.arguments.CommandArgumentType;
import com.bgsoftware.superiorskyblock.core.messages.Message;

import java.math.BigDecimal;

public class BigDecimalArgumentType implements CommandArgumentType<BigDecimal, CommandContext> {

    public static final BigDecimalArgumentType INSTANCE = new BigDecimalArgumentType();

    private BigDecimalArgumentType() {

    }

    @Override
    public BigDecimal parse(SuperiorSkyblock plugin, CommandContext context, ArgumentsReader reader) throws CommandSyntaxException {
        String argument = reader.read();

        try {
            return new BigDecimal(argument);
        } catch (NumberFormatException ex) {
            Message.INVALID_AMOUNT.send(context.getDispatcher());
            throw new CommandSyntaxException("Invalid big decimal: " + argument);
        }
    }

}
