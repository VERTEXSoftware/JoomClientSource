package org.engine.render;

import org.engine.scene.AABB;
import org.engine.scene.GameObject;
import org.engine.scene.Sprite;
import org.engine.scene.entity.HealthBar3D;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.*;

public class Renderer {

    /** Максимальный размер очереди на кадр — защита от утечки/двойного submit. */
    private static final int MAX_QUEUE_SIZE = 100_000;

    /** Identity-матрица для ортографического прохода спрайтов (view = identity). */
    private static final Matrix4f IDENTITY = new Matrix4f();

    private Camera camera;
    private FloatBuffer matrixBuffer;

    /** Какой шейдер реально сейчас забинжен в GL. null == ничего / шейдер 0. */
    private Shader boundShader = null;

    /** Кэш uniform'ов на шейдер. Очищается через {@link #invalidateShader(Shader)}. */
    private final Map<Shader, ShaderUniforms> uniformCache = new IdentityHashMap<>();

    // --- Очереди на кадр ---
    private final List<GameObject> opaqueQueue    = new ArrayList<>();
    private final List<GameObject> healthBarQueue = new ArrayList<>();

    /** Флаг: был ли вызван beginFrame() и ещё не было flush(). */
    private boolean frameBegun = false;

    public void init(Camera camera) throws Exception {
        this.camera = camera;
        this.matrixBuffer = BufferUtils.createFloatBuffer(16);
        this.boundShader = null;

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glClearDepth(1.0);

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glDepthMask(true);
    }

    // ============================================================
    //                       ОЧЕРЕДИ
    // ============================================================

    /**
     * Вызывать РОВНО ОДИН РАЗ в начале кадра, до всех submit().
     * Повторный вызов без flush() — ошибка.
     */
    public void beginFrame() {
        if (frameBegun) {
            throw new IllegalStateException(
                    "Renderer.beginFrame() called twice without flush(). " +
                            "Каждый кадр должен быть: beginFrame() -> submit()* -> flush().");
        }
        frameBegun = true;
        opaqueQueue.clear();
        healthBarQueue.clear();
    }

    /**
     * Кладёт объект в очередь. Реальная отрисовка — в {@link #flush()}.
     * Здесь же делаем быстрый отсев (visible, mesh, AABB), чтобы не тащить мусор в очередь.
     */
    public void submit(GameObject gameObject) {
        if (gameObject == null || !gameObject.IsVisible()) return;

        Mesh mesh = gameObject.getMesh();
        if (mesh == null) return;
        if (gameObject.getShader() == null) return;

        if (mesh.IsUseAABB()) {
            AABB worldAABB = gameObject.getWorldAABB();
            if (!camera.isAABBVisible(worldAABB)) return;
        }

        if (gameObject instanceof HealthBar3D) {
            if (healthBarQueue.size() >= MAX_QUEUE_SIZE) {
                System.err.println("[Renderer] healthBarQueue overflow, skip object: " + gameObject);
                return;
            }
            healthBarQueue.add(gameObject);
        } else {
            if (opaqueQueue.size() >= MAX_QUEUE_SIZE) {
                System.err.println("[Renderer] opaqueQueue overflow, skip object: " + gameObject);
                return;
            }
            opaqueQueue.add(gameObject);
        }
    }

    // ============================================================
    //                       FLUSH (DRAW)
    // ============================================================

    /**
     * Рисует всё, что накопилось в очередях.
     * Сортировки нет: шейдеры часто повторяются, поэтому группировка
     * не даёт выигрыша — рисуем в порядке submit().
     */
    public void flush() {
        // --- 1. Обычные объекты ---
        drawBatch(opaqueQueue, false);

        // --- 2. HealthBar3D (отдельный проход, доп. uniform) ---
        drawBatch(healthBarQueue, true);

        // Кадр завершён — разрешаем следующий beginFrame()
        frameBegun = false;
    }

