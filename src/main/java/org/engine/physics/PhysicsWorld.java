package org.engine.physics;



import com.bulletphysics.collision.broadphase.AxisSweep3;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import org.engine.scene.entity.Trigger;

import javax.vecmath.Vector3f;
import java.util.HashSet;
import java.util.Set;

public class PhysicsWorld {
    public DiscreteDynamicsWorld dynamicsWorld;

    public void init() {
        CollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
        CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);

        Vector3f worldAabbMin = new Vector3f(-10000, -10000, -10000);
        Vector3f worldAabbMax = new Vector3f(10000, 10000, 10000);
        AxisSweep3 broadphase = new AxisSweep3(worldAabbMin, worldAabbMax);

        SequentialImpulseConstraintSolver solver = new SequentialImpulseConstraintSolver();

        dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);
        dynamicsWorld.setGravity(new Vector3f(0, -9.81f, 0));
    }

    public void step(float deltaTime) {

        dynamicsWorld.stepSimulation(deltaTime*2.0f, 16);


    }




    public RigidBody addBox(float mass, Vector3f size, Vector3f position) {
        CollisionShape shape = new BoxShape(new Vector3f(size.x / 2, size.y / 2, size.z / 2));
        Transform transform = new Transform();
        transform.setIdentity();
        transform.origin.set(position);

        DefaultMotionState motionState = new DefaultMotionState(transform);
        Vector3f localInertia = new Vector3f(0, 0, 0);
        if (mass > 0) {
            shape.calculateLocalInertia(mass, localInertia);
        }

        RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(mass, motionState, shape, localInertia);
        RigidBody body = new RigidBody(rbInfo);
        dynamicsWorld.addRigidBody(body);
        return body;
    }

    public void cleanup() {
        // Здесь нужно удалить все тела из мира (в реальном проекте)
    }



}