package com.bgsoftware.superiorskyblock.core.menu;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.api.menu.layout.MenuLayout;
import com.bgsoftware.superiorskyblock.api.menu.parser.MenuParser;
import com.bgsoftware.superiorskyblock.api.menu.view.MenuView;
import com.bgsoftware.superiorskyblock.api.world.GameSound;
import com.bgsoftware.superiorskyblock.core.menu.layout.AbstractMenuLayout;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;

public class MenuParseResult<V extends MenuView<V, ?>> implements MenuParser.ParseResult<V> {

    private final AbstractMenuLayout.AbstractBuilder<V> menuLayoutBuilder;
    private final GameSound openingSound;
    private final boolean isPreviousMoveAllowed;
    private final boolean isSkipOneItem;
    private final MenuPatternSlots patternSlots;
    private final YamlConfiguration config;

    public MenuParseResult(AbstractMenuLayout.AbstractBuilder<V> menuLayoutBuilder) {
        this(menuLayoutBuilder, null, true, false, null, null);
    }

    public MenuParseResult(AbstractMenuLayout.AbstractBuilder<V> menuLayoutBuilder, @Nullable GameSound openingSound,
                           boolean isPreviousMoveAllowed, boolean isSkipOneItem,
                           MenuPatternSlots patternSlots, YamlConfiguration config) {
        this.menuLayoutBuilder = menuLayoutBuilder;
        this.openingSound = openingSound;
        this.isPreviousMoveAllowed = isPreviousMoveAllowed;
        this.isSkipOneItem = isSkipOneItem;
        this.patternSlots = patternSlots;
        this.config = config;
    }

    @Override
    public AbstractMenuLayout.AbstractBuilder<V> getLayoutBuilder() {
        return menuLayoutBuilder;
    }

    @Nullable
    public GameSound getOpeningSound() {
        return openingSound;
    }

    @Override
    public boolean isPreviousMoveAllowed() {
        return isPreviousMoveAllowed;
    }

    @Override
    public boolean isSkipOneItem() {
        return isSkipOneItem;
    }

    @Override
    public List<Integer> getSlotsForChar(char ch) {
        return patternSlots.getSlots(ch).handle();
    }

    public MenuPatternSlots getPatternSlots() {
        return patternSlots;
    }

    public YamlConfiguration getConfig() {
        return config;
    }

}