    /**
     * @param withHealthUniform true — для HealthBar3D, передаём healthPercent на объект.
     */
    private void drawBatch(List<GameObject> queue, boolean withHealthUniform) {
        if (queue.isEmpty()) return;

        Shader current = null;
        ShaderUniforms currentU = null;
        Texture currentTex = null;

        for (int i = 0, n = queue.size(); i < n; i++) {
            GameObject go = queue.get(i);
            Shader shd = go.getShader();

            // --- Смена шейдера ---
            if (shd != current) {
                current = shd;
                currentU = uniformsFor(shd);
                bindShader(shd);

                // projection/view — один раз на шейдер за проход
                camera.getProjectionMatrix().get(matrixBuffer.rewind());
                GL20.glUniformMatrix4fv(currentU.projection, false, matrixBuffer);

                camera.getViewMatrix().get(matrixBuffer.rewind());
                GL20.glUniformMatrix4fv(currentU.view, false, matrixBuffer);
            }

            // --- Смена текстуры ---
            // Не unbind'им старую: следующий bind() перезапишет привязку.
            Texture tex = go.getTexture();
            if (tex != currentTex) {
                if (tex != null) tex.bind();
                currentTex = tex;
            }

            // --- model ---
            go.getModelMatrix().get(matrixBuffer.rewind());
            GL20.glUniformMatrix4fv(currentU.model, false, matrixBuffer);

            // --- color (пропускаем, если не изменился) ---
            Vector4f clr = go.getColor();
            GL20.glUniform4f(currentU.color, clr.x, clr.y, clr.z, clr.w);

            // --- healthPercent (только для HealthBar3D) ---
            if (withHealthUniform && currentU.healthPercent >= 0 && go instanceof HealthBar3D) {
                GL20.glUniform1f(currentU.healthPercent, ((HealthBar3D) go).getHealthPercent());
            }

            // --- draw ---
            go.getMesh().render();
        }

        // Финальный unbind текстуры НЕ нужен — следующий bind() перезапишет.
    }

    // ============================================================
    //                       СПРАЙТЫ
    // ============================================================

    /**
     * Спрайты рисуем отдельным проходом: depth test выкл., cull выкл., blend вкл.
     * После — восстанавливаем 3D-состояние через renderset3d().
     */
    public void renderSprite(Sprite sprite) {
        if (sprite == null) return;

        Shader shd = sprite.getShader();
        Mesh mesh = sprite.getMesh();
        Texture texture = sprite.getTexture();
        if (shd == null || mesh == null) return;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        bindShader(shd);
        ShaderUniforms u = uniformsFor(shd);

        // projection — ортографическая
        camera.getOrthoMatrix().get(matrixBuffer.rewind());
        GL20.glUniformMatrix4fv(u.projection, false, matrixBuffer);

        // view = identity (ортопроекция работает в screen-space)
        if (u.view >= 0) {
            IDENTITY.get(matrixBuffer.rewind());
            GL20.glUniformMatrix4fv(u.view, false, matrixBuffer);
        }

        sprite.updateModelMatrix();
        sprite.getModelMatrix().get(matrixBuffer.rewind());
        GL20.glUniformMatrix4fv(u.model, false, matrixBuffer);

        if (texture != null) texture.bind();
        mesh.render();
        if (texture != null) texture.unbind();

        // depth/cull восстановит renderset3d()/clear()
    }

    // ============================================================
    //                       СОСТОЯНИЕ
    // ============================================================

    public void renderset3d() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glDepthMask(true);

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);
    }

    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        renderset3d();

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // сбрасываем кэш шейдера, т.к. GL-состояние могло измениться извне
        boundShader = null;
    }

    // ============================================================
    //                       ВНУТРЕННЕЕ
    // ============================================================

    private void bindShader(Shader shd) {
        if (shd == boundShader) return;
        if (boundShader != null) boundShader.unbind();
        shd.bind();
        boundShader = shd;
    }

    private ShaderUniforms uniformsFor(Shader shd) {
        return uniformCache.computeIfAbsent(shd,
                s -> new ShaderUniforms(s.getProgramId()));
    }

    /**
     * Удаляет кэш uniform'ов для шейдера.
     * Вызывать при удалении/пересоздании шейдера, иначе IdentityHashMap держит ссылку вечно.
     */
    public void invalidateShader(Shader shd) {
        if (shd == null) return;
        uniformCache.remove(shd);
        if (boundShader == shd) {
            boundShader = null;
        }
    }

    /** Полная очистка кэша uniform'ов (например, при смене сцены). */
    public void clearUniformCache() {
        uniformCache.clear();
        boundShader = null;
    }
}