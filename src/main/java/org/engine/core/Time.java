package org.engine.core;

import org.lwjgl.glfw.GLFW;

/**
 * Класс для управления временем в движке.
 * Отвечает за расчет DeltaTime (времени между кадрами), подсчет FPS и ограничение FPS.
 * Реализован как Синглтон (Singleton).
 */
public class Time {

    // Единственный экземпляр класса (создается сразу при запуске)
    private static final Time INSTANCE = new Time();

    private double lastFrameTime;
    private float deltaTime;

    private double startTime;
    private double elapsedTime;

    private double fpsTimeSum;
    private int fpsFrameCount;
    private int currentFPS;

    // Переменные для лимитера FPS
    private int targetFPS = 60; // 0 или меньше - без лимита (безлимитный FPS)

    // Приватный конструктор запрещает создание объекта через new Time()
    private Time() {
    }

    // Метод для получения доступа к единственному экземпляру
    public static Time getInstance() {
        return INSTANCE;
    }

    public void init() {
        startTime = GLFW.glfwGetTime();
        lastFrameTime = startTime;
        elapsedTime = 0;
        deltaTime = 0;

        fpsTimeSum = 0;
        fpsFrameCount = 0;
        currentFPS = 0;
    }

    /**
     * Обновление времени. Должно вызываться один раз в начале каждого кадра.
     */
    public void update() {
        // 1. Применяем лимитер (ждем, пока не наступит время следующего кадра)
        syncFPS();

        // 2. Считаем время после ожидания
        double currentTime = GLFW.glfwGetTime();

        deltaTime = (float)(currentTime - lastFrameTime);
        lastFrameTime = currentTime;

        elapsedTime = currentTime - startTime;

        fpsTimeSum += deltaTime;
        fpsFrameCount++;

        if (fpsTimeSum >= 0.25) {
            currentFPS = (int)(fpsFrameCount / fpsTimeSum);
            fpsFrameCount = 0;
            fpsTimeSum = 0;
        }
    }

    /**
     * Лимитер FPS.
     * Если кадр отрисовался быстрее, чем нужно, поток засыпает до начала следующего кадра.
     */
    private void syncFPS() {
        if (targetFPS <= 0) return; // Если лимит отключен - выходим

        double targetFrameTime = 1.0 / targetFPS;
        double targetEndTime = lastFrameTime + targetFrameTime;
        double currentTime = GLFW.glfwGetTime();

        // Если мы уложились в рамки времени кадра, нужно подождать
        if (currentTime < targetEndTime) {
            double timeToSleep = targetEndTime - currentTime;

            // Thread.sleep() не очень точен (обычно гранулярность ~1мс).
            // Поэтому мы спим чуть меньше, а остаток времени ждем в цикле (busy-wait) для точности.
            long sleepMillis = (long) (timeToSleep * 1000) - 2; // -2 мс для запаса

            if (sleepMillis > 0) {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Точный досчет времени (busy-wait)
            while (GLFW.glfwGetTime() < targetEndTime) {
                // Если используете Java 9+, можно заменить на Thread.onSpinWait();
                // Это подскажет процессору, что мы в цикле ожидания, и снизим потребление ресурсов.
            }
        }
    }

    /**
     * Устанавливает целевое количество кадров в секунду.
     * @param fps Желаемый FPS. Передайте 0 или отрицательное значение, чтобы снять лимит.
     */
    public void setTargetFPS(int fps) {
        targetFPS = fps;
    }

    public int getTargetFPS() {
        return targetFPS;
    }

    /**
     * Возвращает время между текущим и предыдущим кадром в секундах.
     * Используется для плавной анимации и движения.
     */
    public float getDeltaSecond() {
        return deltaTime;
    }

    /**
     * Возвращает общее время, прошедшее с момента старта движка, в секундах.
     */
    public double getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Возвращает текущее значение FPS (Frames Per Second).
     */
    public int getFPS() {
        return currentFPS;
    }
}