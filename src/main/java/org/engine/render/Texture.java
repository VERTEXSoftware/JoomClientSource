package org.engine.render;

import org.engine.utils.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Texture {
    private final int textureId;

    // Конструктор для загрузки по пути (использует ResourceManager)
    public Texture(String filePath) {
        this(ResourceManager.getResourceOrError(filePath, "texture/error.png"));
    }
    public static void saveBufferToFile(ByteBuffer buffer, String filePath) throws IOException {
        buffer.rewind(); // вернуться к началу
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Files.write(Paths.get(filePath), bytes);
        buffer.rewind(); // вернуть позицию, чтобы не сломать дальнейшее использование
    }

    // Конструктор для загрузки напрямую из буфера памяти
    public Texture(ByteBuffer imageBuffer) {
        if (imageBuffer == null) {
            throw new RuntimeException("Texture buffer is null! (Возможно, отсутствует texture/error.png в ресурсах)");
        }




        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);

            ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, width, height, channels, 4);
            if (image == null) {
                throw new RuntimeException("Failed to load texture from memory: " + STBImage.stbi_failure_reason());
            }

            textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            // --- ДОБАВЛЕНО: Установка параметров текстуры (ОБЯЗАТЕЛЬНО!) ---
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width.get(), height.get(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image);

            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            STBImage.stbi_image_free(image);
        }
    }

    public int getTextureId() {
        return textureId;
    }

    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        GL11.glDeleteTextures(textureId);
    }
}