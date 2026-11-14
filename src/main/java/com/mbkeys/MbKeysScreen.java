package com.mbkeys;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** Screen: [+] [action + normal key]   then   [trash] [extra key] rows under it. */
public class MbKeysScreen extends Screen {
    private static final int ROW_H = 22;
    private final Screen parent;
    private int page = 0;

    public MbKeysScreen(Screen parent) {
        super(Text.literal("Simple MB Keys"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x0 = cx - 150;

        addDrawableChild(ButtonWidget.builder(Text.literal("Simple MB Keys"), b -> { })
                .dimensions(cx - 100, 6, 200, 20).build()).active = false;

        int pageRows = Math.max(3, (this.height - 34 - 58) / ROW_H);
        List<List<KeyBinding>> pages = new ArrayList<>();
        List<KeyBinding> cur = new ArrayList<>();
        int used = 0;
        for (KeyBinding kb : this.client.options.allKeys) {
            int rows = 1 + MbKeysClient.extrasOf(kb.getTranslationKey()).size();
            if (!cur.isEmpty() && used + rows > pageRows) {
                pages.add(cur);
                cur = new ArrayList<>();
                used = 0;
            }
            cur.add(kb);
            used += rows;
        }
        if (!cur.isEmpty()) pages.add(cur);
        int pageCount = Math.max(1, pages.size());
        page = Math.max(0, Math.min(page, pageCount - 1));

        int y = 34;
        if (!pages.isEmpty()) {
            for (KeyBinding kb : pages.get(page)) {
                final String name = kb.getTranslationKey();

                // header row: [+] [Action name (normal key)]
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
                    setFocused(null);
                    MbKeysClient.beginCapture(name, -1, this::clearAndInit);
                    clearAndInit();
                }).dimensions(x0, y, 20, 20).build());

                Text label = MbKeysClient.isCapturing(name, -1)
                        ? Text.literal("Press a key... (Esc cancels)").formatted(Formatting.YELLOW)
                        : Text.translatable(name).append(Text.literal("   ("))
                            .append(Text.translatable(kb.getBoundKeyTranslationKey())).append(Text.literal(")"));
                addDrawableChild(ButtonWidget.builder(label, b -> { })
                        .dimensions(x0 + 24, y, 276, 20).build()).active = false;
                y += ROW_H;

                // extra key rows: [trash] [key]
                List<String> extras = MbKeysClient.extrasOf(name);
                for (int i = 0; i < extras.size(); i++) {
                    final int idx = i;
                    addDrawableChild(ButtonWidget.builder(Text.literal("X").formatted(Formatting.RED), b -> {
                        MbKeysClient.removeExtra(name, idx);
                        clearAndInit();
                    }).dimensions(x0, y, 20, 20).build());

                    Text keyText = MbKeysClient.isCapturing(name, idx)
                            ? Text.literal("> Press a key <").formatted(Formatting.YELLOW)
                            : Text.literal(MbKeysClient.describeKey(extras.get(i)));
                    addDrawableChild(ButtonWidget.builder(keyText, b -> {
                        setFocused(null);
                        MbKeysClient.beginCapture(name, idx, this::clearAndInit);
                        clearAndInit();
                    }).dimensions(x0 + 24, y, 276, 20).build());
                    y += ROW_H;
                }
            }
        }

        // bottom bar
        int by = this.height - 52;
        addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), b -> {
            page--;
            clearAndInit();
        }).dimensions(cx - 125, by, 80, 20).build()).active = page > 0;

        addDrawableChild(ButtonWidget.builder(Text.literal((page + 1) + " / " + pageCount), b -> { })
                .dimensions(cx - 40, by, 80, 20).build()).active = false;

        addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), b -> {
            page++;
            clearAndInit();
        }).dimensions(cx + 45, by, 80, 20).build()).active = page < pageCount - 1;

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(cx - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !MbKeysClient.isCapturing();
    }

    @Override
    public void close() {
        MbKeysClient.stopCapture();
        this.client.setScreen(parent);
    }
}
