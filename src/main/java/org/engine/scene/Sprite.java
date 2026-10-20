package org.engine.scene;

import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Sprite {
    private final Mesh mesh;
    private final Shader shader;
    private final Texture texture;

    private final Vector3f position;
    private final Vector3f scale;
    private float rotation;

    private final Matrix4f modelMatrix;

    public Sprite(Shader shader, Texture texture, Vector3f position, Vector3f scale) {
        this.shader = shader;
        this.texture = texture;
        this.position = position;
        this.scale = scale;
        this.rotation = 0.0f;
        this.modelMatrix = new Matrix4f();

        // Создаем стандартный квад (прямоугольник) для спрайта
        // Вершины формируются вокруг центра (0,0)
        float[] positions = new float[] {
                -0.5f,  0.5f, 0.0f,   // Верх-лево
                -0.5f, -0.5f, 0.0f,   // Низ-лево
                0.5f, -0.5f, 0.0f,   // Низ-право
                0.5f,  0.5f, 0.0f    // Верх-право
        };

        float[] texCoords = new float[] {
                0.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f,
                1.0f, 0.0f
        };

        int[] indices = new int[] { 0, 1, 2, 2, 3, 0 };

        this.mesh = new Mesh(positions, texCoords, indices,false);
    }

    public void updateModelMatrix() {
        modelMatrix.identity()
                .translate(position)
                .rotateZ((float) Math.toRadians(rotation))
                .scale(scale);
    }

    // Сеттеры для перемещения спрайта
    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

   public void setScale(float x, float y, float z) {
       scale.set(x, y, z);
   }

    public Mesh getMesh() { return mesh; }
    public Shader getShader() { return shader; }
    public Texture getTexture() { return texture; }
    public Matrix4f getModelMatrix() { return modelMatrix; }
    public Vector3f getPosition() { return position; }

    public void cleanup() {
        mesh.cleanup();
        texture.cleanup();
    }
}