package studio.modroll.critfall.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FormattedCharSequence;
import studio.modroll.critfall.Critfall;
import studio.modroll.critfall.config.RulesEditor;
import studio.modroll.critfall.config.RulesPresets;
import studio.modroll.critfall.config.RulesSchema;
import studio.modroll.critfall.config.RulesSchema.Setting;
import studio.modroll.critfall.data.RulesReloadListener;

/**
 * Edits {@code config/critfall/rules.json}, with a preset picker on top. The main page shows only the
 * few switches of {@link RulesSchema#MAIN}; its All settings button opens every key, grouped by
 * section, on a page of its own that edits a copy: Cancel there returns to the main page as it was,
 * Save there saves everything and leaves both pages. Save
 * writes the file and loads it through {@link RulesReloadListener#reload}, the same path as
 * {@code /reload}, so a singleplayer or LAN world plays by the new rules at once. Opened from
 * NeoForge's mod list and from Mod Menu on Fabric.
 *
 * <p>On a server the rules come from the server's own file, which this game cannot see, so
 * {@link #forCurrentGame} opens {@link ServerRulesScreen} instead.
 */
public final class RulesScreen extends Screen {

    private static final String LANG = "critfall.config.";
    private static final String CUSTOM = "custom";
    private static final int ROW_WIDTH = 310;
    private static final int CONTROL_WIDTH = 120;
    private static final int ROW_HEIGHT = 24;
    private static final int CONTROL_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 64;
    private static final int FOOTER_HEIGHT = 33;
    private static final int SPACING = 4;
    private static final int BUTTON_WIDTH = 150;
    /** Three footer buttons across the row on the main page. */
    private static final int SMALL_BUTTON_WIDTH = 98;

    private static final int LABEL_GAP = 8;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int DIM_COLOR = 0xA0A0A0;
    private static final int INVALID_COLOR = 0xFF5555;
    private static final int FIELD_LENGTH = 16;

    private final Screen parent;
    /** Where Save leads: the parent, or for the All settings page, the main page's parent. */
    private final Screen afterSave;

    private final Path file;
    private final RulesEditor editor;
    private final boolean allSettings;
    private final Map<String, String> presets = new LinkedHashMap<>();
    private final Set<Setting> invalid = new HashSet<>();

    private Component status;
    private HeaderAndFooterLayout layout;
    private SettingsList list;
    private CycleButton<String> preset;
    private Button save;

    /** The main page. */
    public RulesScreen(Screen parent, Path file) {
        this(parent, parent, file, RulesEditor.read(file), false);
    }

    private RulesScreen(Screen parent, Screen afterSave, Path file, RulesEditor editor, boolean allSettings) {
        super(Component.translatable(LANG + "title"));
        this.parent = parent;
        this.afterSave = afterSave;
        this.file = file;
        this.editor = editor;
        this.allSettings = allSettings;
        RulesPresets.IDS.forEach(id -> presets.put(id, RulesPresets.text(id)));
        this.status = editor.unreadable()
                ? Component.translatable(LANG + "status.unreadable").withStyle(ChatFormatting.RED)
                : Component.translatable(LANG + "status.applies");
    }

