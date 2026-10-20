package org.engine.utils;

import org.engine.audio.SoundBuffer;
import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ResourceCache {
    // ConcurrentHashMap: безопасен при фоновом прогреве/загрузке из других потоков
    private static final Map<String, Texture> textureCache = new ConcurrentHashMap<>();
    private static final Map<String, Shader> shaderCache = new ConcurrentHashMap<>();
    private static final Map<String, Mesh> meshCache = new ConcurrentHashMap<>();
    private static final Map<String, SoundBuffer> soundCache = new ConcurrentHashMap<>();

    private ResourceCache() {}

    public static SoundBuffer getSound(String soundPath) {
        if (soundPath == null || soundPath.isEmpty()) return null;

        SoundBuffer sound = soundCache.get(soundPath);
        if (sound != null) {
            return sound;
        }

        try {
            sound = new SoundBuffer(soundPath);
            soundCache.put(soundPath, sound);
            return sound;
        } catch (Exception e) {
            System.err.println("Не удалось загрузить звук: " + soundPath);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Получает или загружает шейдер.
     */
    public static Shader getShader(String vertexPath, String fragmentPath) {
        String key = vertexPath + "|" + fragmentPath;
        Shader shader = shaderCache.get(key);
        if (shader != null) {
            return shader;
        }

        try {
            shader = new Shader(vertexPath, fragmentPath);
            shaderCache.put(key, shader);
            return shader;
        } catch (Exception e) {
            System.err.println("КРИТИЧЕСКАЯ ОШИБКА: Не удалось загрузить шейдер " + key);
            throw new RuntimeException(e);
        }
    }

    /**
     * Получает или загружает текстуру по пути.
     */
    public static Texture getTexture(String texturePath) {
        if (texturePath == null || texturePath.isEmpty()) return null;

        Texture texture = textureCache.get(texturePath);
        if (texture != null) {
            return texture;
        }

        ByteBuffer texBuffer = ResourceManager.getResourceOrError(texturePath, "texture/error.png");
        if (texBuffer == null) {
            System.err.println("Файл текстуры не найден ни в slvl, ни в JAR: " + texturePath);
            return null;
        }

        try {
            texture = new Texture(texBuffer);
            textureCache.put(texturePath, texture);
            return texture;
        } catch (RuntimeException e) {
            System.err.println("Не удалось загрузить текстуру: " + texturePath);
            return null;
        }
    }

    /**
     * Получает меш из кеша по ключу.
     */
    public static Mesh getMesh(String key) {
        return meshCache.get(key);
    }

    /**
     * Добавляет меш в кеш.
     */
    public static void putMesh(String key, Mesh mesh) {
        meshCache.put(key, mesh);
    }

    /**
     * Очистка всех ресурсов (вызывать при закрытии приложения или смене глобальной сцены)
     */
    public static void cleanup() {
        for (Texture tex : textureCache.values()) {
            if (tex != null) tex.cleanup();
        }
        textureCache.clear();

        for (Shader shd : shaderCache.values()) {
            if (shd != null) shd.cleanup();
        }
        shaderCache.clear();

        for (Mesh mesh : meshCache.values()) {
            if (mesh != null) mesh.cleanup();
        }
        meshCache.clear();

        for (SoundBuffer snd : soundCache.values()) {
            if (snd != null) snd.cleanup();
        }
        soundCache.clear();

    }
}