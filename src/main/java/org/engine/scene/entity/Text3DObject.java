package org.engine.scene.entity;

import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.joml.Vector3f;

public class Text3DObject extends GameObject {

    private String text;
    private float charScale = 1.0f; // Размер одной буквы в 3D мире
    private float otstup = 1.0f;
    private boolean centered = false; // Флаг центрирования текста

    public Text3DObject(String name, String text, Shader shader, Texture fontTexture,boolean iscentered,Vector3f position) {
        // Вызываем конструктор родителя. createph = false (без физики)
        super(0, name, null, shader, fontTexture, false, 0f, position, new Vector3f(0,0,0), new Vector3f(1,1,1));

        this.text = text;
        this.centered =iscentered;
        // Генерируем меш с буквами
        this.mesh = createTextMesh(text);
    }

    /**
     * Включает или выключает центрирование текста.
     * @param centered true - текст будет по центру, false - выравнивание по левому краю.
     */
    public void setCentered(boolean centered) {
        // Если состояние изменилось, пересоздаем меш
        if (this.centered != centered) {
            this.centered = centered;
            if (this.mesh != null) {
                this.mesh.cleanup();
            }
            this.mesh = createTextMesh(this.text);
        }
    }

    /**
     * Изменяет текст на лету и обновляет 3D меш
     */
    public void setText(String newText) {
        if (!this.text.equals(newText)) {
            this.text = newText;
            if (this.mesh != null) {
                this.mesh.cleanup(); // Удаляем старый меш из памяти OpenGL
            }
            this.mesh = createTextMesh(newText);
        }
    }

    /**
     * Генерирует Mesh из строки текста, используя UV-координаты шрифта-атласа (сетка 16x16)
     */
    private Mesh createTextMesh(String text) {
        // 1. Считаем количество видимых символов (пробелы не генерируют вершины)
        int validChars = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != ' ') {
                validChars++;
            }
        }

        // Если строка пустая или состоит только из пробелов
        if (validChars == 0) {
            return new Mesh(new float[0], new float[0], new int[0], false);
        }

        // 2. Вычисляем общую ширину текста для центрирования
        float totalWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                totalWidth += charScale * otstup; // Ширина пробела
            } else {
                totalWidth += charScale * otstup; // Ширина буквы + отступ
            }
        }

        // 3. Инициализируем массивы точного размера
        float[] vertices = new float[validChars * 4 * 3];
        float[] uvs = new float[validChars * 4 * 2];
        int[] indices = new int[validChars * 6];

        // Вычисляем начальную позицию курсора
        float cursorX = centered ? -(totalWidth / 2.0f) : 0;

        int vIndex = 0;
        int uvIndex = 0;
        int iIndex = 0;
        int charCount = 0;

        // 4. Заполняем массивы данными
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Если пробел, просто сдвигаем курсор и переходим к следующей букве
            if (c == ' ') {
                cursorX += charScale * otstup;
                continue;
            }

            // Вычисляем позицию символа на текстуре (сетка 16x16)
            int charIndex = (int) c;
            int col = charIndex % 16;
            int row = charIndex / 16;

            float charSizeUV = 1.0f / 16.0f;

            float v0 = row * charSizeUV;
            float v1 = (row + 1) * charSizeUV;

            float u0 = col * charSizeUV;
            float u1 = (col + 1) * charSizeUV;

            // Вершины квада (в плоскости XY)
            // 1: Левый нижний угол
            vertices[vIndex++] = cursorX;
            vertices[vIndex++] = 0;
            vertices[vIndex++] = 0;
            // 2: Правый нижний угол
            vertices[vIndex++] = cursorX + charScale;
            vertices[vIndex++] = 0;
            vertices[vIndex++] = 0;
            // 3: Правый верхний угол
            vertices[vIndex++] = cursorX + charScale;
            vertices[vIndex++] = charScale;
            vertices[vIndex++] = 0;
            // 4: Левый верхний угол
            vertices[vIndex++] = cursorX;
            vertices[vIndex++] = charScale;
            vertices[vIndex++] = 0;

            // UV координаты
            uvs[uvIndex++] = u0; uvs[uvIndex++] = v1; // Низ левый
            uvs[uvIndex++] = u1; uvs[uvIndex++] = v1; // Низ правый
            uvs[uvIndex++] = u1; uvs[uvIndex++] = v0; // Верх правый
            uvs[uvIndex++] = u0; uvs[uvIndex++] = v0; // Верх левый

            // Индексы (2 треугольника)
            int startVert = charCount * 4;
            indices[iIndex++] = startVert;
            indices[iIndex++] = startVert + 1;
            indices[iIndex++] = startVert + 2;

            indices[iIndex++] = startVert;
            indices[iIndex++] = startVert + 2;
            indices[iIndex++] = startVert + 3;

            // Сдвигаем курсор для следующей буквы и увеличиваем счетчик букв
            cursorX += charScale * otstup;
            charCount++;
        }

        // 5. Создаем и возвращаем Mesh
        return new Mesh(vertices, uvs, indices, false);
    }
}