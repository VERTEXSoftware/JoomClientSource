package org.engine.render;

import org.lwjgl.opengl.GL20;

/** Кэш uniform-локаций для одного шейдера. -1 == uniform отсутствует. */
final class ShaderUniforms {

    final int projection;
    final int view;
    final int model;
    final int color;
    final int healthPercent;

    ShaderUniforms(int programId) {
        this.projection    = GL20.glGetUniformLocation(programId, "projection");
        this.view          = GL20.glGetUniformLocation(programId, "view");
        this.model         = GL20.glGetUniformLocation(programId, "model");
        this.color         = GL20.glGetUniformLocation(programId, "color");
        this.healthPercent = GL20.glGetUniformLocation(programId, "healthPercent");
    }
}