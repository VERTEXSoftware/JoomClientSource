package org.engine.render;

public class Billboard extends Mesh {

    /**
     * Создает билбоард (плоский квад) размером width x height.
     * В классическом стиле Doom билбоард привязан нижней частью к полу (Y=0).
     *
     * @param width  Ширина спрайта
     * @param height Высота спрайта
     */
    public Billboard(float width, float height) {
        super(
                createPositions(width, height),
                createTexCoords(),
                createIndices(),true
        );
    }

    private static float[] createPositions(float width, float height) {
        float halfWidth = width / 2.0f;

        // Плоскость в XY (смотрит вдоль оси Z по умолчанию)
        // Y = 0 это низ спрайта (он стоит на полу)
        return new float[] {
                -halfWidth, -height / 2.0f, 0.0f,   // Нижняя левая
                halfWidth, -height / 2.0f, 0.0f,   // Нижняя правая
                halfWidth,  height / 2.0f, 0.0f,   // Верхняя правая
                -halfWidth,  height / 2.0f, 0.0f    // Верхняя левая
        };
    }

    private static float[] createTexCoords() {
        // Стандартные UV координаты для текстуры
        return new float[] {
                0.0f, 1.0f, // Нижняя левая (в OpenGL UV (0,0) - это низ, но если текстура перевернута, используй 0.0f, 0.0f)
                1.0f, 1.0f, // Нижняя правая
                1.0f, 0.0f, // Верхняя правая
                0.0f, 0.0f  // Верхняя левая
        };
    }

    private static int[] createIndices() {
        // Два треугольника
        return new int[] {
                0, 1, 2,
                2, 3, 0
        };
    }
}