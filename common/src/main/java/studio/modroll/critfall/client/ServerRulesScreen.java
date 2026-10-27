package studio.modroll.critfall.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What the config button opens while connected to a server. The server plays by its own
 * {@code rules.json}, which never reaches this game, and this game's own file would only mislead, so
 * the screen shows no values: just a line saying the server sets the rules, and Done.
 */
public final class ServerRulesScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int TEXT_WIDTH = 310;

    private final Screen parent;
    private HeaderAndFooterLayout layout;

    public ServerRulesScreen(Screen parent) {
        super(Component.translatable("critfall.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        layout.addToContents(new MultiLineTextWidget(Component.translatable("critfall.config.status.server"), font)
                .setMaxWidth(TEXT_WIDTH)
                .setCentered(true));
        layout.addToFooter(Button.builder(Component.translatable("critfall.config.done"), button -> onClose())
                .width(BUTTON_WIDTH)
                .build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
