#version 330 core

// Входные данные от вершинного шейдера
in vec2 TexCoord;

// Результирующий цвет пикселя
out vec4 FragColor;

// Текстура объекта (сэмплер)
uniform sampler2D tex;

// Цвет-модификатор (RGBA). По умолчанию (1,1,1,1) — текстура без изменений
uniform vec4 color;

void main() {
    // Сэмплируем цвет из текстуры по UV координатам
    vec4 texColor = texture(tex, TexCoord);

    // Умножаем цвет текстуры на заданный цвет (тинт)
    FragColor = texColor * color;

    // Если итоговая альфа почти нулевая — discard
    if(FragColor.a < 0.1)
        discard;
}