    /**
     * This screen, or {@link ServerRulesScreen} while connected to a server: the rules then come from
     * the server's file, so this game's file would only mislead.
     */
    public static Screen forCurrentGame(Screen parent, Path file) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && minecraft.getSingleplayerServer() == null) {
            return new ServerRulesScreen(parent);
        }
        return new RulesScreen(parent, file);
    }

    @Override
    protected void init() {
        double scroll = list == null ? 0 : list.getScrollAmount();
        layout = new HeaderAndFooterLayout(this, HEADER_HEIGHT, FOOTER_HEIGHT);

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(SPACING));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        preset = header.addChild(presetButton());
        header.addChild(new StringWidget(status, font).setColor(DIM_COLOR));

        list = layout.addToContents(new SettingsList(minecraft, width, layout));
        if (allSettings) {
            RulesSchema.bySection().forEach((section, settings) -> {
                list.add(new SectionRow(
                        Component.translatable(LANG + "section." + section).withStyle(ChatFormatting.YELLOW)));
                settings.forEach(setting -> list.add(new SettingRow(setting, control(setting))));
            });
        } else {
            RulesSchema.MAIN.forEach(setting -> list.add(new SettingRow(setting, control(setting))));
        }

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(LABEL_GAP));
        int buttonWidth = allSettings ? BUTTON_WIDTH : SMALL_BUTTON_WIDTH;
        if (!allSettings) {
            footer.addChild(Button.builder(Component.translatable(LANG + "all_settings"), button -> openAllSettings())
                    .width(buttonWidth)
                    .tooltip(Tooltip.create(Component.translatable(LANG + "all_settings.tooltip")))
                    .build());
        }
        save = footer.addChild(Button.builder(Component.translatable(LANG + "save"), button -> save())
                .width(buttonWidth)
                .tooltip(Tooltip.create(Component.translatable(LANG + "save.tooltip")))
                .build());
        footer.addChild(Button.builder(Component.translatable(LANG + "cancel"), button -> onClose())
                .width(buttonWidth)
                .build());

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        list.setScrollAmount(scroll);
        refresh();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        list.updateSize(width, layout);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** Every key, on a copy of what this page holds so far, saved together from there. */
    private void openAllSettings() {
        minecraft.setScreen(new RulesScreen(this, parent, file, editor.copy(), true));
    }

    /** The preset the file matches, or the custom marker when it matches none. */
    private String matchingPreset() {
        return presets.entrySet().stream()
                .filter(entry -> editor.matches(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(CUSTOM);
    }

    private CycleButton<String> presetButton() {
        List<String> ids = RulesPresets.IDS;
        CycleButton<String> button = CycleButton.<String>builder(
                        id -> Component.translatable(LANG + "preset", Component.translatable(LANG + "preset." + id)))
                .withValues(ids)
                // Start on the last preset, so the first press lands on the first whatever is shown.
                .withInitialValue(ids.getLast())
                .displayOnlyValue()
                .withTooltip(id -> Tooltip.create(Component.translatable(LANG + "preset.tooltip")))
                .create(0, 0, ROW_WIDTH, CONTROL_HEIGHT, Component.translatable(LANG + "preset.name"), (b, id) -> {
                    editor.applyPreset(presets.get(id));
                    invalid.clear();
                    rebuildWidgets();
                });
        button.setValue(matchingPreset());
        return button;
    }

    private AbstractWidget control(Setting setting) {
        Component label = label(setting);
        Tooltip tooltip = Tooltip.create(tooltip(setting));
        String value = editor.value(setting);
        return switch (setting) {
            case RulesSchema.Toggle toggle ->
                CycleButton.booleanBuilder(Component.translatable(LANG + "on"), Component.translatable(LANG + "off"))
                        .withInitialValue(Boolean.parseBoolean(value))
                        .displayOnlyValue()
                        .withTooltip(on -> tooltip)
                        .create(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, label, (b, on) -> changed(toggle, on.toString()));
            case RulesSchema.Choice choice ->
                CycleButton.<String>builder(option -> Component.translatable(LANG + "choice." + option))
                        .withValues(choice.choices())
                        .withInitialValue(value)
                        .displayOnlyValue()
                        .withTooltip(option -> tooltip)
                        .create(0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, label, (b, option) -> changed(choice, option));
            default -> field(setting, label, tooltip, value);
        };
    }

    /** A text field for a number or dice; input the loader would reject turns red and is not kept. */
    private EditBox field(Setting setting, Component label, Tooltip tooltip, String value) {
        EditBox box = new EditBox(font, 0, 0, CONTROL_WIDTH, CONTROL_HEIGHT, label);
        box.setMaxLength(FIELD_LENGTH);
        box.setValue(value);
        box.setTooltip(tooltip);
        box.setResponder(text -> {
            if (editor.set(setting, text)) {
                invalid.remove(setting);
                box.setTextColor(EditBox.DEFAULT_TEXT_COLOR);
                box.setTooltip(tooltip);
            } else {
                invalid.add(setting);
                box.setTextColor(INVALID_COLOR);
                box.setTooltip(Tooltip.create(problem(setting).withStyle(ChatFormatting.RED)));
            }
            refresh();
        });
        return box;
    }

    private void changed(Setting setting, String value) {
        editor.set(setting, value);
        refresh();
    }

    /** Save waits for every field to hold a value the loader accepts; the preset follows the file. */
    private void refresh() {
        save.active = invalid.isEmpty();
        preset.setValue(matchingPreset());
    }

    private void save() {
        try {
            editor.save(file);
        } catch (IOException e) {
            Critfall.LOG.error("Could not save {}", file, e);
            status = Component.translatable(LANG + "status.save_failed").withStyle(ChatFormatting.RED);
            rebuildWidgets();
            return;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> RulesReloadListener.reload(file));
        } else {
            RulesReloadListener.reload(file);
        }
        minecraft.setScreen(afterSave);
    }

    private static Component label(Setting setting) {
        return Component.translatable(LANG + "rules." + setting.key());
    }

    private static Component tooltip(Setting setting) {
        return Component.translatable(LANG + "rules." + setting.key() + ".tooltip");
    }

    private static MutableComponent problem(Setting setting) {
        return switch (setting) {
            case RulesSchema.WholeNumber number
            when number.unbounded() -> Component.translatable(LANG + "invalid.at_least", number.min());
            case RulesSchema.WholeNumber number ->
                Component.translatable(LANG + "invalid.range", number.min(), number.max());
            case RulesSchema.Decimal decimal -> Component.translatable(LANG + "invalid.positive");
            case RulesSchema.Dice dice -> Component.translatable(LANG + "invalid.dice");
            default -> Component.translatable(LANG + "invalid.choice");
        };
    }

    /** The scrolling rows between the header and the footer. */
    private static final class SettingsList extends ContainerObjectSelectionList<Row> {

        SettingsList(Minecraft minecraft, int width, HeaderAndFooterLayout layout) {
            super(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight(), ROW_HEIGHT);
            centerListVertically = false;
        }

        void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return ROW_WIDTH;
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {}

    /** A section's name across the row. */
    private final class SectionRow extends Row {

        private final Component name;

        SectionRow(Component name) {
            this.name = name;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovering,
                float partialTick) {
            graphics.drawCenteredString(font, name, left + width / 2, top + (height - font.lineHeight) / 2, TEXT_COLOR);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** A setting's name on the left, its control on the right; the name shows the tooltip too. */
    private final class SettingRow extends Row {

        private final Setting setting;
        private final AbstractWidget control;

        SettingRow(Setting setting, AbstractWidget control) {
            this.setting = setting;
            this.control = control;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovering,
                float partialTick) {
            int labelWidth = width - CONTROL_WIDTH - LABEL_GAP;
            int labelY = top + (CONTROL_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, fitted(font, label(setting), labelWidth), left, labelY, TEXT_COLOR);
            control.setPosition(left + width - CONTROL_WIDTH, top);
            control.render(graphics, mouseX, mouseY, partialTick);
            if (hovering && mouseX < left + labelWidth && mouseY < top + CONTROL_HEIGHT) {
                setTooltipForNextRenderPass(Tooltip.create(tooltip(setting)).toCharSequence(minecraft));
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(control);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(control);
        }
    }

    /** The label, cut short with an ellipsis when a long translation would run under the control. */
    private static FormattedCharSequence fitted(Font font, Component label, int width) {
        if (font.width(label) <= width) {
            return label.getVisualOrderText();
        }
        String ellipsis = "...";
        String cut = font.plainSubstrByWidth(label.getString(), width - font.width(ellipsis));
        return Component.literal(cut + ellipsis).getVisualOrderText();
    }
}
