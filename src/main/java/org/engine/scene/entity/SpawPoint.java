package org.engine.scene.entity;

import org.engine.scene.GameObject;
import org.joml.Vector3f;

public class SpawPoint extends GameObject {



    public SpawPoint(String name,
                   Vector3f startPosition
    ) {

        super(0, name,null, null, null, false, 0.0f, startPosition, new Vector3f(0.0f,0.0f,0.0f), new Vector3f(1.0f,1.0f,1.0f));




    }

    @Override
    public javax.vecmath.Vector3f getPosition() {
        return super.getPosition();
    }

}
