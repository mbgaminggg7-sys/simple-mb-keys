package com.mbkeys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mbkeys.mixin.KeyMappingAccessor;
import net.fabricmc.api.ClientModInitializer;
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
    private static boolean enabled = true;
    private static List<String> toggleKeys = new ArrayList<>();
    private static boolean toggleLatch = false;
    private static boolean capturingToggle = false;
    private static final List<String> toggleCaptureKeys = new ArrayList<>();
    private static Runnable toggleCaptureDone;
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

    public static boolean isEnabled() { return enabled; }
    public static String toggleDescription() {
        if (toggleKeys.isEmpty()) return "Not set";
        return String.join(" + ", toggleKeys.stream().map(MbKeysClient::describeKey).toList());
    }
    public static void beginToggleCapture(Runnable done) {
        stopCapture();
        capturingToggle = true;
        toggleCaptureKeys.clear();
        toggleCaptureDone = done;
        IGNORED.clear();
        long win = GLFW.glfwGetCurrentContext();
        for (String id : ALL_IDS) if (isDown(win, id)) IGNORED.add(id);
    }
    public static boolean isToggleCapturing() { return capturingToggle; }
    public static boolean isCapturing() { return capturing != null || capturingToggle; }

    /** index -1 means "adding a new key". */
    public static boolean isCapturing(String action, int index) {
        return capturing != null && capturing.equals(action) && capturingIndex == index;
    }

    public static void beginCapture(String action, int index, Runnable done) {
        capturingToggle = false;
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
        capturingToggle = false;
        toggleCaptureDone = null;
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
        long win = GLFW.glfwGetCurrentContext();
        if (capturingToggle) pollToggleCapture(win);
        else if (capturing != null) pollCapture(client, win);
        if (!toggleKeys.isEmpty() && !capturingToggle && capturing == null) {
            boolean held = toggleKeys.stream().allMatch(id -> isDown(win, id));
            if (held && !toggleLatch) {
                enabled = !enabled;
                if (!enabled) releaseExtraKeys(client, win);
            }
            toggleLatch = held;
        } else toggleLatch = false;
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
        if (!enabled || capturing != null || capturingToggle || EXTRAS.isEmpty()) return;
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
                if (enabled && capturing == null && !capturingToggle) kb.setDown(true);
            } else {
                boolean stillHeld = primaryDown(win, kb);
                for (String other : list) {
                    if (!other.equals(id) && isDown(win, other)) stillHeld = true;
                }
                if (!stillHeld) kb.setDown(false);
            }
        }
    }

    private static void releaseExtraKeys(Minecraft client, long win) {
        for (KeyMapping kb : client.options.keyMappings) {
            if (EXTRAS.containsKey(idOf(kb)) && !primaryDown(win, kb)) kb.setDown(false);
        }
    }

    private static void pollToggleCapture(long win) {
        boolean anyDown = false;
        for (String id : ALL_IDS) {
            if (!isDown(win, id)) { IGNORED.remove(id); continue; }
            if (IGNORED.contains(id)) continue;
            anyDown = true;
            if (id.equals("k:" + GLFW.GLFW_KEY_ESCAPE)) {
                capturingToggle = false;
                toggleCaptureKeys.clear();
                Runnable done = toggleCaptureDone; toggleCaptureDone = null;
                if (done != null) done.run();
                return;
            }
            if (!toggleCaptureKeys.contains(id)) toggleCaptureKeys.add(id);
        }
        // Collect all keys until released, so combinations such as M+N work.
        if (!anyDown && !toggleCaptureKeys.isEmpty()) {
            toggleKeys = new ArrayList<>(toggleCaptureKeys);
            toggleCaptureKeys.clear();
            saveConfig();
            capturingToggle = false;
            toggleLatch = false;
            Runnable done = toggleCaptureDone; toggleCaptureDone = null;
            if (done != null) done.run();
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
        if (client.player != null) client.player.displayClientMessage(text, true);
    }

    // -------------------------------------------------------------- config

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("mbkeys.json");
    }

    private static void loadConfig() {
        Path p = configPath();
        if (!Files.exists(p)) return;
        try (Reader r = Files.newBufferedReader(p)) {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
            if (root.has("_toggle") && root.get("_toggle").isJsonArray()) {
                toggleKeys.clear();
                for (var item : root.getAsJsonArray("_toggle")) {
                    String id = item.getAsString();
                    if (id.matches("[km]:\\d+")) toggleKeys.add(id);
                }
            }
            Type t = new TypeToken<LinkedHashMap<String, List<String>>>() { }.getType();
            root.remove("_toggle");
            Map<String, List<String>> m = GSON.fromJson(root, t);
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
            Map<String, Object> config = new LinkedHashMap<>(EXTRAS);
            config.put("_toggle", toggleKeys);
            Files.writeString(configPath(), GSON.toJson(config));
        } catch (IOException e) {
            System.err.println("[MB Keys] Could not save config: " + e);
        }
    }

}
