#version 330 core

// Атрибуты вершин (обычно настраиваются при создании VAO/Mesh)
layout (location = 0) in vec3 aPos;      // Позиция вершины
layout (location = 1) in vec2 aTexCoord; // UV координаты текстуры

// Матрицы, которые вы передаете из Java (uniform)
uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;

// Выходные данные для фрагментного шейдера
out vec2 TexCoord;

void main() {
    // Умножение матриц: сначала модельная, затем вид, затем проекция
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    
    // Передаем UV координаты дальше
    TexCoord = aTexCoord;
}