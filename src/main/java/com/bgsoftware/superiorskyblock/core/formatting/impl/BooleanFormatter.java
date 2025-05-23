package com.bgsoftware.superiorskyblock.core.formatting.impl;

import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.core.formatting.IBiFormatter;

import java.util.Locale;

public class BooleanFormatter implements IBiFormatter<Boolean, Locale> {

    private static final BooleanFormatter INSTANCE = new BooleanFormatter();

    public static BooleanFormatter getInstance() {
        return INSTANCE;
    }

    private BooleanFormatter() {

    }

    @Override
    public String format(Boolean value, Locale locale) {
        return (value ? Message.PLACEHOLDER_YES : Message.PLACEHOLDER_NO).getMessage(locale);
    }

}
