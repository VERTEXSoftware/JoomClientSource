package org.engine.render;

import org.engine.utils.ResourceManager;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Shader {
    private final int programId;

    public Shader(String vertexPath, String fragmentPath) throws Exception {
        ByteBuffer vBuff = ResourceManager.getResourceOrError(vertexPath, "vertex.glsl");
        ByteBuffer fBuff = ResourceManager.getResourceOrError(fragmentPath, "fragment.glsl");

        if (vBuff == null || fBuff == null) {
            throw new RuntimeException("Failed to load shaders and fallback shaders are missing in JAR resources.");
        }

        String vertexSource = StandardCharsets.UTF_8.decode(vBuff).toString();
        String fragmentSource = StandardCharsets.UTF_8.decode(fBuff).toString();

        int vertexId = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexId, vertexSource);
        GL20.glCompileShader(vertexId);

        int fragmentId = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentId, fragmentSource);
        GL20.glCompileShader(fragmentId);

        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexId);
        GL20.glAttachShader(programId, fragmentId);
        GL20.glLinkProgram(programId);

        GL20.glDeleteShader(vertexId);
        GL20.glDeleteShader(fragmentId);
    }

    public void bind() { GL20.glUseProgram(programId); }
    public void unbind() { GL20.glUseProgram(0); }
    public int getProgramId() { return programId; }

    // ДОБАВЛЕН МЕТОД ОЧИСТКИ
    public void cleanup() {
        GL20.glDeleteProgram(programId);
    }
}