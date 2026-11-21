package com.mbkeys;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Screen: [+] [action + normal key]   then   [X] [extra key] rows under it. */
public class MbKeysScreen extends Screen {
    private static final int ROW_H = 22;
    private final Screen parent;
    private int page = 0;

    public MbKeysScreen(Screen parent) {
        super(Component.literal("MB Keys"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x0 = cx - 150;

        addRenderableWidget(Button.builder(Component.literal("MB Keys"), b -> { })
                .bounds(cx - 100, 6, 200, 20).build()).active = false;

        int pageRows = Math.max(3, (this.height - 34 - 90) / ROW_H);
        List<List<KeyMapping>> pages = new ArrayList<>();
        List<KeyMapping> cur = new ArrayList<>();
        int used = 0;
        for (KeyMapping kb : this.minecraft.options.keyMappings) {
            int rows = 1 + MbKeysClient.extrasOf(MbKeysClient.idOf(kb)).size();
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
            for (KeyMapping kb : pages.get(page)) {
                final String name = MbKeysClient.idOf(kb);

                // header row: [+] [Action name (normal key)]
                addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                    setFocused(null);
                    MbKeysClient.beginCapture(name, -1, this::rebuildWidgets);
                    rebuildWidgets();
                }).bounds(x0, y, 20, 20).build());

                Component label = MbKeysClient.isCapturing(name, -1)
                        ? Component.literal("Press a key... (Esc cancels)").withStyle(ChatFormatting.YELLOW)
                        : Component.translatable(name).append(Component.literal("   ("))
                            .append(Component.translatable(kb.saveString())).append(Component.literal(")"));
                addRenderableWidget(Button.builder(label, b -> { })
                        .bounds(x0 + 24, y, 276, 20).build()).active = false;
                y += ROW_H;

                // extra key rows: [X] [key]
                List<String> extras = MbKeysClient.extrasOf(name);
                for (int i = 0; i < extras.size(); i++) {
                    final int idx = i;
                    addRenderableWidget(Button.builder(Component.literal("X").withStyle(ChatFormatting.RED), b -> {
                        MbKeysClient.removeExtra(name, idx);
                        rebuildWidgets();
                    }).bounds(x0, y, 20, 20).build());

                    Component keyText = MbKeysClient.isCapturing(name, idx)
                            ? Component.literal("> Press a key <").withStyle(ChatFormatting.YELLOW)
                            : Component.literal(MbKeysClient.describeKey(extras.get(i)));
                    addRenderableWidget(Button.builder(keyText, b -> {
                        setFocused(null);
                        MbKeysClient.beginCapture(name, idx, this::rebuildWidgets);
                        rebuildWidgets();
                    }).bounds(x0 + 24, y, 276, 20).build());
                    y += ROW_H;
                }
            }
        }

        addRenderableWidget(Button.builder(Component.literal(
                MbKeysClient.isToggleCapturing() ? "Hold your ON/OFF key combination..." :
                "Switch ON/OFF: " + MbKeysClient.toggleDescription()), b -> {
            MbKeysClient.beginToggleCapture(this::rebuildWidgets);
            rebuildWidgets();
        }).bounds(cx - 150, this.height - 76, 300, 20).build());

        // bottom bar
        int by = this.height - 52;
        addRenderableWidget(Button.builder(Component.literal("< Prev"), b -> {
            page--;
            rebuildWidgets();
        }).bounds(cx - 125, by, 80, 20).build()).active = page > 0;

        addRenderableWidget(Button.builder(Component.literal((page + 1) + " / " + pageCount), b -> { })
                .bounds(cx - 40, by, 80, 20).build()).active = false;

        addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
            page++;
            rebuildWidgets();
        }).bounds(cx + 45, by, 80, 20).build()).active = page < pageCount - 1;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !MbKeysClient.isCapturing();
    }

    @Override
    public void onClose() {
        MbKeysClient.stopCapture();
        this.minecraft.setScreen(parent);
    }
}
