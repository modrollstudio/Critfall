package studio.modroll.critfall.uishots;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import studio.modroll.critfall.client.RulesScreen;
import studio.modroll.critfall.client.ServerRulesScreen;
import studio.modroll.critfall.config.RulesSchema;
import studio.modroll.critfall.config.RulesSchema.Setting;

/**
 * Dev-only screenshot run: opens the rules screen from the title screen and saves it at several GUI
 * scales: the main page, with a tooltip; the All settings page scrolled, with a tooltip, with a
 * rejected value and on a picked preset; and the screen a server connection opens instead, then
 * quits without saving. Each loader's {@code runUiShots} task calls {@link #tick} every client tick
 * and names the output folder with the {@value #DIRECTORY_PROPERTY} system property.
 */
public final class UiShots {

    public static final String DIRECTORY_PROPERTY = "critfall.uishots.dir";

    private static final List<Integer> GUI_SCALES = List.of(2, 3, 4);
    /** Long enough for a resize or a screen change to reach a rendered frame. */
    private static final int SETTLE_TICKS = 3;
    /** The rows' height in {@link RulesScreen}. */
    private static final int ROW_HEIGHT = 24;

    private static final String RULES_LABEL = "critfall.config.rules.";
    private static final String DC = "fumbles.confirmation_roll.dc";
    private static final String WEAPON_DAMAGE = "fumbles.durability_break.enabled";
    private static final String ALL_SETTINGS = "critfall.config.all_settings";

    private static ShotScript script;

    private UiShots() {}

    public static Optional<Path> directory() {
        return Optional.ofNullable(System.getProperty(DIRECTORY_PROPERTY)).map(Path::of);
    }

    public static void tick() {
        if (script == null) {
            script = script(new Camera(directory().orElseThrow()));
        }
        Camera.forceSize();
        script.tick();
    }

    private static ShotScript script(Camera camera) {
        ShotScript script = new ShotScript()
                .waitFor(
                        "the first screen",
                        () -> Minecraft.getInstance().getOverlay() == null && Minecraft.getInstance().screen != null)
                .then(UiShots::quietOptions);
        for (int scale : GUI_SCALES) {
            script.then(UiShots::open);
            shoot(script, camera, scale, "rules-main");
            hover(script, camera, scale, WEAPON_DAMAGE, "rules-main-tooltip");
            script.then(() -> button(ALL_SETTINGS).onPress()).waitFor("the All settings page", () ->
                    (boolean) Fields.get(RulesScreen.class, screen(), "allSettings"));
            shoot(script, camera, scale, "rules-all-top");
            script.then(() -> scrollTo("fumbles"));
            shoot(script, camera, scale, "rules-all-fumbles");
            hover(script, camera, scale, WEAPON_DAMAGE, "rules-all-tooltip");
            script.then(() -> ((EditBox) control(DC)).setValue("25"));
            hover(script, camera, scale, DC, "rules-all-invalid-value");
            script.then(() -> ((EditBox) control(DC)).setValue("10"))
                    .then(() -> scrollTo("attack_rolls"))
                    .then(() -> presetButton().onPress());
            shoot(script, camera, scale, "rules-all-preset-classic");
            script.then(() -> Minecraft.getInstance().setScreen(new ServerRulesScreen(new TitleScreen())));
            shoot(script, camera, scale, "rules-on-a-server");
        }
        return script.then(() -> Minecraft.getInstance().stop());
    }

    /** The mouse waits in the corner so nothing under it lights up; vanilla toasts would only add noise. */
    private static void shoot(ShotScript script, Camera camera, int scale, String name) {
        script.then(() -> Camera.guiScale(scale))
                .then(() -> Camera.mouseAt(0, 0))
                .then(() -> Minecraft.getInstance().getToasts().clear())
                .waitTicks(SETTLE_TICKS)
                .then(() -> camera.save(name + "-gui" + scale));
    }

    /** The mouse over a setting's control once a frame has placed it, so its tooltip shows. */
    private static void hover(ShotScript script, Camera camera, int scale, String key, String name) {
        script.then(() -> Camera.guiScale(scale))
                .waitTicks(SETTLE_TICKS)
                .then(() -> {
                    AbstractWidget control = control(key);
                    Camera.mouseAt(control.getX() + control.getWidth() / 2, control.getY() + control.getHeight() / 2);
                })
                .waitTicks(SETTLE_TICKS)
                .then(() -> camera.save(name + "-gui" + scale));
    }

    /** No sound, no pausing when the window loses focus, no onboarding or tutorial over the screens. */
    private static void quietOptions() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        minecraft.options.pauseOnLostFocus = false;
        minecraft.options.onboardAccessibility = false;
        minecraft.options.tutorialStep = TutorialSteps.NONE;
        // A hidden window gets no frame callbacks on Wayland, and vsync would then block rendering.
        minecraft.options.enableVsync().set(false);
        minecraft.getWindow().updateVsync(false);
    }

    private static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        Path file = minecraft.gameDirectory.toPath().resolve("config/critfall/rules.json");
        minecraft.setScreen(new RulesScreen(new TitleScreen(), file));
    }

    /** Scrolls the section's heading to the top of the list. */
    private static void scrollTo(String section) {
        int row = 0;
        for (Map.Entry<String, List<Setting>> entry : RulesSchema.bySection().entrySet()) {
            if (entry.getKey().equals(section)) {
                break;
            }
            row += 1 + entry.getValue().size();
        }
        list().setScrollAmount(row * ROW_HEIGHT);
    }

    private static AbstractSelectionList<?> list() {
        return (AbstractSelectionList<?>) Fields.get(RulesScreen.class, screen(), "list");
    }

    @SuppressWarnings("unchecked")
    private static CycleButton<String> presetButton() {
        return (CycleButton<String>) Fields.get(RulesScreen.class, screen(), "preset");
    }

    /** The footer button labelled {@code key}. */
    private static Button button(String key) {
        return screen().children().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> key.equals(labelKey(button)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("UI shots found no " + key + " button"));
    }

    /** The control whose label is the setting's. */
    private static AbstractWidget control(String key) {
        return list().children().stream()
                .flatMap(row -> ((ContainerEventHandler) row).children().stream())
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> (RULES_LABEL + key).equals(labelKey(widget)))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("UI shots found no control for " + key));
    }

    /** A cycle button shows only its value; its name, like a field's, is in the narration message. */
    private static String labelKey(AbstractWidget widget) {
        Component name = widget instanceof CycleButton<?>
                ? (Component) Fields.get(CycleButton.class, widget, "name")
                : widget.getMessage();
        return name.getContents() instanceof TranslatableContents contents ? contents.getKey() : name.getString();
    }

    private static Screen screen() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof RulesScreen) {
            return screen;
        }
        throw new IllegalStateException("UI shots expected the rules screen, found " + screen);
    }
}
