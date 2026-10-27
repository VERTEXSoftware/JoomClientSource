package org.engine.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.engine.utils.ResourceManager;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class MainMenu {

    public enum Screen {
        Splash,
        Main,
        Settings,
        Profile,
        Servers,
        Connecting,
        Exit
    }

    public enum ClickAction {
        NONE,
        GO_PLAY,
        GO_SETTINGS,
        GO_PROFILE,
        GO_SERVERS,
        GO_MAIN,
        GO_EXIT,
        APPLY_SETTINGS,
        SAVE_PROFILE,
        DISCARD_CHANGES,
        ADD_SERVER,
        CONNECT_SERVER,
        REMOVE_SERVER,
        TOGGLE_FULLSCREEN,
        TOGGLE_VSYNC,
        MODEL_PREV,
        MODEL_NEXT,
        RES_PREV,
        RES_NEXT,
        FPS_PREV,
        FPS_NEXT,
        SLIDER_SENSITIVITY,
        SLIDER_VOLUME
    }

    private static final int[][] RESOLUTIONS = {
            {1280, 720},
            {1600, 900},
            {1920, 1080},
            {2560, 1440},
            {3840, 2160}
    };

    private static final String CONFIG_FILE = "settings.cfg";
    private static final int[] FPS_OPTIONS = {30, 60, 144};
    private static final float SENS_MIN = 0.01f;
    private static final float SENS_MAX = 2.0f;
    private static final float VOL_MIN  = 0.0f;
    private static final float VOL_MAX  = 1.0f;

    // --- Базовое разрешение для масштабирования UI ---
    private static final float BASE_WIDTH  = 1920f;
    private static final float BASE_HEIGHT = 1080f;
    private static final float SCALE_MIN   = 0.5f;
    private static final float SCALE_MAX   = 3.0f;

    private float uiScale = 1.0f;
    private int   fbWidth = 1920, fbHeight = 1080;

    // --- Splash-баннер ---
    private static final float SPLASH_DURATION = 4.0f;
    private static final float SPLASH_FADE     = 0.4f;
    private float splashElapsed = 0.0f;
    private float alphasplash   = 0.0f;
    private ByteBuffer splashBuffer;
    private int splashImage = -1;

    // --- Лимит длины строки ввода ---
    private static final int INPUT_MAX_LEN = 24;

    public static final class Settings {
        public int    width           = 1920;
        public int    height          = 1080;
        public boolean fullscreen     = false;
        public boolean vsync          = true;
        public int    fps             = 60;
        public float  mouseSensitivity= 0.3f;
        public float  masterVolume    = 1.0f;

        public String playerName      = "Player";
        public int    playerModel     = 0;
    }

    private static class ConfigData {
        Settings settings;
        List<ServerInfo> servers;
    }

    public void loadConfig() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(CONFIG_FILE);
            if (java.nio.file.Files.exists(path)) {
                String json = new String(java.nio.file.Files.readAllBytes(path), StandardCharsets.UTF_8);
                Gson gson = new Gson();
                ConfigData data = gson.fromJson(json, ConfigData.class);

                if (data != null) {
                    if (data.settings != null) {
                        settings.width = data.settings.width;
                        settings.height = data.settings.height;
                        settings.fullscreen = data.settings.fullscreen;
                        settings.vsync = data.settings.vsync;
                        settings.fps = data.settings.fps;
                        settings.mouseSensitivity = data.settings.mouseSensitivity;
                        settings.masterVolume = data.settings.masterVolume;
                        settings.playerName = data.settings.playerName;
                        settings.playerModel = data.settings.playerModel;
                    }
                    if (data.servers != null) {
                        servers.clear();
                        servers.addAll(data.servers);
                    }
                }
                System.out.println("Settings loaded successfully.");
            }
        } catch (Exception e) {
            System.err.println("Failed to load settings: " + e.getMessage());
        }
    }

    public void saveConfig() {
        try {
            ConfigData data = new ConfigData();
            data.settings = this.settings;
            data.servers = this.servers;

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(data);

            java.nio.file.Files.write(java.nio.file.Paths.get(CONFIG_FILE), json.getBytes(StandardCharsets.UTF_8));
            System.out.println("Settings saved successfully.");
        } catch (Exception e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    public static final class ServerInfo {
        public String name;
        public String ip;
        public ServerInfo() {}
        public ServerInfo(String name, String ip) {
            this.name = name;
            this.ip = ip;
        }
    }

    private long vg;
    private ByteBuffer fontBuffer;
    private static final String FONT_NAME = "menu-font";
    private static final String FONT_FILE = "font/def.ttf";


    private final Settings  settings = new Settings();
    private final List<ServerInfo> servers = new ArrayList<>();
    private final List<String> playerModels = new ArrayList<>();
    private int  selectedServer = -1;

    private String pendingServerName = "New Server";
    private String pendingServerIp = "";

    private String  inputText = "";
    private boolean inputActive = false;
    private int     inputField = 0;

    private boolean draggingSlider = false;
    private ClickAction dragSliderAction = ClickAction.NONE;

    public void onCharTyped(char c) {
        if (!inputActive) return;
        if (inputText.length() >= INPUT_MAX_LEN) return;
        if (c < 32 || c == 127) return;
        inputText += c;
    }

    public void onKeyPressed(int keyCode, int mods, int action) {
        if (!inputActive) return;

        boolean ctrl = (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
            if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                pasteFromClipboard();
            }
            return;
        }

        if (action != org.lwjgl.glfw.GLFW.GLFW_PRESS && action != org.lwjgl.glfw.GLFW.GLFW_REPEAT) {
            return;
        }

        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE:
                if (!inputText.isEmpty()) {
                    inputText = inputText.substring(0, inputText.length() - 1);
                }
                break;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER:
            case org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER:
                applyInput();
                break;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE:
                cancelInput();
                break;
            default:
                break;
        }
    }

    private void pasteFromClipboard() {
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window == 0) return;

        String clip = org.lwjgl.glfw.GLFW.glfwGetClipboardString(window);
        if (clip == null || clip.isEmpty()) return;

        int room = INPUT_MAX_LEN - inputText.length();
        if (room <= 0) return;

        StringBuilder sb = new StringBuilder(room);
        for (int i = 0; i < clip.length() && sb.length() < room; i++) {
            char c = clip.charAt(i);
            if (c >= 32 && c != 127) {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            inputText += sb;
        }
    }

    private void cancelInput() {
        inputActive = false;
        inputText = "";
        inputField = 0;
    }

    private void applyInput() {
        if (!inputActive) return;
        if (inputField == 0) {
            if (!inputText.isEmpty()) settings.playerName = inputText;
        } else if (inputField == 1) {
            if (!inputText.isEmpty()) pendingServerIp = inputText;
        } else if (inputField == 2) {
            if (!inputText.isEmpty()) pendingServerName = inputText;
        }
        inputActive = false;
        inputText = "";
    }

    private Screen screen = Screen.Main;
    private float mouseX, mouseY;

    private static final int COL_BG_TOP     = 0xFF0A0A12;
    private static final int COL_BG_BOTTOM  = 0xFF1A1A2A;
    private static final int COL_PANEL      = 0xE01A1A25;
    private static final int COL_BORDER     = 0xFF3A3A4A;
    private static final int COL_ACCENT     = 0xFFFFDC5A;
    private static final int COL_ACCENT_DIM = 0xFFAA8830;
    private static final int COL_TEXT       = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM   = 0xFFB0B0C0;
    private static final int COL_DANGER     = 0xFFE04040;
    private static final int COL_SUCCESS    = 0xFF60D060;

    public void init() throws Exception {
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == NULL) throw new Exception("Не удалось инициализировать NanoVG");

        fontBuffer = ResourceManager.getResource(FONT_FILE);
        if (fontBuffer == null) {
            throw new Exception("Файл шрифта не найден в ресурсах: " + FONT_FILE);
        }
        if (nvgCreateFontMem(vg, FONT_NAME, fontBuffer, false) == -1)
            throw new Exception("Не удалось загрузить шрифт");



        splashBuffer = ResourceManager.getResource("texture/startscreen.png");
        if (splashBuffer != null) {
            splashImage = nvgCreateImageMem(vg, 0, splashBuffer);
            if (splashImage == -1) {
                System.err.println("Не удалось загрузить баннер: texture/startscreen.png");
            }
        } else {
            System.err.println("Файл баннера не найден: texture/startscreen.png");
        }

        playerModels.add("Kinger");
        playerModels.add("Ghost");
        playerModels.add("Bishop");
        playerModels.add("Dicer");
        playerModels.add("Karen");
        playerModels.add("Crow");
        playerModels.add("Mettaton");
        playerModels.add("Kerfus");
        playerModels.add("Physicle");
        playerModels.add("Clearbot");
        playerModels.add("Creeper");
        playerModels.add("SCP-173");

        servers.add(new ServerInfo("Local Test", "127.0.0.1:12789"));

        loadConfig();

        screen = Screen.Splash;
        splashElapsed = 0.0f;
    }

    public Settings getSettings()          { return settings; }
    public Screen   getScreen()            { return screen; }
    public void     setScreen(Screen s)    { screen = s; cancelInput(); }

    public void setMousePosition(float x, float y) { mouseX = x; mouseY = y; }

    // =====================================================================
    // Масштабирование UI
    // =====================================================================

    private void updateScale(int w, int h) {
        fbWidth = w;
        fbHeight = h;
        float sx = w / BASE_WIDTH;
        float sy = h / BASE_HEIGHT;
        uiScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, Math.min(sx, sy)));
    }

    /** Масштабирует размер относительно базового разрешения. */
    private float s(float v) { return v * uiScale; }

    // =====================================================================
    // Splash-баннер
    // =====================================================================

    public boolean isSplashActive() {
        return screen == Screen.Splash;
    }

    public void updateSplash(float delta) {
        if (screen != Screen.Splash) return;

        splashElapsed += delta;
        if (splashElapsed >= SPLASH_DURATION) {
            if (splashImage != -1 && vg != NULL) {
                nvgDeleteImage(vg, splashImage);
                splashImage = -1;
            }
            splashBuffer = null;

            screen = Screen.Main;
            System.out.println("Splash finished -> Main Menu");
        }
    }

    // =====================================================================
    // Откат изменений по кнопке BACK
    // =====================================================================

    private Settings editBackup = null;

    public void beginEditSession() {
        editBackup = copyOf(settings);
    }

    public void discardChanges() {
        if (editBackup != null) {
            copyInto(editBackup, settings);
            editBackup = null;
        }
    }

    public void commitChanges() {
        editBackup = null;
    }

    private static Settings copyOf(Settings src) {
        Settings c = new Settings();
        copyInto(src, c);
        return c;
    }

    private static void copyInto(Settings src, Settings dst) {
        dst.width            = src.width;
        dst.height           = src.height;
        dst.fullscreen       = src.fullscreen;
        dst.vsync            = src.vsync;
        dst.fps              = src.fps;
        dst.mouseSensitivity = src.mouseSensitivity;
        dst.masterVolume     = src.masterVolume;
        dst.playerName       = src.playerName;
        dst.playerModel      = src.playerModel;
    }

    public String getSelectedServerIp() {
        if (selectedServer < 0 || selectedServer >= servers.size()) return null;
        return servers.get(selectedServer).ip;
    }

    public void changeResolution(int direction) {
        int idx = getResolutionIndex();
        idx = (idx + direction + RESOLUTIONS.length) % RESOLUTIONS.length;
        settings.width = RESOLUTIONS[idx][0];
        settings.height = RESOLUTIONS[idx][1];
    }

    public void changeFps(int direction) {
        int idx = getFpsIndex();
        idx = (idx + direction + FPS_OPTIONS.length) % FPS_OPTIONS.length;
        settings.fps = FPS_OPTIONS[idx];
    }

    private int getResolutionIndex() {
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            if (RESOLUTIONS[i][0] == settings.width && RESOLUTIONS[i][1] == settings.height) return i;
        }
        return 2;
    }

    private int getFpsIndex() {
        for (int i = 0; i < FPS_OPTIONS.length; i++) {
            if (FPS_OPTIONS[i] == settings.fps) return i;
        }
        return 3;
    }

    public void render(int w, int h) {
        updateScale(w, h);

        nvgBeginFrame(vg, w, h, 1.0f);

        if (screen == Screen.Splash) {
            renderSplash(w, h);
        } else {
            switch (screen) {
                case Main:       renderMain(w, h);       break;
                case Settings:   renderSettings(w, h);   break;
                case Profile:    renderProfile(w, h);    break;
                case Servers:    renderServers(w, h);    break;
                case Connecting: renderConnecting(w, h); break;
                case Exit:       break;
                default:         break;
            }
        }

        nvgEndFrame(vg);
    }

    private void renderSplash(int w, int h) {
        nvgBeginPath(vg);
        nvgRect(vg, 0, 0, w, h);
        nvgFillColor(vg, rgba(0xFF000000));
        nvgFill(vg);

        if (splashImage != -1) {
            if (splashElapsed < SPLASH_FADE) {
                alphasplash = splashElapsed / SPLASH_FADE;
            } else if (splashElapsed > SPLASH_DURATION - SPLASH_FADE) {
                alphasplash = (SPLASH_DURATION - splashElapsed) / SPLASH_FADE;
            } else {
                alphasplash = 1.0f;
            }
            alphasplash = Math.max(0f, Math.min(1f, alphasplash));

            NVGPaint paint = NVGPaint.calloc();
            nvgImagePattern(vg, 0, 0, w, h, 0, splashImage, alphasplash, paint);

            nvgBeginPath(vg);
            nvgRect(vg, 0, 0, w, h);
            nvgFillPaint(vg, paint);
            nvgFill(vg);
            paint.free();
        }
    }



    private void renderMain(int w, int h) {
        nvgFontFace(vg, FONT_NAME);

        float bw  = s(420), bh = s(64), gap = s(18);
        float bx  = w / 2.0f - bw / 2.0f;
        float totalButtonsHeight = 4 * bh + 3 * gap;
        float by  = (h - totalButtonsHeight) / 2.0f + s(150);

        drawButton(bx, by,                  bw, bh, "SERVER BROWSER", COL_ACCENT, ClickAction.GO_SERVERS);
        drawButton(bx, by + (bh + gap) * 1, bw, bh, "PROFILE",        COL_TEXT,   ClickAction.GO_PROFILE);
        drawButton(bx, by + (bh + gap) * 2, bw, bh, "SETTINGS",       COL_TEXT,   ClickAction.GO_SETTINGS);
        drawButton(bx, by + (bh + gap) * 3, bw, bh, "EXIT",           COL_DANGER, ClickAction.GO_EXIT);

        nvgFontSize(vg, s(16.0f));
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_BOTTOM);
        nvgFillColor(vg, rgba(COL_TEXT_DIM));
        nvgText(vg, w / 2.0f, h - s(20),
                "Player: " + settings.playerName + "   ·   Model: " +
                        playerModels.get(settings.playerModel));
    }

    private void renderSettings(int w, int h) {
        drawTitle(w, "SETTINGS", h);

        float panelW = s(720), panelH = s(560);
        float px = (w - panelW) / 2.0f;
        float py = Math.max(s(80), (h - panelH) / 2.0f + s(40));

        drawPanel(px, py, panelW, panelH);

        float labelX      = px + s(32);
        float valueRightX = px + panelW - s(32);
        float rowY        = py + s(40);
        float rowH        = s(52);
        float uiW         = s(220);
        float uiX         = valueRightX - uiW;
        float navW        = s(40);
        float resBtnW     = s(140);

        drawLabel(labelX, rowY, "RESOLUTION");
        int resIdx = getResolutionIndex();
        String resStr = RESOLUTIONS[resIdx][0] + " x " + RESOLUTIONS[resIdx][1];
        float totalResW = resBtnW + navW * 2 + s(10);
        float resMx = valueRightX - totalResW;
        drawButton(resMx, rowY, navW, s(36), "<", COL_TEXT, ClickAction.RES_PREV);
        drawValueButton(resMx + navW + s(5), rowY, resBtnW, s(36), resStr, ClickAction.NONE);
        drawButton(resMx + navW + resBtnW + s(10), rowY, navW, s(36), ">", COL_TEXT, ClickAction.RES_NEXT);
        rowY += rowH;

        drawLabel(labelX, rowY, "FULLSCREEN");
        drawToggle(uiX, rowY, uiW, s(36), settings.fullscreen, ClickAction.TOGGLE_FULLSCREEN);
        rowY += rowH;

        drawLabel(labelX, rowY, "VSYNC");
        drawToggle(uiX, rowY, uiW, s(36), settings.vsync, ClickAction.TOGGLE_VSYNC);
        rowY += rowH;

        drawLabel(labelX, rowY, "FPS LIMIT");
        int fpsIdx = getFpsIndex();
        String fpsStr = settings.fps == 0 ? "UNLIMITED" : String.valueOf(settings.fps);
        float fpsBtnW = s(140);
        float totalFpsW = fpsBtnW + navW * 2 + s(10);
        float fpsMx = valueRightX - totalFpsW;
        drawButton(fpsMx, rowY, navW, s(36), "<", COL_TEXT, ClickAction.FPS_PREV);
        drawValueButton(fpsMx + navW + s(5), rowY, fpsBtnW, s(36), fpsStr, ClickAction.NONE);
        drawButton(fpsMx + navW + fpsBtnW + s(10), rowY, navW, s(36), ">", COL_TEXT, ClickAction.FPS_NEXT);
        rowY += rowH;

        drawLabel(labelX, rowY, "MOUSE SENSITIVITY");
        drawSlider(uiX, rowY, uiW, s(36), settings.mouseSensitivity, SENS_MIN, SENS_MAX, ClickAction.SLIDER_SENSITIVITY);
        rowY += rowH;

        drawLabel(labelX, rowY, "MASTER VOLUME");
        drawSlider(uiX, rowY, uiW, s(36), settings.masterVolume, VOL_MIN, VOL_MAX, ClickAction.SLIDER_VOLUME);
        rowY += rowH;

        drawButton(px + s(40), py + panelH - s(70), s(240), s(50), "APPLY",
                COL_ACCENT, ClickAction.APPLY_SETTINGS);
        drawButton(px + panelW - s(280), py + panelH - s(70), s(240), s(50), "BACK",
                COL_TEXT, ClickAction.DISCARD_CHANGES);
    }

    private void renderProfile(int w, int h) {
        drawTitle(w, "PROFILE", h);

        float panelW = s(720), panelH = s(200);
        float px = (w - panelW) / 2.0f;
        float py = Math.max(s(80), (h - panelH * 1.5f));
        drawPanel(px, py, panelW, panelH);

        float labelX = px + s(32);
        float valueX = px + panelW - s(32);
        float rowY   = py + s(30);
        float rowH   = s(50);
        float navW   = s(40);
        float modelBtnW = s(200);

        drawLabel(labelX, rowY, "NICKNAME");
        String nickShow = inputActive && inputField == 0 ? inputText + "|" : settings.playerName;
        drawInput(valueX - s(320), rowY, s(320), s(40), nickShow, inputActive && inputField == 0, 0);
        rowY += rowH;

        drawLabel(labelX, rowY, "CHARACTER MODEL");
        String modelName = playerModels.get(settings.playerModel);
        float totalW = modelBtnW + navW * 2 + s(10);
        float mx = valueX - totalW;
        drawButton(mx, rowY, navW, s(36), "<", COL_TEXT, ClickAction.MODEL_PREV);
        drawValueButton(mx + navW + s(5), rowY, modelBtnW, s(36), modelName, ClickAction.NONE);
        drawButton(mx + navW + modelBtnW + s(10), rowY, navW, s(36), ">", COL_TEXT, ClickAction.MODEL_NEXT);

        float btnY = py + panelH - s(56);
        drawButton(px + s(40), btnY, s(240), s(44), "SAVE",
                COL_ACCENT, ClickAction.SAVE_PROFILE);
        drawButton(px + panelW - s(280), btnY, s(240), s(44), "BACK",
                COL_TEXT, ClickAction.DISCARD_CHANGES);
    }

    private void renderServers(int w, int h) {
        drawTitle(w, "SERVER BROWSER", h);

        float panelW = s(1000), panelH = s(620);
        float px = (w - panelW) / 2.0f;
        float py = Math.max(s(80), (h - panelH) / 2.0f + s(40));
        drawPanel(px, py, panelW, panelH);

        float labelX = px + s(32);
        float rowY   = py + s(30);
        float nameW  = s(280);
        float ipW    = s(380);

        String nameShow = inputActive && inputField == 2 ? inputText + "|" : pendingServerName;
        drawInput(labelX, rowY, nameW, s(40), nameShow, inputActive && inputField == 2, 2);

        String ipShow = inputActive && inputField == 1 ? inputText + "|"
                : (pendingServerIp.isEmpty() ? "e.g. 192.168.1.1:27015" : pendingServerIp);
        drawInput(labelX + nameW + s(20), rowY, ipW, s(40), ipShow, inputActive && inputField == 1, 1);

        drawButton(labelX + nameW + s(20) + ipW + s(20), rowY, s(120), s(40), "ADD",
                COL_SUCCESS, ClickAction.ADD_SERVER);

        float tableY = py + s(110);
        float rowH   = s(52);

        drawServerRow(px + s(20), tableY, panelW - s(40), s(44),
                "NAME", "ADDRESS", true, false, -1);

        tableY += s(44);

        for (int i = 0; i < servers.size(); i++) {
            ServerInfo sInfo = servers.get(i);
            boolean selected = (i == selectedServer);
            drawServerRow(px + s(20), tableY + i * rowH, panelW - s(40), rowH,
                    sInfo.name, sInfo.ip, false, selected, i);
        }

        float btnY = py + panelH - s(70);
        boolean canConnect = selectedServer >= 0;
        drawButton(px + s(32), btnY, s(260), s(50),
                canConnect ? "CONNECT" : "SELECT A SERVER",
                canConnect ? COL_ACCENT : COL_TEXT_DIM,
                ClickAction.CONNECT_SERVER);
        drawButton(px + s(312), btnY, s(220), s(50), "REMOVE",
                COL_DANGER, ClickAction.REMOVE_SERVER);
        drawButton(px + panelW - s(260), btnY, s(220), s(50), "BACK",
                COL_TEXT, ClickAction.GO_MAIN);
    }

    private void drawServerRow(float x, float y, float w, float h,
                               String a, String b,
                               boolean header, boolean selected, int rowIndex) {
        float radius = s(6);

        if (selected) {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, radius);
            nvgFillColor(vg, rgba(0xFFDC5A40));
            nvgFill(vg);

            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, s(4), h, s(2));
            nvgFillColor(vg, rgba(COL_ACCENT));
            nvgFill(vg);
        } else if (header) {
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x, y, w, h, radius);
            nvgFillColor(vg, rgba(0xFF252535));
            nvgFill(vg);
        } else {
            nvgBeginPath(vg);
            nvgRect(vg, x, y, w, h);
            nvgFillColor(vg, rgba(0xFF151520));
            nvgFill(vg);
        }

        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, s(header ? 18.0f : 20.0f));
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

        NVGColor col = header ? rgba(COL_ACCENT) : rgba(COL_TEXT);
        nvgFillColor(vg, col);

        float ty = y + h / 2.0f;
        nvgText(vg, x + s(20),  ty, a);
        nvgText(vg, x + s(260), ty, b);

        if (!header) {
            registerHitbox(x, y, w, h, ClickAction.NONE, false, rowIndex);
        }
    }

    private void renderConnecting(int w, int h) {
        nvgFontFace(vg, FONT_NAME);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

        String ip = getSelectedServerIp() != null ? getSelectedServerIp() : "unknown";

        nvgFontSize(vg, s(52.0f));
        nvgFillColor(vg, rgba(0x000000AA));
        nvgText(vg, w / 2.0f + s(3), h / 2.0f - s(30) + s(3), "CONNECTING...");
        nvgFillColor(vg, rgba(COL_ACCENT));
        nvgText(vg, w / 2.0f, h / 2.0f - s(30), "CONNECTING...");

        nvgFontSize(vg, s(24.0f));
        nvgFillColor(vg, rgba(COL_TEXT_DIM));
        nvgText(vg, w / 2.0f, h / 2.0f + s(30), "Server: " + ip);

        int dots = (int) ((System.currentTimeMillis() / 300) % 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dots; i++) sb.append(".");
        nvgFontSize(vg, s(28.0f));
        nvgFillColor(vg, rgba(COL_TEXT));
        nvgText(vg, w / 2.0f, h / 2.0f + s(80), sb.toString());

        drawButton(w / 2.0f - s(130), h / 2.0f + s(160), s(260), s(50), "CANCEL", COL_DANGER, ClickAction.GO_MAIN);
    }

    private void drawTitle(int w, String title, int h) {
        nvgFontFace(vg, FONT_NAME);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_TOP);
        nvgFontSize(vg, s(56.0f));
        nvgFillColor(vg, rgba(0x000000AA));
        nvgText(vg, w / 2.0f + s(2), h * 0.07f + s(2), title);
        nvgFillColor(vg, rgba(COL_ACCENT));
        nvgText(vg, w / 2.0f, h * 0.07f, title);
    }

    private void drawPanel(float x, float y, float w, float h) {
        float r = s(14);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillColor(vg, rgba(COL_PANEL));
        nvgFill(vg);

        nvgStrokeColor(vg, rgba(COL_BORDER));
        nvgStrokeWidth(vg, s(2.0f));
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgStroke(vg);
    }

    private void drawLabel(float x, float y, String text) {
        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, s(20.0f));
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, rgba(COL_TEXT_DIM));
        nvgText(vg, x, y + s(18), text);
    }

    private void drawInput(float x, float y, float w, float h, String value, boolean active, int fieldId) {
        float r = s(6);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillColor(vg, rgba(0xFF101018));
        nvgFill(vg);

        nvgStrokeColor(vg, active ? rgba(COL_ACCENT) : rgba(COL_BORDER));
        nvgStrokeWidth(vg, active ? s(2.5f) : s(1.5f));
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgStroke(vg);

        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, s(20.0f));
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, rgba(COL_TEXT));
        nvgText(vg, x + s(14), y + h / 2.0f, value);

        registerHitbox(x, y, w, h, ClickAction.NONE, true, fieldId);
    }

    private void drawToggle(float x, float y, float w, float h, boolean on, ClickAction action) {
        float knobW = h - s(8);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, h / 2);
        nvgFillColor(vg, on ? rgba(0xFF3A7040) : rgba(0xFF303040));
        nvgFill(vg);

        float kx = on ? (x + w - knobW - s(4)) : (x + s(4));
        nvgBeginPath(vg);
        nvgCircle(vg, kx + knobW / 2, y + h / 2, knobW / 2);
        nvgFillColor(vg, on ? rgba(COL_SUCCESS) : rgba(0x808090));
        nvgFill(vg);

        registerHitbox(x, y, w, h, action, false, -1);
    }

    private void drawValueButton(float x, float y, float w, float h, String value, ClickAction action) {
        float r = s(6);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillColor(vg, rgba(0xFF101018));
        nvgFill(vg);

        nvgStrokeColor(vg, rgba(COL_BORDER));
        nvgStrokeWidth(vg, s(1.5f));
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgStroke(vg);

        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, s(18.0f));
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, rgba(COL_TEXT));
        nvgText(vg, x + w / 2, y + h / 2, value);

        if (action != ClickAction.NONE) registerHitbox(x, y, w, h, action, false, -1);
    }

    public ClickAction onMouseClick(double mx, double my) {
        mouseX = (float) mx;
        mouseY = (float) my;

        for (int i = hitboxes.size() - 1; i >= 0; i--) {
            Hitbox hb = hitboxes.get(i);
            if (mx >= hb.x && mx <= hb.x + hb.w &&
                    my >= hb.y && my <= hb.y + hb.h) {

                if (hb.editableField) {
                    if (inputActive) {
                        applyInput();
                    }
                    inputActive = true;
                    inputField = hb.data;

                    if (inputField == 0) inputText = settings.playerName;
                    else if (inputField == 1) inputText = pendingServerIp;
                    else if (inputField == 2) inputText = pendingServerName;

                    return ClickAction.NONE;
                }

                if (hb.action == ClickAction.ADD_SERVER) {
                    if (inputActive) applyInput();
                    addServer();
                    return ClickAction.NONE;
                }

                if (hb.action == ClickAction.REMOVE_SERVER) {
                    removeSelectedServer();
                    return ClickAction.NONE;
                }

                if (hb.action == ClickAction.SLIDER_SENSITIVITY || hb.action == ClickAction.SLIDER_VOLUME) {
                    draggingSlider = true;
                    dragSliderAction = hb.action;
                    updateSliderFromMouse(mx);
                    return ClickAction.NONE;
                }

                if (hb.data >= 0 && screen == Screen.Servers) {
                    selectedServer = hb.data;
                    return ClickAction.NONE;
                }

                if (hb.action == ClickAction.SAVE_PROFILE || hb.action == ClickAction.APPLY_SETTINGS) {
                    if (inputActive) {
                        applyInput();
                    }
                }

                return hb.action;
            }
        }

        if (inputActive) {
            applyInput();
        } else {
            cancelInput();
        }
        return ClickAction.NONE;
    }

    public boolean isDraggingSlider() {
        return draggingSlider;
    }

    public void updateSliderDrag(double mx, boolean mouseDown) {
        if (!draggingSlider) return;

        if (!mouseDown) {
            draggingSlider = false;
            dragSliderAction = ClickAction.NONE;
            return;
        }
        updateSliderFromMouse(mx);
    }

    public void onMouseRelease() {
        draggingSlider = false;
        dragSliderAction = ClickAction.NONE;
    }

    private void updateSliderFromMouse(double mx) {
        for (Hitbox hb : hitboxes) {
            if (hb.action != dragSliderAction) continue;

            float t = (float) ((mx - hb.x) / hb.w);
            t = Math.max(0f, Math.min(1f, t));

            switch (dragSliderAction) {
                case SLIDER_SENSITIVITY:
                    settings.mouseSensitivity = SENS_MIN + t * (SENS_MAX - SENS_MIN);
                    break;

                case SLIDER_VOLUME:
                    float v = VOL_MIN + t * (VOL_MAX - VOL_MIN);
                    settings.masterVolume = v;
                    break;

                default:
                    break;
            }
            return;
        }
    }

    private void drawSlider(float x, float y, float w, float h, float value,
                            float min, float max, ClickAction sliderAction) {
        float trackH = s(8);
        float trackY = y + h / 2 - trackH / 2;

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, trackY, w, trackH, trackH / 2);
        nvgFillColor(vg, rgba(0xFF303040));
        nvgFill(vg);

        float t = (value - min) / (max - min);
        t = Math.max(0f, Math.min(1f, t));

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, trackY, w * t, trackH, trackH / 2);
        nvgFillColor(vg, rgba(COL_ACCENT));
        nvgFill(vg);

        float knobR = s(10);
        nvgBeginPath(vg);
        nvgCircle(vg, x + w * t, y + h / 2, knobR);
        nvgFillColor(vg, rgba(COL_ACCENT));
        nvgFill(vg);
        nvgStrokeColor(vg, rgba(0x000000));
        nvgStrokeWidth(vg, s(2));
        nvgStroke(vg);

        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, s(16.0f));
        nvgTextAlign(vg, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, rgba(COL_TEXT_DIM));
        nvgText(vg, x - s(10), y + h / 2, String.format("%.2f", value));

        if (sliderAction != ClickAction.NONE) {
            registerHitbox(x, y - s(4), w, h + s(8), sliderAction, false, -1);
        }
    }

    private void drawButton(float x, float y, float w, float h, String text,
                            int color, ClickAction action) {
        boolean hovered = isHovered(x, y, w, h);
        float r = s(8);

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgFillColor(vg, hovered ? rgba(0xFF2A2A3A) : rgba(0xFF1A1A28));
        nvgFill(vg);

        nvgStrokeColor(vg, rgba(color));
        nvgStrokeWidth(vg, hovered ? s(3.0f) : s(1.8f));
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, r);
        nvgStroke(vg);

        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, s(22.0f));
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, rgba(color));
        nvgText(vg, x + w / 2.0f, y + h / 2.0f, text);

        if (action != ClickAction.NONE) {
            registerHitbox(x, y, w, h, action, false, -1);
        }
    }

    private static final class Hitbox {
        float x, y, w, h;
        ClickAction action;
        boolean editableField;
        int data;
    }

    private final List<Hitbox> hitboxes = new ArrayList<>();

    private void registerHitbox(float x, float y, float w, float h,
                                ClickAction action, boolean editable, int data) {
        Hitbox hb = new Hitbox();
        hb.x = x; hb.y = y; hb.w = w; hb.h = h;
        hb.action = action;
        hb.editableField = editable;
        hb.data = data;
        hitboxes.add(hb);
    }

    private boolean isHovered(float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void addServer() {
        if (!pendingServerIp.isEmpty()) {
            String name = pendingServerName.isEmpty() ? "New Server" : pendingServerName;
            servers.add(new ServerInfo(name, pendingServerIp));
            saveConfig();

            pendingServerName = "New Server";
            pendingServerIp = "";
        }
    }

    private void removeSelectedServer() {
        if (selectedServer >= 0 && selectedServer < servers.size()) {
            servers.remove(selectedServer);
            selectedServer = -1;
            saveConfig();
        }
    }

    public void beginFrameHitboxes() {
        hitboxes.clear();
    }

    public void endFrameHitboxes() {
    }

    private static NVGColor rgba(int argb) {
        NVGColor c = NVGColor.create();
        nvgRGBA(
                (byte) ((argb >> 16) & 0xFF),
                (byte) ((argb >> 8)  & 0xFF),
                (byte) (argb & 0xFF),
                (byte) ((argb >> 24) & 0xFF),
                c);
        return c;
    }

    public void dispose() {
        if (splashImage != -1 && vg != NULL) {
            nvgDeleteImage(vg, splashImage);
            splashImage = -1;
        }
        splashBuffer = null;


        if (vg != NULL) {
            nvgDelete(vg);
            vg = NULL;
        }
        fontBuffer = null;
    }
}