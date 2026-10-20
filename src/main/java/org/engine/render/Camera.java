package org.engine.render;

import org.engine.scene.AABB;
import org.engine.scene.GameObject;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera extends GameObject {

    private final Matrix4f projectionMatrix;
    private final Matrix4f frustumprojectionMatrix;
    private final Matrix4f viewMatrix;
    private final Matrix4f orthoMatrix; // Добавлено для 2D

    // Для Frustum Culling
    private final Matrix4f projViewMatrix;
    private final Matrix4f frustumprojViewMatrix;
    private final FrustumIntersection frustum;

    // Временные векторы для оптимизации (чтобы не создавать мусор каждый кадр)
    private final Vector3f position;
    private final Vector3f forward;
    private final Vector3f up;
    private final Vector3f target;

    private final float fov;
    private final float nearPlane;
    private final float farPlane;

    private float width;
    private float height;

    // Поля для управления мышью (если понадобятся)
    private double lastMouseX;
    private double lastMouseY;
    private boolean firstMouse = true;

    // Изменено: передаем width и height вместо aspectRatio
    public Camera(Vector3f position, Vector3f rotation, float fov, float width, float height, float nearPlane, float farPlane) {
        super(0,"Camera", null, null, null, false, 0.0f, position, rotation, new Vector3f(1.0f, 1.0f, 1.0f));

        this.fov = fov;
        this.width = width;
        this.height = height;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;

        float aspectRatio = width / height;

        this.projectionMatrix = new Matrix4f().perspective((float) Math.toRadians(fov), aspectRatio, nearPlane, farPlane);
        this.frustumprojectionMatrix= new Matrix4f().perspective((float) Math.toRadians(fov+15), aspectRatio, nearPlane, farPlane);

        // Инициализация ортографической матрицы для 2D
        // Координаты: от 0 до Width по X, от 0 до Height по Y (Y инвертирован, чтобы 0 был сверху)
        this.orthoMatrix = new Matrix4f().ortho(0.0f, width, height, 0.0f, -1.0f, 1.0f);

        this.viewMatrix = new Matrix4f();

        this.projViewMatrix = new Matrix4f();
        this.frustumprojViewMatrix = new Matrix4f();
        this.frustum = new FrustumIntersection();

        // Инициализируем временные векторы
        this.position = new Vector3f();
        this.forward = new Vector3f();
        this.up = new Vector3f();
        this.target = new Vector3f();
    }

    // Метод для обновления матриц при изменении размера окна
    public void resize(int newWidth, int newHeight) {
        this.width = newWidth;
        this.height = newHeight;
        float aspectRatio = (float) newWidth / (float) newHeight;

        this.projectionMatrix.setPerspective((float) Math.toRadians(fov), aspectRatio, nearPlane, farPlane);
        this.orthoMatrix.setOrtho(0.0f, newWidth, newHeight, 0.0f, -1.0f, 1.0f);
    }

    public Vector3f getForward() {
        return forward;
    }

    public Vector3f getUp() {
        return up;
    }


    public Vector3f getCameraPosition() {
        return position;
    }

    public Matrix4f getViewMatrix() {
        // 1. Получаем позицию из JBullet Transform
        javax.vecmath.Vector3f bulletPos = transform.origin;
        position.set(bulletPos.x, bulletPos.y, bulletPos.z);

        // 2. Извлекаем направление "Вперед" (Forward) и "Вверх" (Up)
        // JBullet хранит вращение в матрице basis (Matrix3f).
        javax.vecmath.Matrix3f basis = transform.basis;

        // В стандартной конвенции OpenGL/JOML:
        // Forward — это локальная ось -Z. В матрице basis это третья колонка (m02, m12, m22).
        forward.set(-basis.m02, -basis.m12, -basis.m22);

        // Up — это локальная ось +Y. В матрице basis это вторая колонка (m01, m11, m21).
        up.set(basis.m01, basis.m11, basis.m21);

        // 3. Вычисляем целевую точку (Position + Forward)
        target.set(position).add(forward);

        // 4. Строим View матрицу через JOML lookAt
        viewMatrix.identity();
        viewMatrix.lookAt(position, target, up);

        // 5. Обновляем фрустум при каждом обновлении View Matrix
        projViewMatrix.set(projectionMatrix).mul(viewMatrix);
        frustumprojViewMatrix.set(frustumprojectionMatrix).mul(viewMatrix);
        frustum.set(frustumprojViewMatrix);
        return viewMatrix;
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    // Геттер для ортографической матрицы
    public Matrix4f getOrthoMatrix() {
        return orthoMatrix;
    }

    /**
     * Проверяет, попадает ли AABB в поле зрения камеры.
     * @return true если виден, false если отсечен.
     */
    public boolean isAABBVisible(AABB aabb) {
        if (aabb == null) return true;

        return frustum.testAab(aabb.min, aabb.max);
        // Если нужно строгое отсечение (только если целиком внутри), используйте:
        // return frustum.testAab(aabb.min, aabb.max) == FrustumIntersection.INSIDE;
        // Но обычно лучше оставлять INTERSECT (пересечение), чтобы объекты не пропадали у краев экрана.
    }
}