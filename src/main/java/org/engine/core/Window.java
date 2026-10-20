package org.engine.core;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

public class Window {

    /**
     * НОВОЕ: слушатель изменения размера окна.
     * Window сам обновляет glViewport, а этот колбек нужен движку (Engine),
     * чтобы пересобрать матрицу проекции камеры под новое соотношение сторон.
     */
    public interface ResizeListener {
        void onWindowResize(int width, int height, int fbWidth, int fbHeight);
    }

    private long window;
    private final String title;

    // НОВОЕ: больше НЕ final — реальный размер меняется при смене разрешения
    // и при растягивании окна мышью за границу (окно у нас RESIZABLE)
    private int width, height;       // размер ОКНА в условных координатах (для NanoVG, GUI и мыши)
    private int fbWidth, fbHeight;   // размер ФРЕЙМБУФЕРА в ПИКСЕЛЯХ (для glViewport и камеры)

    // Переменные для хранения позиции мыши
    private double mouseX, mouseY;

    private ResizeListener resizeListener;

    public Window(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
        this.fbWidth = width;
        this.fbHeight = height;
    }

    /** НОВОЕ: подписка на изменение размера (Engine передаёт сюда обновление камеры). */
    public void setResizeListener(ResizeListener listener) {
        this.resizeListener = listener;
    }

    public void init() {
        // Вывод ошибок GLFW в системную консоль (стандартная практика)
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);

        window = GLFW.glfwCreateWindow(width, height, title, 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create window");
        }

        // Callback для обновления позиции мыши
        GLFW.glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            this.mouseX = xpos;
            this.mouseY = ypos;
        });

        GLFW.glfwMakeContextCurrent(window);

        // Включаем V-Sync (1), чтобы ограничить FPS до частоты монитора (убирает tearing)
        // Если нужна максимальная FPS, поставьте 0
        GLFW.glfwSwapInterval(1);

        GL.createCapabilities(); // Важно для LWJGL 3

        // ============================================================
        // НОВОЕ: КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ — колбеки изменения размера.
        // Раньше glViewport вызывался один раз в init(), поэтому после
        // glfwSetWindowMonitor(...) графика продолжала рисоваться
        // в старую область окна ("размер не меняется").
        // ============================================================

        // Меняется размер ОКНА (условные координаты) — для NanoVG/GUI/мыши
        GLFW.glfwSetWindowSizeCallback(window, (win, w, h) -> {
            this.width = w;
            this.height = h;
        });

        // Меняется размер ФРЕЙМБУФЕРА (пиксели) — сюда рисует OpenGL
        GLFW.glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            if (w <= 0 || h <= 0) return;
            this.fbWidth = w;
            this.fbHeight = h;
            GL11.glViewport(0, 0, w, h); // <-- без этого картинка "не растягивается"
            if (resizeListener != null) {
                resizeListener.onWindowResize(width, height, w, h);
            }
        });

        // Узнаём РЕАЛЬНЫЕ размеры (могут отличаться от запрошенных из-за DPI/ограничений ОС)
        syncSizes();

        GLFW.glfwShowWindow(window);
    }

    /**
     * НОВОЕ: немедленно перечитать текущие размеры у GLFW и обновить glViewport.
     * Вызывается из Engine.applySettings() сразу после glfwSetWindowMonitor,
     * чтобы не ждать ближайшего glfwPollEvents (однокадровый глитч).
     */
    public void syncSizes() {
        int[] w = new int[1], h = new int[1];

        GLFW.glfwGetWindowSize(window, w, h);
        if (w[0] > 0 && h[0] > 0) {
            this.width = w[0];
            this.height = h[0];
        }

        GLFW.glfwGetFramebufferSize(window, w, h);
        if (w[0] > 0 && h[0] > 0) {
            this.fbWidth = w[0];
            this.fbHeight = h[0];
            GL11.glViewport(0, 0, fbWidth, fbHeight);
            if (resizeListener != null) {
                resizeListener.onWindowResize(width, height, fbWidth, fbHeight);
            }
        }
    }

    /**
     * НОВОЕ: применяет оконный/полноэкранный режим и разрешение одним вызовом.
     * Вся работа с glfwSetWindowMonitor теперь живёт здесь — Engine не должен
     * знать детали GLFW. После смены видеорежима немедленно перечитывает
     * РЕАЛЬНЫЕ размеры (монитор мог подобрать ближайший видеорежим + DPI-масштаб)
     * и обновляет glViewport + слушателя (см. syncSizes).
     *
     * @param fullscreen true — полноэкранный режим на основном мониторе
     * @param width      запрошенная ширина
     * @param height     запрошенная высота
     */
    public void setVideoMode(boolean fullscreen, int width, int height) {
        if (window == 0) return;

        if (fullscreen) {
            // В полноэкранном режиме x/y игнорируются, ставим 0
            GLFW.glfwSetWindowMonitor(window, GLFW.glfwGetPrimaryMonitor(),
                    0, 0, width, height, GLFW.GLFW_DONT_CARE);
        } else {
            // Оконный режим: позиция 100,100 — как было раньше
            GLFW.glfwSetWindowMonitor(window, 0,
                    100, 100, width, height, GLFW.GLFW_DONT_CARE);
        }

        // Сразу синхронизируем viewport и размеры, не дожидаясь glfwPollEvents
        syncSizes();
    }

    /** НОВОЕ: находится ли окно сейчас в полноэкранном режиме. */
    public boolean isFullscreen() {
        return window != 0 && GLFW.glfwGetWindowMonitor(window) != 0;
    }

    public void update() {
        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();
    }

    public boolean shouldClose() {
        return GLFW.glfwWindowShouldClose(window);
    }

    public void cleanup() {
        // НОВОЕ: освобождаем ВСЕ колбеки окна (мышь, клавиатура, размеры и т.д.)
        // перед уничтожением — каноничный порядок завершения из документации LWJGL
        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwSetErrorCallback(null).free();
        GLFW.glfwTerminate();
    }

    // ==============================
    // МЕТОДЫ ДЛЯ ВВОДА (Удобство)
    // ==============================
    public boolean isMouseButtonDown(int button) {
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }
    /** Проверяет, нажата ли клавиша в данный момент */
    public boolean isKeyDown(int key) {
        if (key <= 0) {
            return false;
        }
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    /** Возвращает текущую координату X мыши */
    public double getMouseX() {
        return mouseX;
    }

    /** Возвращает текущую координату Y мыши */
    public double getMouseY() {
        return mouseY;
    }

    /** Скрывает и блокирует курсор внутри окна (режим FPS) */
    public void setCursorDisabled(boolean disabled) {
        if (disabled) {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        } else {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        }
    }

    // Геттеры
    public long getWindowHandle() { return window; }
    public int getWidth()  { return width;  }   // окно в координатах (NanoVG/GUI)
    public int getHeight() { return height; }
    public int getFbWidth()  { return fbWidth;  }  // фреймбуфер в пикселях (камера/viewport)
    public int getFbHeight() { return fbHeight; }
}
