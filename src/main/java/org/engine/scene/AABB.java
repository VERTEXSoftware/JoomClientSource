package org.engine.scene;

import org.joml.Vector3f;

public class AABB {
    public final Vector3f min;
    public final Vector3f max;

    public AABB() {
        this.min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        this.max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
    }

    public AABB(Vector3f min, Vector3f max) {
        this.min = min;
        this.max = max;
    }

    public void reset() {
        min.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        max.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
    }

    // Расширяем границы, чтобы включить новую точку
    public void expand(float x, float y, float z) {
        if (x < min.x) min.x = x;
        if (y < min.y) min.y = y;
        if (z < min.z) min.z = z;
        if (x > max.x) max.x = x;
        if (y > max.y) max.y = y;
        if (z > max.z) max.z = z;
    }
}