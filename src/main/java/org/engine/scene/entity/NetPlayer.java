package org.engine.scene.entity;

import com.bulletphysics.collision.broadphase.CollisionFilterGroups;
import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.collision.shapes.CapsuleShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import org.engine.audio.SoundBuffer;
import org.engine.audio.SoundSource;
import org.engine.core.Time;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.engine.scene.Scene;
import org.engine.utils.ModelLoader;
import org.engine.utils.ResourceCache;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Quat4f;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NetPlayer extends GameObject {

    private float teleportDistance = 5.0f;
    private float lerpSpeed = 12.0f;
    private final Vector4f targetColor = new Vector4f(1, 1, 1, 1);
    private HandGun guninhand;
    private List<GameObject> animMesh;
    private Text3DObject TextName;
    private HealthBar3D healthBar;
    public int health = 100;

    // Углы поворота (полученные из сети)
    public float yaw = 0;

    // Целевые значения для интерполяции
    private final Vector3f targetPosition;
    private float targetYaw;

    private List<SoundBuffer> steps;
    private List<SoundBuffer> death;
    private List<SoundBuffer> jumps;
    private SoundBuffer jumpdrop;
    private SoundBuffer respawnplayer;

    private SoundSource soundSource;

    private float stepTimer = 0f;
    private float stepCooldown = 0.45f;

    private  Shader sceneShader;

    // Флаг для отслеживания прыжка
    private boolean wasJumping = false;

    public void setDamage(int dmg){
        this.color.set(1,0,0,1);
        // this.health -= dmg;

        for (GameObject obj : animMesh) {
            if (obj != null) {
                obj.setColor(new Vector4f(1,0,0,1));
            }
        }

    }

    public NetPlayer(int _id,int model, String name, Vector3f startPosition, Vector3f startRotation, float mass) {
        List<String> modelpack = new ArrayList<>();
        modelpack.add("model/kinger.dae");
        modelpack.add("model/ghost.dae");
        modelpack.add("model/bishop.dae");
        modelpack.add("model/dicer.dae");
        modelpack.add("model/karen.dae");
        modelpack.add("model/crow.dae");
        modelpack.add("model/mettaton.dae");
        modelpack.add("model/kerfus.dae");

        Texture bloodTexture = new Texture("texture/blood.png");
        // Передаем false в createph, так как мы создадим кастомное кинематическое тело
        super(_id, name, null, null, bloodTexture, false, 0.0f, startPosition, startRotation, new Vector3f(1, 1, 1));

        this.targetPosition = new Vector3f(startPosition);
        this.targetYaw = startRotation.y;
        this.yaw = startRotation.y;

        // --- НАСТРОЙКА КИНЕМАТИЧЕСКОГО ТЕЛА ---
        float radius = 1.0f;
        float height = 3.0f;
        CapsuleShape capsuleShape = new CapsuleShape(radius, height);

        // Для кинематического тела инерция не важна, масса = 0
        javax.vecmath.Vector3f localInertia = new javax.vecmath.Vector3f(0, 0, 0);
        DefaultMotionState motionState = new DefaultMotionState(this.transform);
        RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(0, motionState, capsuleShape, localInertia);

        this.rigidBody = new RigidBody(rbInfo);

        // Настраиваем флаги: делаем его кинематическим
        int flags = this.rigidBody.getCollisionFlags();
        flags &= ~CollisionFlags.STATIC_OBJECT; // Снимаем флаг статики
        flags |= CollisionFlags.KINEMATIC_OBJECT; // Добавляем кинематический флаг
        //this.rigidBody.setCollisionFlags(flags);

        // Отключаем деактивацию, чтобы тело всегда было активным для коллизий
        this.rigidBody.setActivationState(RigidBody.DISABLE_DEACTIVATION);
        this.rigidBody.setFriction(0.8f);
        this.rigidBody.setRestitution(0.0f);

        // --- ВИЗУАЛЬНЫЕ ЭЛЕМЕНТЫ ---
        guninhand = new HandGun();
        guninhand.Add();


        Shader textShader = ResourceCache.getShader("shader/text_vertex.glsl", "shader/text_fragment.glsl");
        Texture fontTexture = ResourceCache.getTexture("texture/font.png");
        Shader halfdShader = ResourceCache.getShader("shader/health_bar_vertex.glsl", "shader/health_bar_fragment.glsl");

        TextName = new Text3DObject(
                name,
                name,
                textShader,
                fontTexture,
                true,
                new Vector3f(startPosition.x, startPosition.y + 4.0f, startPosition.z)
        );
        int txtleng = name.length();
        if(txtleng<1){
            txtleng=1;
        }
        float sizet =4/(float)txtleng;
        TextName.setScale(new Vector3f( sizet, sizet, sizet));
        healthBar = new HealthBar3D(100.0f, halfdShader);
        healthBar.setScale(new Vector3f(4, 0.4f, 1));

        Scene.getInstance().addGameObject(TextName);
        Scene.getInstance().addGameObject(healthBar);

        sceneShader = ResourceCache.getShader("shader/vertex.glsl", "shader/fragment.glsl");

        String path ="kinger.dae";
        if(modelpack.size()>model) {
            path = modelpack.get(model);
        }

        animMesh = ModelLoader.load(
                path,
                sceneShader,
                0.0f,
                false,
                new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 0),
                new Vector3f(3, 3, 3)
        );

        Scene.getInstance().addListGameObject(animMesh);
        Scene.getInstance().addGameObject(guninhand);


        steps = new ArrayList<>();
        steps.add(ResourceCache.getSound("sound/player/dirt1.ogg"));
        steps.add(ResourceCache.getSound("sound/player/dirt2.ogg"));
        steps.add(ResourceCache.getSound("sound/player/dirt3.ogg"));
        steps.add(ResourceCache.getSound("sound/player/dirt4.ogg"));

        death = new ArrayList<>();
        death.add(ResourceCache.getSound("sound/player/death01.ogg"));
        death.add(ResourceCache.getSound("sound/player/death02.ogg"));
        death.add(ResourceCache.getSound("sound/player/death03.ogg"));
        death.add(ResourceCache.getSound("sound/player/death04.ogg"));
        death.add(ResourceCache.getSound("sound/player/death05.ogg"));
        death.add(ResourceCache.getSound("sound/player/death06.ogg"));

        jumps= new ArrayList<>();
        jumps.add(ResourceCache.getSound("sound/player/jump01.ogg"));
        jumps.add(ResourceCache.getSound("sound/player/jump02.ogg"));
        jumps.add(ResourceCache.getSound("sound/player/jump03.ogg"));
        jumps.add(ResourceCache.getSound("sound/player/jump04.ogg"));

        respawnplayer = ResourceCache.getSound("sound/player/respawn.ogg");
        jumpdrop = ResourceCache.getSound("sound/player/drop.ogg");

        soundSource = new SoundSource();

        soundSource.play(respawnplayer, false);

    }

    /**
     * Этот метод вызывается MatchManager при получении пакета данных от сервера.
     */
    public void applyNetworkState(float posX, float posY, float posZ, float yaw, int health,int guntype) {
        // Обновляем целевые координаты, к которым будет стремиться NetPlayer
        this.targetPosition.set(posX, posY, posZ);
        this.targetYaw = yaw;

        this.health = health;

        if(guninhand.getCurrentWeaponIndex()!=guntype){
            guninhand.switchWeaponByIndex(guntype);
        }

    }

    public int getCurrentWeaponIndex() {
        return guninhand.getCurrentWeaponIndex();
    }

    /**
     * Возвращает тип оружия по его порядковому номеру.
     * @param index индекс оружия (0 - HandGuy, 1 - RevolverGun, 2 - ShotGun, 3 - AkaGun)
     * @return объект TypeGun или null, если индекс выходит за пределы
     */
    public HandGun.TypeGun getWeaponByIndex(int index) {
        return guninhand.getWeaponByIndex(index);
    }

    /**
     * Переключает оружие по его порядковому номеру.
     * @param index индекс оружия
     */
    public void switchWeaponByIndex(int index) {
        guninhand.switchWeaponByIndex(index);
    }

    public void AttackDrop() {
        if(soundSource!=null) {
            soundSource.play(jumpdrop, false);
        }
    }

    public void AttackAnim() {
        guninhand.shotstart();
    }

    public void Destroy(){
        health = 0;
        guninhand.Remove();
        Scene.getInstance().removeGameObject(guninhand);
        Scene.getInstance().removeListGameObject(animMesh);
        animMesh.clear();
        Scene.getInstance().removeGameObject(healthBar);
        Scene.getInstance().removeGameObject(TextName);
        Scene.getInstance().removeGameObject(this);
    }

    private boolean checkOnGround() {
        javax.vecmath.Vector3f from = new javax.vecmath.Vector3f(transform.origin.x, transform.origin.y, transform.origin.z);
        javax.vecmath.Vector3f to = new javax.vecmath.Vector3f(transform.origin.x, transform.origin.y - 3.0f, transform.origin.z);

        CollisionWorld.ClosestRayResultCallback callback = new CollisionWorld.ClosestRayResultCallback(from, to);
        callback.collisionFilterGroup = (short) GameObject.RAYCAST_MASK;
        callback.collisionFilterMask = CollisionFilterGroups.ALL_FILTER;
        Scene.getInstance().GetPhysicsWorld().dynamicsWorld.rayTest(from, to, callback);

        return callback.hasHit();
    }

    @Override
    public void update() {
        super.update();

        // Проверка смерти (если сервер прислал здоровье <= 0)
        if (health <= 0) {
            health = 0;
            DiePlauer();
            guninhand.Remove();
            Scene.getInstance().removeGameObject(guninhand);
            Scene.getInstance().removeListGameObject(animMesh);
            animMesh.clear();
            Scene.getInstance().removeGameObject(healthBar);
            Scene.getInstance().removeGameObject(TextName);
            Scene.getInstance().removeGameObject(this);
            return;
        }

        // --- ИНТЕРПОЛЯЦИЯ ---
        float deltaTime = Time.getInstance().getDeltaSecond();
        boolean isOnGround = checkOnGround();

        // --- ЛОГИКА ЗАДЕРЖКИ ШАГОВ ---
        if (stepTimer > 0) {
            stepTimer -= deltaTime;
        }

        // Вычисляем вектор разницы между целью и текущей позицией
        float dx = targetPosition.x - this.transform.origin.x;
        float dy = targetPosition.y - this.transform.origin.y;
        float dz = targetPosition.z - this.transform.origin.z;

        // Вычисляем квадрат расстояния (быстрее, чем Math.sqrt)
        float distanceSquared = dx * dx + dy * dy + dz * dz;

        // Считаем горизонтальное расстояние (X и Z), чтобы шаги не играли при падении с высоты
        float distSqXZ = (dx * dx) + (dz * dz);
        boolean isMoving = distSqXZ > 0.01f; // 0.1f ^ 2 - порог движения

        // --- ЛОГИКА ПРЫЖКА ---
        // Если игрок на земле, но серверная позиция начала резко уходить вверх
        if (isOnGround && dy > 0.5f) {
            if (!wasJumping && soundSource != null) {
                soundSource.playRnd(jumps, false);
            }
            wasJumping = true;
        }
        // Сбрасываем флаг прыжка, когда игрок начал падать (опустился ниже цели или цель пошла вниз)
        else if (dy <= 0.1f) {
            wasJumping = false;
        }

        // --- ЛОГИКА ШАГОВ ---
        // Если игрок на земле и смещается по горизонтали (и не в прыжке)
        if (isOnGround && isMoving) {
            if (stepTimer <= 0 && soundSource != null) {
                soundSource.playRnd(steps, false);
                stepTimer = stepCooldown;
            }
        } else {
            // Если остановился или прыгнул - сбрасываем таймер для моментального следующего шага
            stepTimer = 0;
        }
        // -----------------------------

        // Ограничиваем lerpAmount, чтобы при больших лагах игрок не пролетел сквозь цель
        float lerpAmount = Math.min(1.0f, lerpSpeed * deltaTime);
        float t = Math.min(1.0f, 3.0f * deltaTime);

        // Если расстояние слишком большое (например, игрок попал в портале или сервер исправил лаг)
        if (distanceSquared > teleportDistance * teleportDistance) {
            // Мгновенный перенос
            this.transform.origin.set(targetPosition.x, targetPosition.y, targetPosition.z);
            this.yaw = targetYaw; // Заодно мгновенно поворачиваем
        } else {
            // Плавная интерполяция (Lerp)
            this.transform.origin.x += dx * lerpAmount;
            this.transform.origin.y += dy * lerpAmount;
            this.transform.origin.z += dz * lerpAmount;

            // Плавный поворот (Yaw). Учитываем переход через 360 / -360 градусов
            float deltaYaw = targetYaw - this.yaw;
            while (deltaYaw > 180) deltaYaw -= 360;
            while (deltaYaw < -180) deltaYaw += 360;
            this.yaw += deltaYaw * lerpAmount;
        }

        // Применяем вращение к трансформации (поворачиваем капсулу/модель)
        Quat4f yawQuat = new Quat4f();
        yawQuat.set(new AxisAngle4f(0, 1, 0, (float) Math.toRadians(this.yaw)));
        this.transform.setRotation(yawQuat);

        // ВАЖНО: Для кинематических тел в JBullet необходимо вручную обновлять мир трансформации
        if (this.rigidBody != null) {
            this.rigidBody.setCenterOfMassTransform(this.transform);
        }

        // --- ОБНОВЛЕНИЕ ПРИВЯЗАННЫХ ОБЪЕКТОВ ---
        javax.vecmath.Vector3f myPos = this.getTransform().origin;
        javax.vecmath.Quat4f myRot = new javax.vecmath.Quat4f();
        this.getTransform().getRotation(myRot);

        if (healthBar != null) {
            healthBar.setHealth(health);
            healthBar.getTransform().origin.set(myPos.x, myPos.y + 3.5f, myPos.z);
        }

        if (TextName != null) {
            TextName.getTransform().origin.set(myPos.x, myPos.y + 4.0f, myPos.z);
        }

        if (guninhand != null) {
            guninhand.getTransform().setRotation(myRot);

            Vector3f position = new Vector3f();
            javax.vecmath.Vector3f bulletPos = this.transform.origin;
            position.set(bulletPos.x, bulletPos.y, bulletPos.z);

            Vector3f forward = new Vector3f();
            javax.vecmath.Matrix3f basis = transform.basis;
            forward.set(-basis.m02, -basis.m12, -basis.m22); // Направление "вперед"

            Vector3f target = new Vector3f();
            target.set(position).add(forward);
            guninhand.getTransform().origin.set(target.x, target.y, target.z);
        }

        for (GameObject obj : animMesh) {
            if (obj != null) {
                obj.getTransform().setRotation(myRot);
                obj.getTransform().origin.set(myPos.x, myPos.y - 2.5f, myPos.z);
                obj.setColor(obj.getColor().lerp(targetColor, t));
            }
        }

        // Обновляем позицию источника звука, чтобы он следовал за игроком
        if (soundSource != null) {
            soundSource.setPosition(myPos.x, myPos.y, myPos.z);
        }
    }

    // Метод смерти, аналогичный Player
    public void DiePlauer() {

        if(soundSource!=null) {
            soundSource.playRnd(death, false);
        }
        Vector3f playerPos = new Vector3f(
                this.transform.origin.x,
                this.transform.origin.y - 2,
                this.transform.origin.z
        );
        Vector3f spawnRotation = new Vector3f(0, yaw + 180.0f, 0);




        List<GameObject> objects = ModelLoader.load(
                "model/skeleton.dae", sceneShader, 2.0f, true, playerPos, spawnRotation, new Vector3f(1, 1, 1)
        );
        for (GameObject obj : objects) {
            obj.setTimerDestoroy(20.0f);
            Scene.getInstance().addGameObject(obj);
        }

    }

    public int getHealth() {
        return health;
    }
}