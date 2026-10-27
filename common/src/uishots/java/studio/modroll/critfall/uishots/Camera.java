package studio.modroll.critfall.uishots;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;

/**
 * Renders at a fixed 1920×1080 whatever size the window manager gives the window, places the mouse
 * and saves the last rendered frame.
 */
final class Camera {

    static final int WIDTH = 1920;
    static final int HEIGHT = 1080;

    private final Path directory;

    Camera(Path directory) {
        this.directory = directory;
    }

    /**
     * A tiling window manager resizes the window, and GLFW reports each resize; forcing the size back
     * every tick keeps the frames 1920×1080.
     */
    static void forceSize() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        if (window.getWidth() == WIDTH && window.getHeight() == HEIGHT && window.getScreenWidth() == WIDTH) {
            return;
        }
        for (String field : new String[] {"width", "framebufferWidth"}) {
            Fields.set(Window.class, window, field, WIDTH);
        }
        for (String field : new String[] {"height", "framebufferHeight"}) {
            Fields.set(Window.class, window, field, HEIGHT);
        }
        minecraft.resizeDisplay();
    }

    static void guiScale(int scale) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.guiScale().set(scale);
        minecraft.resizeDisplay();
    }

    /** Puts the mouse on a point in GUI coordinates, as the next frames' screen sees it. */
    static void mouseAt(int guiX, int guiY) {
        Minecraft minecraft = Minecraft.getInstance();
        double scale = minecraft.getWindow().getGuiScale();
        Fields.set(MouseHandler.class, minecraft.mouseHandler, "xpos", (guiX + 0.5) * scale);
        Fields.set(MouseHandler.class, minecraft.mouseHandler, "ypos", (guiY + 0.5) * scale);
    }

    void save(String name) {
        Path file = directory.resolve(name + ".png");
        try (NativeImage image =
                Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            Files.createDirectories(directory);
            image.writeToFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
