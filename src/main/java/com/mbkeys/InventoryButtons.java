package com.mbkeys;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Four tiny vanilla-looking squares inside the corners of the inventory panel.
 * Three are only drawn (no hover, no click, no sound). The top-right one is a real,
 * clickable button hidden under the drawing, and it opens the SIMPLE MB KEYS screen.
 */
public final class InventoryButtons {
    private static final int SIZE = 5;      // tiny
    private static final int INSET = 2;     // distance from the panel edge
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    private InventoryButtons() { }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof InventoryScreen)) return;

            Button open = Button.builder(Component.empty(),
                    b -> client.setScreen(new MbKeysScreen(screen)))
                    .bounds(0, 0, SIZE, SIZE).build();
            Screens.getWidgets(screen).add(open);

            // Keep the real (clickable) button on the top-right square, even if the recipe book moves the panel.
            ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                int[] o = origin(s, screen.width, screen.height);
                open.setX(o[0] + PANEL_W - INSET - SIZE);
                open.setY(o[1] + INSET);
            });

            // Draw all four squares on top. Only the top-right one reacts to the mouse.
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                int[] o = origin(s, screen.width, screen.height);
                int left = o[0] + INSET;
                int right = o[0] + PANEL_W - INSET - SIZE;
                int top = o[1] + INSET;
                int bottom = o[1] + PANEL_H - INSET - SIZE;

                drawTiny(graphics, left, top, false);
                drawTiny(graphics, left, bottom, false);
                drawTiny(graphics, right, bottom, false);
                boolean hover = mouseX >= right && mouseX < right + SIZE
                        && mouseY >= top && mouseY < top + SIZE;
                drawTiny(graphics, right, top, hover);
            });
        });
    }

    /** A tiny grey bevelled square that looks like a vanilla button. */
    private static void drawTiny(GuiGraphicsExtractor g, int x, int y, boolean hover) {
        int body = hover ? 0xFF8A8A8A : 0xFF6C6C6C;
        g.fill(x, y, x + SIZE, y + SIZE, 0xFF000000);                          // outline
        g.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, body);                // body
        g.fill(x + 1, y + 1, x + SIZE - 1, y + 2, 0xFFB4B4B4);                 // top light edge
        g.fill(x + 1, y + 1, x + 2, y + SIZE - 1, 0xFFB4B4B4);                 // left light edge
        g.fill(x + 1, y + SIZE - 2, x + SIZE - 1, y + SIZE - 1, 0xFF3A3A3A);   // bottom dark edge
        g.fill(x + SIZE - 2, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF3A3A3A);   // right dark edge
    }

    /**
     * Top-left corner of the inventory panel. The recipe-book button sits at a fixed spot
     * inside the panel (104, 61), so we read where it is. If it isn't found, assume the panel is centered.
     */
    private static int[] origin(Screen screen, int sw, int sh) {
        for (AbstractWidget w : Screens.getWidgets(screen)) {
            if (w instanceof ImageButton && w.getWidth() == 20 && w.getHeight() == 18) {
                return new int[] { w.getX() - 104, w.getY() - 61 };
            }
        }
        return new int[] { (sw - PANEL_W) / 2, (sh - PANEL_H) / 2 };
    }
}
