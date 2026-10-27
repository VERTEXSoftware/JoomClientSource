package org.engine.scene;

import com.bulletphysics.collision.broadphase.BroadphaseProxy;
import com.bulletphysics.collision.broadphase.CollisionFilterGroups;
import com.bulletphysics.collision.shapes.BvhTriangleMeshShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.ConvexHullShape;
import com.bulletphysics.collision.shapes.TriangleIndexVertexArray;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;

import com.bulletphysics.util.ObjectArrayList;
import org.engine.core.Time;
import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GameObject {
    public int id;
    private final AABB worldAABB = new AABB();
    protected Mesh mesh;
    protected Shader shader;
    protected Texture texture;
    protected Vector4f color;

    protected RigidBody rigidBody;
    protected String Name;
    protected final Transform transform;
    protected final Vector3f scale;
    protected float timedestroy=0.0f;
    protected boolean istimedestroy=false;

    public static final int RAYCAST_MASK = 1 << 15;
    protected float maxLinearSpeed = 30.0f;

    private boolean visible;

    // Хранение масок объекта
    protected int collisionFilterGroup = CollisionFilterGroups.DEFAULT_FILTER;
    protected int collisionFilterMask = CollisionFilterGroups.ALL_FILTER;

    public void SetCollisionMask(int group, int mask) {
        this.collisionFilterGroup = group;
        this.collisionFilterMask = mask;

        if (rigidBody != null && rigidBody.getBroadphaseHandle() != null) {
            BroadphaseProxy proxy = rigidBody.getBroadphaseHandle();
            proxy.collisionFilterGroup = (short) group;
            proxy.collisionFilterMask = (short) mask;
        }
    }


    private boolean occluder = false;

    public boolean IsOccluder() { return occluder; }
    public void SetOccluder(boolean occluder) { this.occluder = occluder; }

    public void SetCollisionMaskUpd() {


        if (rigidBody != null && rigidBody.getBroadphaseHandle() != null) {
            System.out.println("updssa");
            BroadphaseProxy proxy = rigidBody.getBroadphaseHandle();
            proxy.collisionFilterGroup = (short)   this.collisionFilterGroup;
            proxy.collisionFilterMask = (short)   this.collisionFilterMask;
        }
    }

    public void SetIgnoreRaycast(boolean ignore) {
        if (ignore) {
            // Убираем RAYCAST_MASK из маски, чтобы лучи проходили сквозь
            SetCollisionMask(this.collisionFilterGroup, this.collisionFilterMask & ~RAYCAST_MASK);
        } else {
            // Добавляем RAYCAST_MASK в маску, чтобы лучи снова попадали по объекту
            SetCollisionMask(this.collisionFilterGroup, this.collisionFilterMask | RAYCAST_MASK);
        }
    }

    public int getCollisionFilterGroup() {
        return collisionFilterGroup;
    }

    public int getCollisionFilterMask() {
        return collisionFilterMask;
    }

    public boolean IsVisible(){
        return visible;
    }

    public void SetVisible(boolean vsb){
        this.visible = vsb;
    }

    public void setMaxLinearSpeed(float maxSpeed) {
        this.maxLinearSpeed = maxSpeed;
    }

    public float getMaxLinearSpeed() {
        return maxLinearSpeed;
    }


    public GameObject(
            int _id,
            String name,
            Mesh mesh,
            Shader shader,
            Texture texture,
            Boolean createph,
            float mass,
            Vector3f startPosition,
            Vector3f startRotation,
            Vector3f startScale
    ) {
        this.id = _id;
        this.Name = name;
        this.mesh = mesh;
        this.shader = shader;
        this.texture = texture;
        this.color = new Vector4f(1,1,1,1);
        this.visible=true;
        this.transform = new Transform();
        this.transform.setIdentity();

        this.transform.origin.set(
                startPosition.x,
                startPosition.y,
                startPosition.z
        );

        Quaternionf jomlRot = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(startRotation.x),
                (float) Math.toRadians(startRotation.y),
                (float) Math.toRadians(startRotation.z)
        );

        javax.vecmath.Quat4f bulletRot =
                new javax.vecmath.Quat4f(
                        jomlRot.x,
                        jomlRot.y,
                        jomlRot.z,
                        jomlRot.w
                );

        this.transform.setRotation(bulletRot);

        this.scale = new Vector3f(startScale);

        if (createph) {
            this.rigidBody = createRigidBodyFromMesh(
                    mesh,
                    mass
            );
        } else {
            this.rigidBody = null;
        }
    }

    public void setColor(Vector4f color) {
        this.color = color;
    }

    public Vector4f getColor() {
        return color;
    }

    public AABB getWorldAABB() {
        if (mesh == null) {
            return null;
        }

        AABB local = mesh.getLocalAABB();
        Matrix4f modelMatrix = getModelMatrix();

        modelMatrix.transformAab(local.min, local.max, worldAABB.min, worldAABB.max);

        return worldAABB;
    }

    public String getName() {
        return Name;
    }

    /**
     * Создаёт физическое тело из меша.
     *
     * mass > 0:
     *     ConvexHullShape
     *
     * mass == 0:
     *     BvhTriangleMeshShape
     */
    private RigidBody createRigidBodyFromMesh(
            Mesh mesh,
            float mass
    ) {

        if (mesh == null) {
            return null;
        }

        CollisionShape shape;

        if (mass > 0.0f) {
            shape = createConvexShape(mesh);
        } else {
            shape = createStaticMeshShape(mesh);
        }

        // Масштаб объекта
        shape.setLocalScaling(
                new javax.vecmath.Vector3f(
                        scale.x,
                        scale.y,
                        scale.z
                )
        );

        // Позиция и вращение
        Transform startTransform = new Transform();
        startTransform.set(transform);

        // Для динамического тела рассчитываем инерцию
        javax.vecmath.Vector3f localInertia =
                new javax.vecmath.Vector3f(0, 0, 0);

        if (mass > 0.0f) {
            shape.calculateLocalInertia(
                    mass,
                    localInertia
            );
        }

        DefaultMotionState motionState =
                new DefaultMotionState(startTransform);

        RigidBodyConstructionInfo rbInfo =
                new RigidBodyConstructionInfo(
                        mass,
                        motionState,
                        shape,
                        localInertia
                );

        RigidBody body =
                new RigidBody(rbInfo);

        // Для динамических объектов можно включить CCD.
        if (mass > 0.0f) {
            body.setCcdMotionThreshold(1.0f);
            body.setCcdSweptSphereRadius(0.2f);
        }

        return body;
    }

    public void setTimerDestoroy(float time){
        this.timedestroy=time;
        this.istimedestroy=true;
    }

    /**
     * Выпуклая оболочка.
     *
     * Подходит для:
     * - ящиков
     * - камней
     * - машин
     * - предметов
     * - динамических объектов
     */
    private CollisionShape createConvexShape(Mesh mesh) {

        float[] vertices = mesh.getVertices();

        ObjectArrayList<javax.vecmath.Vector3f> points = new ObjectArrayList<>();

        for (int i = 0; i < vertices.length; i += 3) {

            points.add(
                    new javax.vecmath.Vector3f(
                            vertices[i],
                            vertices[i + 1],
                            vertices[i + 2]
                    )
            );
        }

        return new ConvexHullShape(points);
    }

    /**
     * Точная треугольная коллизия для статических объектов.
     *
     * Использовать для:
     * - пола
     * - стен
     * - уровня
     * - зданий
     * - статического окружения
     */
    private CollisionShape createStaticMeshShape(Mesh mesh) {

        float[] vertices = mesh.getVertices();
        int[] indices = mesh.getIndices();

        ByteBuffer vertexBuffer =
                ByteBuffer
                        .allocateDirect(vertices.length * Float.BYTES)
                        .order(ByteOrder.nativeOrder());

        for (float vertex : vertices) {
            vertexBuffer.putFloat(vertex);
        }

        vertexBuffer.flip();

        ByteBuffer indexBuffer =
                ByteBuffer
                        .allocateDirect(indices.length * Integer.BYTES)
                        .order(ByteOrder.nativeOrder());

        for (int index : indices) {
            indexBuffer.putInt(index);
        }

        indexBuffer.flip();

        TriangleIndexVertexArray meshInterface =
                new TriangleIndexVertexArray(
                        indices.length / 3,
                        indexBuffer,
                        3 * Integer.BYTES,
                        vertices.length / 3,
                        vertexBuffer,
                        3 * Float.BYTES
                );

        return new BvhTriangleMeshShape(
                meshInterface,
                true
        );
    }


    protected void clampLinearVelocity(float maxSpeed) {
        if (rigidBody == null || maxSpeed <= 0.0f) {
            return;
        }

        javax.vecmath.Vector3f velocity = new javax.vecmath.Vector3f();

        rigidBody.getLinearVelocity(velocity);

        float speedSquared = velocity.lengthSquared();
        float maxSpeedSquared = maxSpeed * maxSpeed;

        if (speedSquared > maxSpeedSquared) {
            float factor = maxSpeed / (float) Math.sqrt(speedSquared);

            velocity.scale(factor);

            rigidBody.setLinearVelocity(velocity);
            rigidBody.activate();
        }
    }



    public void update() {
        if (istimedestroy) {
            if (timedestroy > 0.0f) {
                timedestroy -= Time.getInstance().getDeltaSecond();
            } else {
                Scene.getInstance().removeGameObject(this);
                return;
            }
        }

        if (rigidBody == null) {
            return;
        }

        if (maxLinearSpeed > 0.0f) {
            clampLinearVelocity(maxLinearSpeed);
        }

        rigidBody.getWorldTransform(transform);
    }


    public Matrix4f getModelMatrix() {

        Matrix4f matrix = new Matrix4f();

        matrix.translate(
                transform.origin.x,
                transform.origin.y,
                transform.origin.z
        );

        javax.vecmath.Quat4f bulletRot =
                new javax.vecmath.Quat4f();

        transform.getRotation(bulletRot);

        matrix.rotate(
                new Quaternionf(
                        bulletRot.x,
                        bulletRot.y,
                        bulletRot.z,
                        bulletRot.w
                )
        );

        matrix.scale(scale);

        return matrix;
    }

    public Transform getTransform() {
        return transform;
    }

    public Vector3f getScale() {
        return scale;
    }

    public void setScale(Vector3f scale) {

        this.scale.set(scale);

        if (this.rigidBody != null &&
                this.rigidBody.getCollisionShape() != null) {

            this.rigidBody
                    .getCollisionShape()
                    .setLocalScaling(
                            new javax.vecmath.Vector3f(
                                    scale.x,
                                    scale.y,
                                    scale.z
                            )
                    );
        }
    }

    public javax.vecmath.Vector3f getPosition() {
        return transform.origin;
    }

    public Shader getShader() {
        return shader;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        this.texture = texture;
    }

    public RigidBody getRigidBody() {
        return rigidBody;
    }


    public void cleanup() {
        // 1. Очистка ресурсов рендеринга (OpenGL)
        // ВАЖНО: Если Mesh, Shader или Texture используются другими объектами
        // (общие ресурсы), то удалять их здесь нельзя!
        // Если они принадлежат только этому объекту, то удаляем:
      //  if (mesh != null) {
     //       mesh.cleanup(); // или mesh.dispose(), смотря как реализован ваш Mesh
       // }

        // 2. Очистка ресурсов физики (JBullet)
        // В JBullet (чистая Java) сборщик мусора уберет объекты сам,
        // но чтобы ускорить освобождение памяти, особенно от DirectByteBuffer,
        // лучше обнулить ссылки.
        if (rigidBody != null) {
            // Убираем ссылку на форму и motion state
            rigidBody.setMotionState(null);
            // Объекты JBullet не имеют метода dispose(),
            // они будут собраны GC, когда на них не останется ссылок.
            rigidBody = null;
        }
        shader=null;
        // Обнуляем остальные ссылки для помощи GC
        mesh = null;
        texture = null;
    }


    public void start() {
        SetCollisionMaskUpd();
    }
}