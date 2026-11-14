package com.mbkeys;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.text.Text;

/**
 * Four small vanilla-looking buttons around the inventory panel, one per corner.
 * Only the top-right one does anything: it opens the Simple MB Keys screen.
 */
public final class InventoryButtons {
    private static final int SIZE = 10;     // small
    private static final int GAP = 2;
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 166;

    private InventoryButtons() { }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof InventoryScreen)) return;

            ButtonWidget topLeft = decoy();
            ButtonWidget bottomLeft = decoy();
            ButtonWidget bottomRight = decoy();
            ButtonWidget topRight = ButtonWidget.builder(Text.empty(),
                    b -> client.setScreen(new MbKeysScreen(screen)))
                    .dimensions(0, 0, SIZE, SIZE).build();

            var buttons = Screens.getButtons(screen);
            buttons.add(topLeft);
            buttons.add(topRight);
            buttons.add(bottomLeft);
            buttons.add(bottomRight);

            // Re-place every frame so they follow the panel when the recipe book opens/closes.
            ScreenEvents.beforeRender(screen).register((s, ctx, mouseX, mouseY, delta) -> {
                int[] o = origin(s, screen.width, screen.height);
                int left = o[0] - SIZE - GAP;
                int right = o[0] + PANEL_W + GAP;
                int top = o[1];
                int bottom = o[1] + PANEL_H - SIZE;
                put(topLeft, left, top);
                put(topRight, right, top);
                put(bottomLeft, left, bottom);
                put(bottomRight, right, bottom);
            });
        });
    }

    private static ButtonWidget decoy() {
        return ButtonWidget.builder(Text.empty(), b -> { })
                .dimensions(0, 0, SIZE, SIZE).build();
    }

    private static void put(ClickableWidget w, int x, int y) {
        w.setX(x);
        w.setY(y);
    }

    /**
     * Top-left corner of the inventory panel. The recipe-book button sits at a fixed spot
     * inside the panel (104, 61), so we read where it is. If it isn't found, assume the panel is centered.
     */
    private static int[] origin(Screen screen, int sw, int sh) {
        for (ClickableWidget w : Screens.getButtons(screen)) {
            if (w instanceof TexturedButtonWidget && w.getWidth() == 20 && w.getHeight() == 18) {
                return new int[] { w.getX() - 104, w.getY() - 61 };
            }
        }
        return new int[] { (sw - PANEL_W) / 2, (sh - PANEL_H) / 2 };
    }
}
