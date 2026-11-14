package com.mbkeys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mbkeys.mixin.KeyMappingAccessor;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * MB Keys: lets you bind extra keys to any vanilla (or modded) key binding.
 * Extra keys are stored in config/mbkeys.json as { "key.jump": ["k:70", "m:3"] }
 * where "k:" is a keyboard key and "m:" is a mouse button (GLFW codes).
 */
public class MbKeysClient implements ClientModInitializer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, List<String>> EXTRAS = new LinkedHashMap<>();
    private static final Set<String> IGNORED = new HashSet<>();
    private static final List<String> ALL_IDS = new ArrayList<>();
    private static String capturing = null;
    private static int capturingIndex = -1;
    private static Runnable onCaptureEnd = null;

    static {
        for (int i = 32; i <= 348; i++) ALL_IDS.add("k:" + i);
        for (int i = 0; i < 8; i++) ALL_IDS.add("m:" + i);
    }

    @Override
    public void onInitializeClient() {
        loadConfig();
        ClientTickEvents.START_CLIENT_TICK.register(MbKeysClient::tick);
        InventoryButtons.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    // ---------------------------------------------------------- public api

    /** The id of a key binding, e.g. "key.jump". (Renamed from getTranslationKey in 1.21.9+.) */
    public static String idOf(KeyMapping kb) {
        return kb.getName();
    }

    public static List<String> extrasOf(String action) {
        List<String> l = EXTRAS.get(action);
        return l == null ? List.of() : l;
    }

    public static boolean isCapturing() { return capturing != null; }

    /** index -1 means "adding a new key". */
    public static boolean isCapturing(String action, int index) {
        return capturing != null && capturing.equals(action) && capturingIndex == index;
    }

    public static void beginCapture(String action, int index, Runnable done) {
        Minecraft client = Minecraft.getInstance();
        long win = GLFW.glfwGetCurrentContext();
        IGNORED.clear();
        for (String id : ALL_IDS) {
            if (isDown(win, id)) IGNORED.add(id);
        }
        capturing = action;
        capturingIndex = index;
        onCaptureEnd = done;
    }

    /** Stop capturing without calling the callback. */
    public static void stopCapture() {
        capturing = null;
        capturingIndex = -1;
        onCaptureEnd = null;
    }

    private static void endCapture() {
        Runnable r = onCaptureEnd;
        stopCapture();
        if (r != null) r.run();
    }

    public static void removeExtra(String action, int index) {
        List<String> l = EXTRAS.get(action);
        if (l == null || index < 0 || index >= l.size()) return;
        l.remove(index);
        if (l.isEmpty()) {
            EXTRAS.remove(action);
        }
        saveConfig();
    }

    // ---------------------------------------------------------------- tick

    /** Only used while you are choosing a new extra key. Normal use is handled instantly by the event hooks below. */
    private static void tick(Minecraft client) {
        if (client.options == null) return;
        if (capturing != null) pollCapture(client, GLFW.glfwGetCurrentContext());
    }

    // ------------------------------------------- instant hooks (called by KeyMappingMixin)

    /** "k:70" for a keyboard key, "m:2" for a mouse button, null for anything else. */
    private static String idOfKey(InputConstants.Key key) {
        String tk = key.getName();
        if (tk.startsWith("key.mouse.")) return "m:" + key.getValue();
        if (tk.startsWith("key.keyboard.")) return "k:" + key.getValue();
        return null;
    }

    /** Runs at the exact moment vanilla registers a key press, so extra keys are as fast as normal ones. */
    public static void onExtraKeyPressed(InputConstants.Key key) {
        if (capturing != null || EXTRAS.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;
        String id = idOfKey(key);
        if (id == null) return;
        for (KeyMapping kb : client.options.keyMappings) {
            List<String> list = EXTRAS.get(idOf(kb));
            if (list != null && list.contains(id)) {
                KeyMappingAccessor acc = (KeyMappingAccessor) kb;
                acc.mbkeys$setClickCount(acc.mbkeys$getClickCount() + 1);
            }
        }
    }

    /** Runs at the exact moment vanilla holds or releases a key. */
    public static void onExtraKeySet(InputConstants.Key key, boolean pressed) {
        if (EXTRAS.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null ) return;
        String id = idOfKey(key);
        if (id == null) return;
        long win = GLFW.glfwGetCurrentContext();
        for (KeyMapping kb : client.options.keyMappings) {
            List<String> list = EXTRAS.get(idOf(kb));
            if (list == null || !list.contains(id)) continue;
            if (pressed) {
                if (capturing == null) kb.setDown(true);
            } else {
                boolean stillHeld = primaryDown(win, kb);
                for (String other : list) {
                    if (!other.equals(id) && isDown(win, other)) stillHeld = true;
                }
                if (!stillHeld) kb.setDown(false);
            }
        }
    }

    private static void pollCapture(Minecraft client, long win) {
        for (String id : ALL_IDS) {
            boolean down = isDown(win, id);
            if (!down) { IGNORED.remove(id); continue; }
            if (IGNORED.contains(id)) continue;

            boolean quiet = client.screen != null;
            if (id.equals("k:" + GLFW.GLFW_KEY_ESCAPE)) {
                if (!quiet) say(client, Component.literal("[MB Keys] Cancelled."), true);
                endCapture();
                return;
            }
            String action = capturing;
            int index = capturingIndex;
            List<String> list = EXTRAS.computeIfAbsent(action, k -> new ArrayList<>());
            if (index >= 0 && index < list.size()) {
                list.set(index, id);
            } else if (!list.contains(id)) {
                list.add(id);
            }
            saveConfig();
            if (!quiet) {
                say(client, Component.literal("[MB Keys] Added " + describeKey(id) + " to ")
                        .append(Component.translatable(action)), false);
            }
            endCapture();
            return;
        }
    }

    // ------------------------------------------------------------ key utils

    private static boolean isDown(long win, String id) {
        int code = Integer.parseInt(id.substring(2));
        if (id.startsWith("m:")) return GLFW.glfwGetMouseButton(win, code) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS;
    }

    /** Is the binding's normal (vanilla) key currently held? */
    private static boolean primaryDown(long win, KeyMapping kb) {
        if (kb.isUnbound()) return false;
        try {
            String tk = kb.saveString();
            int code = InputConstants.getKey(tk).getValue();
            if (tk.startsWith("key.mouse.")) return GLFW.glfwGetMouseButton(win, code) == GLFW.GLFW_PRESS;
            if (tk.startsWith("key.keyboard.")) return GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS;
        } catch (Exception ignored) { }
        return false;
    }

    public static String describeKey(String id) {
        int code = Integer.parseInt(id.substring(2));
        if (id.startsWith("m:")) {
            return switch (code) {
                case 0 -> "Left Mouse";
                case 1 -> "Right Mouse";
                case 2 -> "Middle Mouse";
                default -> "Mouse " + (code + 1);
            };
        }
        switch (code) {
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE: return "Backspace";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "Left Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "Right Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "Left Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "Right Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "Left Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "Right Alt";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            default: break;
        }
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        String n = GLFW.glfwGetKeyName(code, 0);
        if (n != null && !n.isBlank()) return n.toUpperCase();
        return "Key " + code;
    }

    static KeyMapping findBinding(Minecraft client, String translationKey) {
        for (KeyMapping kb : client.options.keyMappings) {
            if (idOf(kb).equals(translationKey)) return kb;
        }
        return null;
    }

    private static void say(Minecraft client, Component text, boolean actionBar) {
        if (client.player != null) client.player.sendOverlayMessage(text);
    }

    // -------------------------------------------------------------- config

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("mbkeys.json");
    }

    private static void loadConfig() {
        Path p = configPath();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            Type t = new TypeToken<LinkedHashMap<String, List<String>>>() { }.getType();
            Map<String, List<String>> m = GSON.fromJson(r, t);
            if (m != null) {
                EXTRAS.clear();
                for (Map.Entry<String, List<String>> e : m.entrySet()) {
                    List<String> clean = new ArrayList<>();
                    for (String id : e.getValue()) {
                        if (id != null && id.matches("[km]:\\d+")) clean.add(id);
                    }
                    if (!clean.isEmpty()) EXTRAS.put(e.getKey(), clean);
                }
            }
        } catch (Exception e) {
            System.err.println("[MB Keys] Could not read config: " + e);
        }
    }

    private static void saveConfig() {
        try {
            Files.writeString(configPath(), GSON.toJson(EXTRAS));
        } catch (IOException e) {
            System.err.println("[MB Keys] Could not save config: " + e);
        }
    }

    // ------------------------------------------------------------ commands

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        SuggestionProvider<FabricClientCommandSource> actions = (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (KeyMapping kb : Minecraft.getInstance().options.keyMappings) {
                String id = idOf(kb);
                if (id.toLowerCase().contains(typed)) builder.suggest(id);
            }
            return builder.buildFuture();
        };

        dispatcher.register(literal("mbkeys")
                .executes(ctx -> openScreen())
                .then(literal("help").executes(ctx -> help(ctx.getSource())))
                .then(literal("list").executes(ctx -> list(ctx.getSource())))
                .then(literal("add")
                        .then(argument("action", StringArgumentType.word()).suggests(actions)
                                .executes(ctx -> add(ctx.getSource(), StringArgumentType.getString(ctx, "action")))))
                .then(literal("remove")
                        .then(argument("action", StringArgumentType.word()).suggests(actions)
                                .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "action")))))
                .then(literal("cancel").executes(ctx -> cancel(ctx.getSource()))));
    }

    private static int help(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("MB Keys commands:"));
        src.sendFeedback(Component.literal(" /mbkeys               - open the Simple MB Keys screen"));
        src.sendFeedback(Component.literal(" /mbkeys add <action>    - then press the extra key (Esc cancels)"));
        src.sendFeedback(Component.literal(" /mbkeys remove <action> - remove all extra keys from an action"));
        src.sendFeedback(Component.literal(" /mbkeys list            - show your extra keys"));
        src.sendFeedback(Component.literal(" Example: /mbkeys add key.jump"));
        return 1;
    }

    private static int list(FabricClientCommandSource src) {
        if (EXTRAS.isEmpty()) {
            src.sendFeedback(Component.literal("No extra keys yet. Try /mbkeys add key.jump"));
            return 1;
        }
        for (Map.Entry<String, List<String>> e : EXTRAS.entrySet()) {
            StringBuilder sb = new StringBuilder(" -> ");
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(describeKey(e.getValue().get(i)));
            }
            src.sendFeedback(Component.translatable(e.getKey()).append(Component.literal(sb.toString())));
        }
        return 1;
    }

    private static int openScreen() {
        Minecraft client = Minecraft.getInstance();
        client.tell(() -> client.setScreen(new MbKeysScreen(null)));
        return 1;
    }

    private static int add(FabricClientCommandSource src, String action) {
        Minecraft client = Minecraft.getInstance();
        if (findBinding(client, action) == null) {
            src.sendFeedback(Component.literal("Unknown action: " + action + " (use Tab to autocomplete)"));
            return 0;
        }
        beginCapture(action, -1, null);
        say(client, Component.literal("[MB Keys] Press the extra key for ")
                .append(Component.translatable(action)).append(Component.literal(" (Esc to cancel)")), true);
        return 1;
    }

    private static int remove(FabricClientCommandSource src, String action) {
        List<String> removed = EXTRAS.remove(action);
        if (removed == null) {
            src.sendFeedback(Component.literal("No extra keys on " + action));
            return 0;
        }
        saveConfig();
        src.sendFeedback(Component.literal("Removed extra keys from ").append(Component.translatable(action)));
        return 1;
    }

    private static int cancel(FabricClientCommandSource src) {
        stopCapture();
        src.sendFeedback(Component.literal("Cancelled."));
        return 1;
    }
}
