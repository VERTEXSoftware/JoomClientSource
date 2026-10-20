package org.engine.scene.entity;

import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.joml.Vector3f;

public class HealthBar3D extends GameObject {

    private float maxHealth;
    private float currentHealth;

    public HealthBar3D(float maxHealth, Shader shader) {
        // Масштаб по X и Y будет 1, текстура не нужна (рисуем цветом в шейдере)
        super(0,"HealthBar", null, shader, null, false, 0f, new Vector3f(0,0,0), new Vector3f(0,0,0), new Vector3f(4,1,4));

        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;

        this.mesh = createBarMesh();
    }

    public void setHealth(float health) {
        if (health < 0) health = 0;
        if (health > maxHealth) health = maxHealth;
        this.currentHealth = health;
    }

    // Метод для получения процента здоровья (от 0.0 до 1.0)
    public float getHealthPercent() {
        return currentHealth / maxHealth;
    }

    /**
     * Создает отцентрованный квадрат (от -0.5 до 0.5 по X)
     */
    private Mesh createBarMesh() {
        // ИЗМЕНЕНИЕ: Координаты X теперь от -0.5 до 0.5 (центр в нуле)
        float[] vertices = new float[] {
                -0.5f, 0.0f, 0.0f,  // 0: Левый нижний
                0.5f, 0.0f, 0.0f,  // 1: Правый нижний
                0.5f, 1.0f, 0.0f,  // 2: Правый верхний
                -0.5f, 1.0f, 0.0f   // 3: Левый верхний
        };

        float[] uvs = new float[] {
                0.0f, 0.0f, // 0: Левый нижний
                1.0f, 0.0f, // 1: Правый нижний
                1.0f, 1.0f, // 2: Правый верхний
                0.0f, 1.0f  // 3: Левый верхний
        };

        int[] indices = new int[] {
                0, 1, 2,
                0, 2, 3
        };

        return new Mesh(vertices, uvs, indices, false);
    }
}