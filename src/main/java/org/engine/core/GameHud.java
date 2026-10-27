package org.engine.core;

import org.engine.utils.ResourceManager;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GameHud {

    public enum TypeMode {
        FPSPlayer,
        DeadPlayer,
        StartLevel,
        EndMatch,
        WaitingMatch,
        LoadMap,
        NoneRender,
    }

    private String NameMap = "";
    private long vg;
    private ByteBuffer fontBuffer;
    private static final String FONT_NAME = "hud-font";
    private static final String FONT_FILE = "font/def.ttf";

    // ========== Масштабирование UI ==========
    private static final float BASE_WIDTH  = 1920f;
    private static final float BASE_HEIGHT = 1080f;
    private static final float SCALE_MIN   = 0.5f;
    private static final float SCALE_MAX   = 3.0f;

    private float uiScale = 1.0f;
    private int   fbWidth = 1920, fbHeight = 1080;

    // Прицел
    private int crosshairImage = -1;
    private static final String CROSSHAIR_FILE = "texture/crosshair.png";
    private static final float CROSSHAIR_SIZE = 32.0f;

    // Кровь на экране
    private int bloodImage = -1;
    private static final String BLOOD_FILE = "texture/BloodOverlay.png";

    /** Буферы изображений, чтобы native-память не освободилась раньше времени. */
    private final Map<String, ByteBuffer> imageBuffers = new HashMap<>();

    private int health = 100;
    private int ammo = 0;
    private float alpha = 0;
    private String Playername = "";
    private TypeMode guimod = TypeMode.NoneRender;

    // ---------- Таймер ----------
    private float timeRemaining = 0.0f;
    private boolean timerRunning = true;

    // ---------- Цветовые метки ----------
    private static final Map<String, int[]> COLOR_TAGS = new HashMap<>();
    static {
        COLOR_TAGS.put("red",    new int[]{255,  100,  100, 255});
        COLOR_TAGS.put("green",  new int[]{ 80, 255,  80, 255});
        COLOR_TAGS.put("yellow", new int[]{255, 220,  60, 255});
        COLOR_TAGS.put("gold",   new int[]{255, 200,  60, 255});
        COLOR_TAGS.put("white",  new int[]{255, 255, 255, 255});
        COLOR_TAGS.put("gray",   new int[]{180, 180, 180, 255});
        COLOR_TAGS.put("cyan",   new int[]{ 80, 220, 220, 255});
        COLOR_TAGS.put("orange", new int[]{255, 150,  50, 255});
        COLOR_TAGS.put("blue",   new int[]{100, 100, 255, 255});
    }
    private static final int[] DEFAULT_COLOR = {255, 255, 255, 255};

    private static final class TextSegment {
        final String text;
        final byte r, g, b;
        final int a;

        TextSegment(String text, int r, int g, int b, int a) {
            this.text = text;
            this.r = (byte) r;
            this.g = (byte) g;
            this.b = (byte) b;
            this.a = a;
        }
    }

    private static List<TextSegment> parseSegments(String raw) {
        List<TextSegment> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;

        int[] cur = DEFAULT_COLOR;
        StringBuilder buf = new StringBuilder();
        int i = 0;

        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '[') {
                int close = raw.indexOf(']', i);
                if (close > i) {
                    String tag = raw.substring(i + 1, close);
                    boolean closing = tag.startsWith("/");
                    String key = closing ? tag.substring(1) : tag;

                    if (COLOR_TAGS.containsKey(key)) {
                        if (buf.length() > 0) {
                            out.add(new TextSegment(buf.toString(), cur[0], cur[1], cur[2], cur[3]));
                            buf.setLength(0);
                        }
                        cur = closing ? DEFAULT_COLOR : COLOR_TAGS.get(key);
                        i = close + 1;
                        continue;
                    }
                }
            }
            buf.append(c);
            i++;
        }
        if (buf.length() > 0) {
            out.add(new TextSegment(buf.toString(), cur[0], cur[1], cur[2], cur[3]));
        }
        return out;
    }

    // ---------- Список событий ----------
    private static final class HudEvent {
        final String text;
        final List<TextSegment> segments;
        float life;
        final float maxLife;
        float slideIn;

        HudEvent(String text, float lifeSeconds) {
            this.text = text;
            this.segments = parseSegments(text);
            this.life = lifeSeconds;
            this.maxLife = lifeSeconds;
            this.slideIn = 0.0f;
        }
    }

    private static final int MAX_EVENTS = 6;
    private static final float EVENT_LIFETIME = 5.0f;
    private static final float EVENT_SLIDE_SPEED = 6.0f;

    private final Deque<HudEvent> events = new ArrayDeque<>();

    public void setPlayername(String pln) {
        this.Playername = pln;
    }

    // ---------- Таблица лидеров ----------
    public static final class LeaderEntry {
        public final int id;
        public final String name;
        public final int kills;
        public final int deaths;

        public LeaderEntry(int id, String name, int kills, int deaths) {
            this.id = id;
            this.name = name;
            this.kills = kills;
            this.deaths = deaths;
        }

        public float kd() {
            if (deaths <= 0) return kills;
            return (float) kills / (float) deaths;
        }

        public int score() {
            return kills * 100 - deaths * 10;
        }
    }

    private final List<LeaderEntry> leaderTable = new ArrayList<>();

    // ------------------------------------------------------------------
    //  Публичное API
    // ------------------------------------------------------------------

    public void setHealth(int health) {
        if (this.health > health) {
            alpha = 1.0f;
        }
        this.health = health;
    }

    public void setGuiMode(TypeMode gui) {
        this.guimod = gui;
        this.alpha = 0;
        if (gui == TypeMode.WaitingMatch) {
            timerRunning = false;
            events.clear();
        }
    }

    public TypeMode getGuiMode() {
        return guimod;
    }

    public void setAmmo(int ammo) { this.ammo = ammo; }

    public void setTimeRemaining(float seconds) {
        this.timeRemaining = Math.max(0.0f, seconds);
        timerRunning = true;
    }

    public void setNameMap(String nameMap) {
        NameMap = nameMap;
    }

    public float getTimeRemaining() {
        return timeRemaining;
    }

    public void setTimerRunning(boolean running) {
        this.timerRunning = running;
    }

    public void addEvent(String text) {
        addEvent(text, EVENT_LIFETIME);
    }

    public void addEvent(String text, float lifeSeconds) {
        if (text == null || text.isEmpty()) return;
        events.addLast(new HudEvent(text, lifeSeconds));
        while (events.size() > MAX_EVENTS) {
            events.removeFirst();
        }
    }

    public void clearEvents() {
        events.clear();
    }

    public void AddLiaderTable(int id, String name, int kills, int deaths) {
        if (name == null) name = "Player" + id;

        for (int i = 0; i < leaderTable.size(); i++) {
            LeaderEntry e = leaderTable.get(i);
            if (e.id == id) {
                leaderTable.set(i, new LeaderEntry(id, name, kills, deaths));
                return;
            }
        }
        leaderTable.add(new LeaderEntry(id, name, kills, deaths));
    }

    public void clearLeaderTable() {
        leaderTable.clear();
    }

    public List<LeaderEntry> getLeaderTable() {
        List<LeaderEntry> copy = new ArrayList<>(leaderTable);
        copy.sort(
                Comparator.comparingInt(LeaderEntry::score).reversed()
                        .thenComparing(Comparator.comparingInt((LeaderEntry e) -> e.kills).reversed())
                        .thenComparing(e -> e.name)
        );
        return copy;
    }

    // ------------------------------------------------------------------
    //  Инициализация
    // ------------------------------------------------------------------

    public void init() throws Exception {
        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == NULL) {
            throw new Exception("Не удалось инициализировать NanoVG");
        }

        fontBuffer = ResourceManager.getResource(FONT_FILE);
        if (fontBuffer == null) {
            throw new Exception("Файл шрифта не найден в ресурсах: " + FONT_FILE);
        }

        int font = nvgCreateFontMem(vg, FONT_NAME, fontBuffer, false);
        if (font == -1) {
            throw new Exception("Не удалось загрузить шрифт в NanoVG: " + FONT_FILE);
        }

        crosshairImage = loadImage(CROSSHAIR_FILE);
        bloodImage     = loadImage(BLOOD_FILE);
    }

    private int loadImage(String filePath) throws Exception {
        ByteBuffer buffer = ResourceManager.getResourceOrError(filePath, "error.png");
        if (buffer == null) {
            throw new Exception("Файл изображения не найден и отсутствует error.png: " + filePath);
        }

        int image = nvgCreateImageMem(vg, 0, buffer);
        if (image == -1) {
            throw new Exception("Не удалось загрузить изображение: " + filePath);
        }

        imageBuffers.put(filePath, buffer);
        return image;
    }

    // ------------------------------------------------------------------
    //  Масштабирование
    // ------------------------------------------------------------------

    private void updateScale(int w, int h) {
        fbWidth = w;
        fbHeight = h;
        float sx = w / BASE_WIDTH;
        float sy = h / BASE_HEIGHT;
        uiScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, Math.min(sx, sy)));
    }

    /** Масштабирует размер относительно базового разрешения. */
    private float s(float v) { return v * uiScale; }

    // ------------------------------------------------------------------
    //  Рендер
    // ------------------------------------------------------------------

    public void render(int windowWidth, int windowHeight) {
        updateScale(windowWidth, windowHeight);

        float dt = Time.getInstance().getDeltaSecond();

        nvgBeginFrame(vg, windowWidth, windowHeight, 1.0f);

        if (this.guimod == TypeMode.NoneRender) {

        }
        else if (this.guimod == TypeMode.LoadMap) {
            renderLoadScene(windowWidth, windowHeight);
        }
        else if (this.guimod == TypeMode.WaitingMatch) {
            renderWaitingMatch(windowWidth, windowHeight);
        } else {
            if (this.guimod != TypeMode.StartLevel && this.guimod != TypeMode.EndMatch) {
                if (this.guimod == TypeMode.FPSPlayer) {
                    if (bloodImage != -1) {
                        if (alpha >= 0.0f) {
                            alpha -= dt * 2.0f;
                        } else {
                            alpha = 0.0f;
                        }

                        NVGPaint paint = NVGPaint.calloc();
                        nvgImagePattern(vg, 0, 0, windowWidth, windowHeight,
                                0.0f, bloodImage, alpha, paint);

                        nvgBeginPath(vg);
                        nvgRect(vg, 0, 0, windowWidth, windowHeight);
                        nvgFillPaint(vg, paint);
                        nvgFill(vg);

                        paint.free();
                    }

                    if (crosshairImage != -1) {
                        float crossSize = s(CROSSHAIR_SIZE);
                        float cx = windowWidth / 2.0f;
                        float cy = windowHeight / 2.0f;
                        float half = crossSize / 2.0f;

                        NVGPaint paint = NVGPaint.calloc();
                        nvgImagePattern(vg, cx - half, cy - half, crossSize, crossSize,
                                0.0f, crosshairImage, 1.0f, paint);

                        nvgBeginPath(vg);
                        nvgRect(vg, cx - half, cy - half, crossSize, crossSize);
                        nvgFillPaint(vg, paint);
                        nvgFill(vg);

                        paint.free();
                    }

                    nvgFontFace(vg, FONT_NAME);
                    nvgFontSize(vg, s(50.0f));
                    nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);

// ---------- HP с обводкой ----------
                    NVGColor healthColor = NVGColor.create();
                    if (health > 50) nvgRGBA((byte) 0, (byte) 255, (byte) 0, (byte) 255, healthColor);
                    else if (health > 20) nvgRGBA((byte) 255, (byte) 255, (byte) 0, (byte) 255, healthColor);
                    else nvgRGBA((byte) 255, (byte) 0, (byte) 0, (byte) 255, healthColor);

                    NVGColor hpShadow = NVGColor.create();
                    nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 200, hpShadow);

                    String hpText = "HP: " + health;
                    float hpX = s(20);
                    float hpY = windowHeight - s(50);

                    nvgFillColor(vg, hpShadow);
                    nvgText(vg, hpX + s(2), hpY + s(2), hpText);

                    nvgFillColor(vg, healthColor);
                    nvgText(vg, hpX, hpY, hpText);

// ---------- AMMO с обводкой ----------
                    NVGColor ammoColor = NVGColor.create();
                    if (ammo > 15) nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255, ammoColor);
                    else if (ammo > 5) nvgRGBA((byte) 255, (byte) 255, (byte) 0, (byte) 255, ammoColor);
                    else nvgRGBA((byte) 255, (byte) 0, (byte) 0, (byte) 255, ammoColor);

                    NVGColor ammoShadow = NVGColor.create();
                    nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 200, ammoShadow);

                    String ammoText = "AMMO: " + ammo;
                    float textWidth = nvgTextBounds(vg, 0, 0, ammoText, (float[]) null);
                    float ammoX = windowWidth - textWidth - s(20);
                    float ammoY = windowHeight - s(50);

                    nvgFillColor(vg, ammoShadow);
                    nvgText(vg, ammoX + s(2), ammoY + s(2), ammoText);

                    nvgFillColor(vg, ammoColor);
                    nvgText(vg, ammoX, ammoY, ammoText);

                    if (Playername != null && !Playername.isEmpty()) {
                        nvgFontFace(vg, FONT_NAME);
                        nvgFontSize(vg, s(50.0f));
                        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_TOP);

                        NVGColor nameShadow = NVGColor.create();
                        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 200, nameShadow);

                        NVGColor nameColor = NVGColor.create();
                        nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255, nameColor);

                        float cx = windowWidth / 2.0f;
                        float ty = windowHeight - s(50.0f);

                        nvgFillColor(vg, nameShadow);
                        nvgText(vg, cx + s(2), ty + s(2), Playername);

                        nvgFillColor(vg, nameColor);
                        nvgText(vg, cx, ty, Playername);
                    }
                }

                if (this.guimod == TypeMode.DeadPlayer) {
                    alpha = 0.0f;
                    nvgFontFace(vg, FONT_NAME);
                    nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

                    float cx = windowWidth / 2.0f;
                    float cy = windowHeight / 2.0f;

                    NVGColor shadow = NVGColor.create();
                    nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 220, shadow);

                    NVGColor deadColor = NVGColor.create();
                    nvgRGBA((byte) 220, (byte) 30, (byte) 30, (byte) 255, deadColor);

                    nvgFontSize(vg, s(96.0f));
                    nvgFillColor(vg, shadow);
                    nvgText(vg, cx + s(3), cy - s(40) + s(3), "YOU ARE DEAD");
                    nvgFillColor(vg, deadColor);
                    nvgText(vg, cx, cy - s(40), "YOU ARE DEAD");

                    nvgFontSize(vg, s(42.0f));
                    NVGColor waitColor = NVGColor.create();
                    nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 230, waitColor);

                    nvgFillColor(vg, shadow);
                    nvgText(vg, cx + s(2), cy + s(50) + s(2), "WAIT TO RESPAWN");
                    nvgFillColor(vg, waitColor);
                    nvgText(vg, cx, cy + s(50), "WAIT TO RESPAWN");
                }
            } else {
                if (this.guimod == TypeMode.EndMatch) {
                    renderLeaderTable(windowWidth, windowHeight);
                }
            }

            if (this.guimod == TypeMode.FPSPlayer || this.guimod == TypeMode.DeadPlayer) {
                renderTimer(windowWidth);
            }

            renderEvents(windowWidth, dt);
        }

        nvgEndFrame(vg);
    }

    private void renderWaitingMatch(int windowWidth, int windowHeight) {
        nvgFontFace(vg, FONT_NAME);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

        float cx = windowWidth / 2.0f;
        float cy = windowHeight / 2.0f;

        NVGColor shadow = NVGColor.create();
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 220, shadow);

        NVGColor mainColor = NVGColor.create();
        nvgRGBA((byte) 255, (byte) 220, (byte) 90, (byte) 255, mainColor);

        NVGColor subColor = NVGColor.create();
        nvgRGBA((byte) 230, (byte) 230, (byte) 230, (byte) 230, subColor);

        nvgFontSize(vg, s(42.0f));
        nvgFillColor(vg, shadow);
        nvgText(vg, cx + s(3), cy - s(20) + s(3), "WAITING FOR PLAYERS");
        nvgFillColor(vg, mainColor);
        nvgText(vg, cx, cy - s(20), "WAITING FOR PLAYERS");

        nvgFontSize(vg, s(28.0f));
        nvgFillColor(vg, shadow);
        nvgText(vg, cx + s(2), cy + s(70) + s(2), "PLEASE WAIT...");
        nvgFillColor(vg, subColor);
        nvgText(vg, cx, cy + s(70), "PLEASE WAIT...");
    }

    private void renderLoadScene(int windowWidth, int windowHeight) {
        nvgFontFace(vg, FONT_NAME);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

        float cx = windowWidth / 2.0f;
        float cy = windowHeight / 2.0f;

        NVGColor shadow = NVGColor.create();
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 220, shadow);

        NVGColor mainColor = NVGColor.create();
        nvgRGBA((byte) 255, (byte) 220, (byte) 90, (byte) 255, mainColor);

        NVGColor subColor = NVGColor.create();
        nvgRGBA((byte) 230, (byte) 230, (byte) 230, (byte) 230, subColor);

        nvgFontSize(vg, s(42.0f));
        nvgFillColor(vg, shadow);
        nvgText(vg, cx + s(3), cy - s(20) + s(3), "LOAD MAP: " + NameMap);
        nvgFillColor(vg, mainColor);
        nvgText(vg, cx, cy - s(20), "LOAD MAP: " + NameMap);

        nvgFontSize(vg, s(28.0f));
        nvgFillColor(vg, shadow);
        nvgText(vg, cx + s(2), cy + s(70) + s(2), "PLEASE WAIT...");
        nvgFillColor(vg, subColor);
        nvgText(vg, cx, cy + s(70), "PLEASE WAIT...");
    }

    private void renderTimer(int windowWidth) {
        if (timerRunning) {
            timeRemaining -= Time.getInstance().getDeltaSecond();
            if (timeRemaining < 0.0f) {
                timeRemaining = 0.0f;
                timerRunning = false;
            }
        }

        int totalSeconds = (int) Math.ceil(timeRemaining);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timeText = String.format("%02d:%02d", minutes, seconds);

        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, s(44.0f));
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_TOP);

        NVGColor color = NVGColor.create();
        if (timeRemaining <= 30.0f) {
            nvgRGBA((byte) 255, (byte) 60, (byte) 60, (byte) 255, color);
        } else {
            nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 255, color);
        }

        NVGColor shadow = NVGColor.create();
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 180, shadow);

        float cx = windowWidth / 2.0f;

        nvgFillColor(vg, shadow);
        nvgText(vg, cx + s(2), s(22), timeText);

        nvgFillColor(vg, color);
        nvgText(vg, cx, s(20), timeText);
    }

    private void renderEvents(int windowWidth, float dt) {
        if (events.isEmpty()) return;

        final float fontSize    = s(22.0f);
        final float padding     = s(10.0f);
        final float lineHeight  = fontSize + s(12.0f);
        final float topMargin   = s(80.0f);
        final float rightMargin = s(20.0f);

        nvgFontFace(vg, FONT_NAME);
        nvgFontSize(vg, fontSize);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

        Iterator<HudEvent> it = events.iterator();
        int index = 0;

        while (it.hasNext()) {
            HudEvent ev = it.next();

            ev.life -= dt;
            if (ev.life <= 0.0f) {
                it.remove();
                continue;
            }
            if (ev.slideIn < 1.0f) {
                ev.slideIn = Math.min(1.0f, ev.slideIn + dt * EVENT_SLIDE_SPEED);
            }

            float lifeRatio = ev.life / ev.maxLife;
            float fade = lifeRatio < 0.25f ? lifeRatio / 0.25f : 1.0f;
            float slideOffset = (1.0f - ev.slideIn) * s(300.0f);

            // Измеряем ширину без цветовых меток
            float textBounds = 0.0f;
            for (TextSegment seg : ev.segments) {
                textBounds += nvgTextBounds(vg, 0, 0, seg.text, (float[]) null);
            }

            float boxWidth  = textBounds + padding * 2.0f;
            float boxHeight = lineHeight;

            float boxX = windowWidth - boxWidth - rightMargin + slideOffset;
            float boxY = topMargin + index * (boxHeight + s(6.0f));

            NVGColor bg = NVGColor.create();
            nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) (140 * fade), bg);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, boxX, boxY, boxWidth, boxHeight, s(6.0f));
            nvgFillColor(vg, bg);
            nvgFill(vg);

            NVGColor accent = NVGColor.create();
            nvgRGBA((byte) 255, (byte) 200, (byte) 60, (byte) (220 * fade), accent);
            nvgBeginPath(vg);
            nvgRoundedRect(vg, boxX, boxY, s(4.0f), boxHeight, s(2.0f));
            nvgFillColor(vg, accent);
            nvgFill(vg);

            // Посегментный рендер текста с цветами
            float textX = boxX + padding;
            float textY = boxY + boxHeight / 2.0f;

            for (TextSegment seg : ev.segments) {
                NVGColor txt = NVGColor.create();
                nvgRGBA(seg.r, seg.g, seg.b, (byte) (seg.a * fade), txt);
                nvgFillColor(vg, txt);
                nvgText(vg, textX, textY, seg.text);
                textX += nvgTextBounds(vg, 0, 0, seg.text, (float[]) null);
            }

            index++;
        }
    }

    private void renderLeaderTable(int windowWidth, int windowHeight) {
        NVGColor overlay = NVGColor.create();
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 180, overlay);
        nvgBeginPath(vg);
        nvgRect(vg, 0, 0, windowWidth, windowHeight);
        nvgFillColor(vg, overlay);
        nvgFill(vg);

        nvgFontFace(vg, FONT_NAME);
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_TOP);

        NVGColor titleColor = NVGColor.create();
        nvgRGBA((byte) 255, (byte) 220, (byte) 90, (byte) 255, titleColor);
        NVGColor shadow = NVGColor.create();
        nvgRGBA((byte) 0, (byte) 0, (byte) 0, (byte) 200, shadow);

        nvgFontSize(vg, s(56.0f));
        nvgFillColor(vg, shadow);
        nvgText(vg, windowWidth / 2.0f + s(2), s(62), "LEADERBOARD");
        nvgFillColor(vg, titleColor);
        nvgText(vg, windowWidth / 2.0f, s(60), "LEADERBOARD");

        float tableWidth = Math.min(s(900.0f), windowWidth - s(80.0f));
        float rowHeight  = s(42.0f);
        float headerH    = s(46.0f);

        List<LeaderEntry> table = getLeaderTable();

        float tableX = (windowWidth - tableWidth) / 2.0f;
        float tableY = s(160.0f);

        int rows = Math.max(table.size(), 1);
        float tableHeight = headerH + rowHeight * rows + s(16.0f);

        float colRank   = tableX + s(30.0f);
        float colName   = tableX + s(110.0f);
        float colKills  = tableX + tableWidth - s(260.0f);
        float colDeaths = tableX + tableWidth - s(170.0f);
        float colKD     = tableX + tableWidth - s(80.0f);

        NVGColor tableBg = NVGColor.create();
        nvgRGBA((byte) 20, (byte) 20, (byte) 25, (byte) 220, tableBg);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, tableX, tableY, tableWidth, tableHeight, s(10.0f));
        nvgFillColor(vg, tableBg);
        nvgFill(vg);

        NVGColor headerBg = NVGColor.create();
        nvgRGBA((byte) 40, (byte) 40, (byte) 50, (byte) 255, headerBg);
        nvgBeginPath(vg);
        nvgRoundedRect(vg, tableX, tableY, tableWidth, headerH, s(10.0f));
        nvgFillColor(vg, headerBg);
        nvgFill(vg);

        NVGColor headerText = NVGColor.create();
        nvgRGBA((byte) 255, (byte) 220, (byte) 90, (byte) 255, headerText);

        nvgFontSize(vg, s(24.0f));
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        nvgFillColor(vg, headerText);
        nvgText(vg, colRank,   tableY + headerH / 2.0f, "#");
        nvgText(vg, colName,   tableY + headerH / 2.0f, "NAME");
        nvgText(vg, colKills,  tableY + headerH / 2.0f, "KILLS");
        nvgText(vg, colDeaths, tableY + headerH / 2.0f, "DEATHS");
        nvgText(vg, colKD,     tableY + headerH / 2.0f, "K/D");

        nvgFontSize(vg, s(22.0f));

        for (int i = 0; i < table.size(); i++) {
            LeaderEntry e = table.get(i);
            float rowY = tableY + headerH + i * rowHeight;
            float textY = rowY + rowHeight / 2.0f;

            if ((i & 1) == 1) {
                NVGColor rowBg = NVGColor.create();
                nvgRGBA((byte) 255, (byte) 255, (byte) 255, (byte) 12, rowBg);
                nvgBeginPath(vg);
                nvgRect(vg, tableX + s(4.0f), rowY, tableWidth - s(8.0f), rowHeight);
                nvgFillColor(vg, rowBg);
                nvgFill(vg);
            }

            NVGColor rowColor = NVGColor.create();
            if (i == 0)      nvgRGBA((byte) 255, (byte) 215, (byte) 0,   (byte) 255, rowColor);
            else if (i == 1) nvgRGBA((byte) 200, (byte) 200, (byte) 210, (byte) 255, rowColor);
            else if (i == 2) nvgRGBA((byte) 205, (byte) 127, (byte) 50,  (byte) 255, rowColor);
            else             nvgRGBA((byte) 230, (byte) 230, (byte) 230, (byte) 255, rowColor);

            nvgFillColor(vg, rowColor);
            nvgText(vg, colRank,   textY, String.valueOf(i + 1));
            nvgText(vg, colName,   textY, e.name);
            nvgText(vg, colKills,  textY, String.valueOf(e.kills));
            nvgText(vg, colDeaths, textY, String.valueOf(e.deaths));
            nvgText(vg, colKD,     textY, String.format("%.2f", e.kd()));
        }

        if (table.isEmpty()) {
            nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            NVGColor emptyColor = NVGColor.create();
            nvgRGBA((byte) 200, (byte) 200, (byte) 200, (byte) 200, emptyColor);
            nvgFillColor(vg, emptyColor);
            nvgText(vg, windowWidth / 2.0f, tableY + headerH + rowHeight / 2.0f, "NO DATA");
        }

        nvgFontSize(vg, s(22.0f));
        nvgTextAlign(vg, NVG_ALIGN_CENTER | NVG_ALIGN_TOP);
        NVGColor hint = NVGColor.create();
        nvgRGBA((byte) 200, (byte) 200, (byte) 200, (byte) 200, hint);
        nvgFillColor(vg, hint);
        nvgText(vg, windowWidth / 2.0f, windowHeight - s(60.0f), "PRESS ESC TO EXIT");
    }

    public void dispose() {
        if (crosshairImage != -1) {
            nvgDeleteImage(vg, crosshairImage);
            crosshairImage = -1;
        }
        if (bloodImage != -1) {
            nvgDeleteImage(vg, bloodImage);
            bloodImage = -1;
        }
        imageBuffers.clear();

        if (vg != NULL) {
            nvgDelete(vg);
            vg = NULL;
        }
        fontBuffer = null;
    }
}