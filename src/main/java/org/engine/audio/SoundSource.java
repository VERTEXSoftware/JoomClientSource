package org.engine.audio;

import org.engine.core.AudioManager;
import org.joml.Vector3f;

import java.util.List;

import static org.lwjgl.openal.AL10.*;

public class SoundSource {
    private final int sourceId;

    public SoundSource() {
        sourceId = alGenSources();

        // Настройки по умолчанию
        setAmbient(false);

        setGain(0.7f);
        setPitch(1.0f);
        setPosition(0, 0, 0);
        setVelocity(0, 0, 0);

        // Настройки радиуса по умолчанию
        setReferenceDistance(15.0f);
        setRolloffFactor(0.8f);
        setMaxDistance(100.0f);

        // По умолчанию источники 3D
        alSourcei(sourceId, AL_DISTANCE_MODEL, AL_INVERSE_DISTANCE_CLAMPED);
    }

    public void play(int bufferId, boolean loop) {
        stop();
        alSourcei(sourceId, AL_BUFFER, bufferId);
        alSourcei(sourceId, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
        alSourcePlay(sourceId);
    }

    public void play(SoundBuffer buffer, boolean loop) {
        stop();
        alSourcei(sourceId, AL_BUFFER, buffer.getBufferId());
        alSourcei(sourceId, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
        alSourcePlay(sourceId);
    }

    public void playRnd(List<SoundBuffer> buffers, boolean loop) {
        if (buffers == null || buffers.isEmpty()) {
            return;
        }

        int randomIndex = AudioManager.getRandomIndex(buffers.size());
        SoundBuffer randomBuffer = buffers.get(randomIndex);
        play(randomBuffer, loop);
    }

    public void stop() {
        alSourceStop(sourceId);
    }

    public void pause() {
        alSourcePause(sourceId);
    }

    public boolean isPlaying() {
        return alGetSourcei(sourceId, AL_SOURCE_STATE) == AL_PLAYING;
    }

    public void setAmbient(boolean isAmbient) {
        if (isAmbient) {
            alSourcei(sourceId, AL_SOURCE_RELATIVE, AL_TRUE);
            alSource3f(sourceId, AL_POSITION, 0, 0, 0);
            alSourcef(sourceId, AL_ROLLOFF_FACTOR, 0.0f);
            alSourcef(sourceId, AL_MAX_DISTANCE, Float.MAX_VALUE); // не ограничивать
        } else {
            alSourcei(sourceId, AL_SOURCE_RELATIVE, AL_FALSE);
            alSourcef(sourceId, AL_ROLLOFF_FACTOR, 1.0f);
        }
    }

    public void setPosition(Vector3f pos) {
        setPosition(pos.x, pos.y, pos.z);
    }

    public void setPosition(javax.vecmath.Vector3f pos) {
        setPosition(pos.x, pos.y, pos.z);
    }

    public void setPosition(float x, float y, float z) {
        if (alGetSourcei(sourceId, AL_SOURCE_RELATIVE) == AL_TRUE) return;
        alSource3f(sourceId, AL_POSITION, x, y, z);
    }

    public void setVelocity(Vector3f vel) {
        setVelocity(vel.x, vel.y, vel.z);
    }

    public void setVelocity(float x, float y, float z) {
        alSource3f(sourceId, AL_VELOCITY, x, y, z);
    }

    public void setGain(float gain) {
        alSourcef(sourceId, AL_GAIN, gain);
    }

    public void setVolume(float gain) {
        alSourcef(sourceId, AL_GAIN, gain);
    }

    public void setPitch(float pitch) {
        alSourcef(sourceId, AL_PITCH, pitch);
    }

    // --- НОВЫЕ МЕТОДЫ ДЛЯ НАСТРОЙКИ РАДИУСА ---

    /**
     * Дистанция, на которой звук играет с максимальной громкостью (без затухания).
     * Если установить большое значение, звук будет "доноситься" издалека четко.
     * @param distance Дистанция в метрах (по умолчанию 1.0f)
     */
    public void setReferenceDistance(float distance) {
        alSourcef(sourceId, AL_REFERENCE_DISTANCE, distance);
    }

    /**
     * Скорость затухания звука.
     * 1.0f - стандартная физика звука.
     * 0.5f - звук затухает медленнее (слышно дальше).
     * 0.0f - звук вообще не затухает (аналог setAmbient(true), но с 3D позицией).
     * > 1.0f - звук затухает очень быстро.
     * @param factor Фактор затухания
     */
    public void setRolloffFactor(float factor) {
        alSourcef(sourceId, AL_ROLLOFF_FACTOR, factor);
    }

    /**
     * Максимальная дистанция, после которой звук перестает затухать.
     * @param distance Максимальная дистанция в метрах
     */
    public void setMaxDistance(float distance) {
        alSourcef(sourceId, AL_MAX_DISTANCE, distance);
    }

    public void cleanup() {
        stop();
        alSourcei(sourceId, AL_BUFFER, 0);
        alDeleteSources(sourceId);
    }
}