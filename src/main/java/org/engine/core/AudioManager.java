package org.engine.core;

import org.joml.Vector3f;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Random; // Добавлен импорт

import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.AL10.*;

public class AudioManager {
    private static long device;
    private static long context;

    // Единый генератор случайных чисел для аудио-системы
    private static final Random random = new Random();

    // ==================== Глобальная громкость ====================

    /** Глобальная громкость (0.0 — тишина, 1.0 — максимум). Сохраняется между init()/cleanup(). */
    private static float globalVolume = 1.0f;

    /** Состояние полного отключения звука (mute). */
    private static boolean muted = false;

    /** Флаг того, что контекст OpenAL создан и вызовы al*() безопасны. */
    private static boolean initialized = false;

    public static void init() {
        device = alcOpenDevice((ByteBuffer) null);
        if (device == 0) {
            throw new IllegalStateException("Не удалось открыть аудио устройство");
        }

        ALCCapabilities deviceCaps = ALC.createCapabilities(device);
        context = alcCreateContext(device, (IntBuffer) null);
        alcMakeContextCurrent(context);

        ALCapabilities caps = AL.createCapabilities(deviceCaps);
        if (!caps.OpenAL10) {
            throw new IllegalStateException("OpenAL 1.0 не поддерживается");
        }

        initialized = true;

        // Повторно применяем сохранённую громкость после инициализации
        applyVolume(globalVolume);

        System.out.println("[AudioManager] OpenAL инициализирован. Громкость: " + globalVolume);
    }

    /**
     * Устанавливает глобальную громкость в диапазоне 0.0–1.0.
     * <p>
     * Реализуется через AL_GAIN слушателя: этот множитель применяется
     * ко ВСЕМ источникам сразу, поэтому отдельные Source менять не нужно.
     * Значение запоминается и автоматически восстановится после повторного init().
     *
     * @param volume громкость от 0.0 (тишина) до 1.0 (максимум); значения вне диапазона обрезаются
     */
    public static void setGlobalVolume(float volume) {
        globalVolume = clamp01(volume);
        applyVolume(globalVolume);
    }

    /**
     * Возвращает текущую глобальную громкость (0.0–1.0)
     */
    public static float getGlobalVolume() {
        return globalVolume;
    }

    /**
     * Полностью отключает звук, не сбрасывая установленную громкость.
     * Последующий вызов {@link #setMuted(boolean)} с false вернёт прежний уровень.
     */
    public static void setMuted(boolean muted) {
        AudioManager.muted = muted;
        applyVolume(globalVolume);
    }

    /**
     * @return true, если звук сейчас приглушён (mute)
     */
    public static boolean isMuted() {
        return muted;
    }

    /**
     * Фактическое применение громкости к слушателю OpenAL.
     * Учитывает состояние mute и защищает от вызовов до init().
     */
    private static void applyVolume(float volume) {
        if (!initialized) return; // контекста OpenAL ещё нет — просто запоминаем значение
        alListenerf(AL_GAIN, muted ? 0f : volume);
    }

    /**
     * Обрезает значение до диапазона [0.0, 1.0]
     */
    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    // ==================== Прочее ====================

    /**
     * Возвращает случайный индекс от 0 до (bound - 1)
     */
    public static int getRandomIndex(int bound) {
        if (bound <= 0) return 0;
        return random.nextInt(bound);
    }

    /**
     * Обновление позиции и ориентации слушателя (обычно привязывается к камере)
     */
    public static void updateListener(Vector3f position, Vector3f velocity, Vector3f forward, Vector3f up) {
        alListener3f(AL_POSITION, position.x, position.y, position.z);
        alListener3f(AL_VELOCITY, velocity.x, velocity.y, velocity.z);

        // Ориентация: первые 3 float - forward, вторые 3 float - up
        float[] orientation = {
                forward.x, forward.y, forward.z,
                up.x, up.y, up.z
        };
        alListenerfv(AL_ORIENTATION, orientation);
    }

    public static void cleanup() {
        alcDestroyContext(context);
        alcCloseDevice(device);
        initialized = false;
        System.out.println("[AudioManager] OpenAL уничтожен.");
    }
}
