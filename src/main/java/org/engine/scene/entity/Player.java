package org.engine.scene.entity;

import com.bulletphysics.collision.broadphase.CollisionFilterGroups;
import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.collision.shapes.CapsuleShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import org.engine.audio.SoundBuffer;
import org.engine.audio.SoundSource;
import org.engine.core.GameHud;
import org.engine.core.MatchManager;
import org.engine.core.Time;
import org.engine.render.Camera;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.engine.scene.Scene;
import org.engine.utils.ModelLoader;
import org.engine.utils.ResourceCache;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Quat4f;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Player extends GameObject {

    public static class NetworkStatePlayer {
        public float posX, posY, posZ;
        public float yaw, pitch;
        public int health;
    }


    private HandGun guninhand;
    public int health = 100;
    private final Camera camera;

    // Настройки параметров игрока
    public float mouseSensitivity = 0.2f;
    private float moveSpeed = 15.0f; // Скорость ходьбы
    private float jumpForce = 15.0f; // Сила прыжка
    private float dropForce = 25.0f; // Сила прыжка
    private int ammo=0;

    // Углы поворота камеры
    private float yaw = 0;   // Поворот влево/вправо
    private float pitch = 0; // Поворот вверх/вниз
    private float roll = 0;  // Наклон (крен) камеры при стрейфе

    // Параметры наклона при стрейфе
    private float maxRollAngle = 8.0f;      // Максимальный угол наклона в градусах
    private float rollSmoothness = 10.0f;   // Скорость плавного возврата/наклона

    // Проверка нахождения на земле
    private boolean isOnGround = false;
    private float eyeHeight = 1.5f; // Высота глаз относительно центра капсулы
    Shader sceneShader = null;


    private float bobTime = 0f;
    private float bobAmountCurrent = 0f;      // текущая амплитуда (0..1)
    private static final float BOB_SPEED = 10f;
    private static final float BOB_AMOUNT = 0.5f;
    private static final float BOB_FADE_SPEED = 5f;

    private boolean ismoving = false;

    // ================= Параметры покачивания оружия (sway) =================
    // Собственное покачивание оружия в такт шагам ("восьмёрка")
    private static final float GUN_BOB_AMOUNT      = 0.07f;  // амплитуда покачивания оружия

    // Отход оружия при стрейфе (инерция — оружие отстаёт от движения)
    private static final float GUN_SWAY_STRAFE     = 0.10f;  // макс. боковое смещение при стрейфе
    private static final float GUN_SWAY_FORWARD    = 0.06f;  // смещение вперёд/назад при беге

    // Отставание оружия от поворота мыши
    private static final float GUN_SWAY_MOUSE      = 0.0012f;// сила смещения на единицу движения мыши
    private static final float GUN_SWAY_MOUSE_MAX  = 0.10f;  // предел смещения от мыши
    private static final float GUN_SWAY_ROLL_MOUSE = 0.03f;  // крен оружия от мыши (град/ед.)
    private static final float GUN_SWAY_ROLL_MAX   = 3.0f;   // предел крена (градусы)

    // Скорости сглаживания
    private static final float GUN_SWAY_SMOOTH     = 8f;     // плавность смещений
    private static final float MOUSE_ACCUM_DECAY   = 12f;    // затухание накопителя мыши

    // Текущее состояние sway
    private float gunSwayX = 0f;      // боковое смещение
    private float gunSwayY = 0f;      // вертикальное смещение
    private float gunSwayZ = 0f;      // смещение вперёд/назад
    private float gunSwayRoll = 0f;   // собственный крен оружия (градусы)
    private float mouseAccumX = 0f;   // накопитель движения мыши X
    private float mouseAccumY = 0f;   // накопитель движения мыши Y
    private float strafeInput = 0f;   // стрейф (-1..1)
    private float forwardInput = 0f;  // вперёд/назад (-1..1)

    public void setMouseSensitivity(float sent){
        this.mouseSensitivity = sent;
    }

    private List<SoundBuffer> steps;
    private List<SoundBuffer> death;
    private List<SoundBuffer> jumps;
    private SoundBuffer jumpdrop;
    private SoundBuffer respawnplayer;

    private SoundSource soundSource;

    private float stepTimer = 0f;
    private float stepCooldown = 0.45f;

    public void setAmmo(int ammo) {
        this.ammo = ammo;
        if( this.ammo<0) {this.ammo=0;}
    }

    public void MinusAmmo() {
        this.ammo--;
        if( this.ammo<0) {this.ammo=0;}
    }

    public int getAmmo() {
        return ammo;
    }

    public Player(int _id, Camera camera, Vector3f startPosition, Vector3f startRotation, float mass) {
        super( _id,"Player",null, null, null, false, mass, startPosition, startRotation, new Vector3f(1, 1, 1));
        this.camera = camera;

        float radius = 1.0f;
        float height = 3.0f;
        CapsuleShape capsuleShape = new CapsuleShape(radius, height);

        javax.vecmath.Vector3f localInertia = new javax.vecmath.Vector3f(0, 0, 0);
        if (mass > 0) {
            capsuleShape.calculateLocalInertia(mass, localInertia);
        }

        DefaultMotionState motionState = new DefaultMotionState(this.transform);
        RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(mass, motionState, capsuleShape, localInertia);
        this.rigidBody = new RigidBody(rbInfo);

        this.rigidBody.setAngularFactor(0f);
        this.rigidBody.setActivationState(RigidBody.DISABLE_DEACTIVATION);
        this.rigidBody.setSleepingThresholds(0.0f, 0.0f);

        this.rigidBody.setFriction(0.8f);
        this.rigidBody.setRestitution(0.0f);
        this.rigidBody.setDamping(0.5f, 0.5f);
        guninhand = new HandGun();
        guninhand.Add();
        Scene.getInstance().addGameObject(guninhand);

        // updateCameraTransform();



        sceneShader = ResourceCache.getShader("shader/vertex.glsl", "shader/fragment.glsl");

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

    public void PickUpWeapon( HandGun.TypeGun type){
        guninhand.switchWeapon(type);
        this.setAmmo(guninhand.getMaxAmmo());


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



    public NetworkStatePlayer getNetworkState() {
        NetworkStatePlayer state = new NetworkStatePlayer();
        state.posX = this.transform.origin.x;
        state.posY = this.transform.origin.y;
        state.posZ = this.transform.origin.z;
        state.yaw = this.yaw;
        state.pitch = this.pitch;
        state.health = this.health;
        return state;
    }

    public void DiePlauer(){

        if(soundSource!=null) {
            soundSource.playRnd(death, false);
        }

        Vector3f playerPos = new Vector3f(
                this.transform.origin.x,
                this.transform.origin.y-2,
                this.transform.origin.z
        );
        Vector3f spawnRotation = new Vector3f(0, yaw+ 180.0f, 0);


        List<GameObject> objects = ModelLoader.load(
                "model/skeleton.dae",
                sceneShader,
                2.0f, // 0 для статики или > 0 для динамики
                true,
                playerPos,
                spawnRotation,
                new Vector3f(1.0f, 1.0f, 1.0f),true
        );

        for (GameObject obj : objects) {
            // Добавляем в рендер
            obj.setTimerDestoroy(20.0f);
            Scene.getInstance().addGameObject(obj);

        }

    }

    public HandGun getWeaponsClass(){
        return guninhand;
    }

    public void SpawnAttack(Vector3f pos, Texture bull){

        List<GameObject> objects = ModelLoader.load(
                "model/bullet.dae",
                sceneShader,
                10.0f, // 0 для статики или > 0 для динамики
                true,
                pos,
                new Vector3f(0, 0, 0),
                new Vector3f(1.0f, 1.0f, 1.0f),true
        );


        for (GameObject obj : objects) {
            if(bull !=null){
                obj.setTexture(bull);
                obj.setColor(new Vector4f(1.4f,1.4f,1.4f,1.0f));
            }
            // Добавляем в рендер
            obj.SetIgnoreRaycast(true);
            obj.setTimerDestoroy(0.3f);
            Scene.getInstance().addGameObject(obj);

        }
    }

    public GameObject RaycastShot(float rayDistance){
        guninhand.shotstart();
        Transform camTransform = camera.getTransform();

        // 2. Извлекаем вращение камеры
        javax.vecmath.Quat4f camRot = new javax.vecmath.Quat4f();
        camTransform.getRotation(camRot);

        // 3. Вычисляем направление Forward (куда смотрит камера)
        Quaternionf jomlRot = new Quaternionf(camRot.x, camRot.y, camRot.z, camRot.w);
        Vector3f forwardJoml = new Vector3f(0, 0, -1).rotate(jomlRot);

        // 4. Точка начала луча (позиция камеры)
        javax.vecmath.Vector3f rayStart = new javax.vecmath.Vector3f(
                camTransform.origin.x,
                camTransform.origin.y,
                camTransform.origin.z
        );

        // 5. Точка конца луча (позиция камеры + направление * дистанция)

        javax.vecmath.Vector3f rayEnd = new javax.vecmath.Vector3f(
                rayStart.x + forwardJoml.x * rayDistance,
                rayStart.y + forwardJoml.y * rayDistance,
                rayStart.z + forwardJoml.z * rayDistance
        );

        // 6. Создаем коллбэк для поиска ближайшего столкновения
        CollisionWorld.ClosestRayResultCallback rayCallback = new CollisionWorld.ClosestRayResultCallback(rayStart, rayEnd);
        rayCallback.collisionFilterGroup = (short) GameObject.RAYCAST_MASK;
        rayCallback.collisionFilterMask = CollisionFilterGroups.ALL_FILTER;
        // 7. Пускаем луч по физическому миру
        Scene.getInstance().GetPhysicsWorld().dynamicsWorld.rayTest(rayStart, rayEnd, rayCallback);

        // 8. Если луч во что-то врезался
        if (rayCallback.hasHit()) {

            // Получаем точку столкновения
            javax.vecmath.Vector3f hitPoint = rayCallback.hitPointWorld;
            RigidBody hitRigidBody = (RigidBody) rayCallback.collisionObject;


            GameObject hitObject = Scene.getInstance().getGameObjectByRigidBody(hitRigidBody);
            float spawnOffset = 0.2f;

            if(hitRigidBody!= null) {
                NetPlayer hitNetPlayer = Scene.getInstance().getNetPlayerByRigidBody(hitRigidBody);

                if (hitNetPlayer != null) {
                    hitNetPlayer.setDamage(5);
                }
            }

            SpawnAttack(
                    new Vector3f(hitPoint.x - forwardJoml.x * spawnOffset
                            , hitPoint.y - forwardJoml.y * spawnOffset
                            , hitPoint.z - forwardJoml.z * spawnOffset), hitObject.getTexture());

            return  hitObject;
        }
        return  null;
    }


    public void setHealth(int health) {
        this.health = health;
    }

    public int getHealth() {
        return health;
    }

    public void Destroy(){
        health=0;
        guninhand.Remove();
        Scene.getInstance().removeGameObject(guninhand);
        Scene.getInstance().removeGameObject(this);
    }

    public float GetMouseSensitivity() {
        return mouseSensitivity;
    }

    public void processMouseInput(float dx, float dy) {
        yaw += dx * mouseSensitivity;
        pitch -= dy * mouseSensitivity;
        pitch = Math.max(-89.0f, Math.min(89.0f, pitch));

        // Накапливаем движение мыши для sway оружия
        mouseAccumX += dx;
        mouseAccumY += dy;
    }

    public void processKeyboardInput(Vector3f moveDir, boolean jump, boolean drop) {
        if (this.rigidBody == null) return;

        // Направление ввода для sway оружия (нормализуем, чтобы по диагонали не усиливалось)
        float inputLen = moveDir.length();
        if (inputLen > 0.001f) {
            strafeInput = moveDir.x / inputLen;
            forwardInput = moveDir.z / inputLen;
        } else {
            strafeInput = 0f;
            forwardInput = 0f;
        }

        ismoving=(inputLen > 0.5f);

        isOnGround = checkOnGround();

        float deltaTime = Time.getInstance().getDeltaSecond();

        if (stepTimer > 0) {
            stepTimer -= deltaTime;
        }

        // Если игрок на земле и движется
        if (isOnGround && ismoving) {
            // Если таймер закончился, играем звук и сбрасываем таймер
            if (stepTimer <= 0 && soundSource != null) {
                soundSource.playRnd(steps, false);
                stepTimer = stepCooldown;
            }
        } else {
            // Если игрок остановился или прыгнул, сбрасываем таймер в 0.
            // Это сделает следующий шаг моментальным, как только игрок снова коснется земли и пойдет.
            stepTimer = 0;
        }
        // Определяем целевой угол наклона в зависимости от стрейфа
        float targetRoll = 0.0f;

        if (moveDir.lengthSquared() > 0) {
            // Нормализуем для корректного определения направления
            Vector3f normalizedDir = new Vector3f(moveDir).normalize();

            // moveDir.x: -1 = влево (A), +1 = вправо (D)
            // При стрейфе вправо наклоняем камеру вправо (отрицательный roll)
            // При стрейфе влево наклоняем камеру влево (положительный roll)
            targetRoll = -normalizedDir.x * maxRollAngle;

            moveDir.normalize();

            float yawRad = (float) Math.toRadians(yaw);
            float sinYaw = (float) Math.sin(yawRad);
            float cosYaw = (float) Math.cos(yawRad);

            float forwardX = -sinYaw;
            float forwardZ = -cosYaw;

            float rightX = cosYaw;
            float rightZ = -sinYaw;

            float velX = (rightX * moveDir.x + forwardX * moveDir.z) * moveSpeed;
            float velZ = (rightZ * moveDir.x + forwardZ * moveDir.z) * moveSpeed;

            //if (isOnGround) {
            javax.vecmath.Vector3f currentVel = new javax.vecmath.Vector3f();
            this.rigidBody.getLinearVelocity(currentVel);

            this.rigidBody.setLinearVelocity(new javax.vecmath.Vector3f(velX, currentVel.y, velZ));

        }

        // Плавно интерполируем текущий roll к целевому значению
        // Это создаёт эффект плавного наклона и возврата
        roll += (targetRoll - roll) * Math.min(1.0f, rollSmoothness * deltaTime);
        // 0.016f — приблизительный deltaTime для 60 FPS

        javax.vecmath.Vector3f currentVel = new javax.vecmath.Vector3f();
        this.rigidBody.getLinearVelocity(currentVel);
        if (jump && isOnGround) {
            this.rigidBody.setLinearVelocity(new javax.vecmath.Vector3f(currentVel.x, currentVel.y + jumpForce, currentVel.z));
            isOnGround = false;
            if (soundSource!=null){
                soundSource.playRnd(jumps, false);
            }
        }else if (drop) {
            if(checkOnGroundDist()>10.0f) {
                this.rigidBody.setLinearVelocity(new javax.vecmath.Vector3f(currentVel.x, currentVel.y - dropForce, currentVel.z));
            }
        }
    }

    public void DropAudioPlay(){
        if (soundSource!=null){
            soundSource.play(jumpdrop, false);
        }
    }

    public boolean IsDropDamage(){
        if(this.rigidBody==null) { return false;}
        javax.vecmath.Vector3f currentVel = new javax.vecmath.Vector3f();
        this.rigidBody.getLinearVelocity(currentVel);
        return (isOnGround && currentVel.length() > 19.0f);

    }

    private float checkOnGroundDist() {
        javax.vecmath.Vector3f from = new javax.vecmath.Vector3f(transform.origin.x, transform.origin.y, transform.origin.z);
        javax.vecmath.Vector3f to = new javax.vecmath.Vector3f(transform.origin.x, transform.origin.y - 200.0f, transform.origin.z);

        CollisionWorld.ClosestRayResultCallback callback = new CollisionWorld.ClosestRayResultCallback(from, to);
        callback.collisionFilterGroup = (short) GameObject.RAYCAST_MASK;
        callback.collisionFilterMask = CollisionFilterGroups.ALL_FILTER;
        Scene.getInstance().GetPhysicsWorld().dynamicsWorld.rayTest(from, to, callback);

        if (callback.hasHit()) {
            return from.y - callback.hitPointWorld.y;

        }
        return -1.0f; // нет столкновения
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

        float deltaTime = Time.getInstance().getDeltaSecond();
        // 1. Сначала обновляем позицию и вращение самой камеры
        updateCameraTransform(deltaTime, isOnGround && ismoving);

        if(health<=0){
            health=0;
            //MatchManager.getInstance().SendDeath();

            DiePlauer();
            guninhand.Remove();
            Scene.getInstance().removeGameObject(guninhand);
            Scene.getInstance().removeGameObject(this);
            return; // Выходим, чтобы не обновлять пистолет для мертвого игрока
        }


        javax.vecmath.Vector3f camPos = camera.getPosition();

        if(guninhand != null){
            if(ammo<=0 && guninhand.getActiveWeapon()!=HandGun.TypeGun.HandGuy){
                guninhand.switchWeapon(HandGun.TypeGun.HandGuy);
            }

            // 1. Обновляем sway оружия (плавные смещения)
            updateGunSway(deltaTime);

            // 2. Трансформ камеры (позиция уже с bob, вращение — yaw/pitch/roll)
            Transform camTransform = camera.getTransform();

            // 3. Базовое смещение пистолета в ЛОКАЛЬНЫХ координатах КАМЕРЫ
            // X = 1.0f (чуть правее центра экрана)
            // Y = -1.0f (чуть ниже центра, на уровне рук)
            // Z = -1.7f (впереди камеры, так как вперед это -Z)
            javax.vecmath.Vector3f localOffset = new javax.vecmath.Vector3f(1.0f, -1.0f, -1.7f);

            // 3a. Покачивание оружия в такт камере ("восьмёрка"):
            // камера качается по вертикали (sin), оружие добавляет движение по X и провисание по Y
            localOffset.x += (float) Math.sin(bobTime) * GUN_BOB_AMOUNT * bobAmountCurrent;
            localOffset.y -= (float) Math.abs(Math.cos(bobTime)) * GUN_BOB_AMOUNT * bobAmountCurrent;

            // 3b. Отход в сторону при стрейфе и поворотах мыши
            localOffset.x += gunSwayX;
            localOffset.y += gunSwayY;

            // 3c. Отход вперёд/назад при движении (инерция)
            localOffset.z += gunSwayZ;

            // 4. Поворачиваем локальное смещение матрицей вращения КАМЕРЫ -> мировые координаты
            javax.vecmath.Vector3f worldOffset = new javax.vecmath.Vector3f();
            camTransform.basis.transform(localOffset, worldOffset);

            // 5. Вычисляем финальную позицию пистолета в мире
            javax.vecmath.Vector3f gunPos = new javax.vecmath.Vector3f(camPos);
            gunPos.add(worldOffset);

            guninhand.getTransform().origin.set(gunPos);

            // 6. Вращение камеры + собственный крен оружия от мыши
            javax.vecmath.Quat4f camRot = new javax.vecmath.Quat4f();
            camTransform.getRotation(camRot);

            // Лёгкий локальный крен оружия поверх вращения камеры
            Quat4f swayRollQuat = new Quat4f();
            swayRollQuat.set(new AxisAngle4f(0, 0, 1, (float) Math.toRadians(gunSwayRoll)));
            camRot.mul(swayRollQuat);

            guninhand.getTransform().setRotation(camRot);
            guninhand.update();
        }
        if(soundSource!=null) {
            soundSource.setPosition(camPos);
        }
    }

    /**
     * Обновляет плавные смещения оружия (sway):
     * - отставание при стрейфе и движении вперёд/назад;
     * - отставание от поворотов мыши;
     * - собственный крен оружия.
     * Вызывается из update() перед расчётом позиции оружия.
     */
    private void updateGunSway(float deltaTime) {
        // 1. Накопители мыши плавно затухают к нулю
        float decay = (float) Math.exp(-MOUSE_ACCUM_DECAY * deltaTime);
        mouseAccumX *= decay;
        mouseAccumY *= decay;

        // Резкий рывок мыши не должен уводить оружие далеко
        mouseAccumX = clamp(mouseAccumX, -80f, 80f);
        mouseAccumY = clamp(mouseAccumY, -80f, 80f);

        // 2. Вклад мыши в смещение (с ограничением)
        // Мышь вправо (dx > 0) -> оружие отстаёт влево
        float mouseSwayX = clamp(-mouseAccumX * GUN_SWAY_MOUSE, -GUN_SWAY_MOUSE_MAX, GUN_SWAY_MOUSE_MAX);
        // Мышь вверх (dy < 0) -> оружие отстаёт вниз
        float mouseSwayY = clamp( mouseAccumY * GUN_SWAY_MOUSE, -GUN_SWAY_MOUSE_MAX, GUN_SWAY_MOUSE_MAX);

        // 3. Целевые смещения
        // Стрейф вправо (strafeInput > 0) -> оружие отстаёт влево (инерция).
        // Хочешь наоборот (в сторону движения) — убери минус.
        float targetX = -strafeInput * GUN_SWAY_STRAFE + mouseSwayX;
        float targetY = mouseSwayY;
        // Бежать вперёд (forwardInput > 0) -> оружие отстаёт назад (+Z в локальных координатах камеры)
        float targetZ = forwardInput * GUN_SWAY_FORWARD;

        // 4. Плавная интерполяция к целевым смещениям
        float t = Math.min(1f, GUN_SWAY_SMOOTH * deltaTime);
        gunSwayX += (targetX - gunSwayX) * t;
        gunSwayY += (targetY - gunSwayY) * t;
        gunSwayZ += (targetZ - gunSwayZ) * t;

        // 5. Лёгкий крен оружия от поворота мыши (в ту же сторону, что и камера при стрейфе)
        float targetRoll = clamp(-mouseAccumX * GUN_SWAY_ROLL_MOUSE, -GUN_SWAY_ROLL_MAX, GUN_SWAY_ROLL_MAX);
        gunSwayRoll += (targetRoll - gunSwayRoll) * t;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private void updateCameraTransform(float deltaTime, boolean isMoving) {
        javax.vecmath.Vector3f pos = this.transform.origin;

        // Фаза крутится всегда — нет скачков
        bobTime += deltaTime * BOB_SPEED;

        // Целевая амплитуда: 1 если идём, 0 если стоим
        float targetAmount = isMoving ? 1f : 0f;

        // Плавно приближаем текущую амплитуду к целевой
        float t = Math.min(1f, deltaTime * BOB_FADE_SPEED);
        bobAmountCurrent += (targetAmount - bobAmountCurrent) * t;

        float bobOffset = (float) Math.sin(bobTime) * BOB_AMOUNT * bobAmountCurrent;

        camera.getPosition().set(pos.x, pos.y + eyeHeight + bobOffset, pos.z);

        // Поворот камеры: Yaw (вокруг Y) * Pitch (вокруг X) * Roll (вокруг Z)
        Quat4f yawQuat = new Quat4f();
        yawQuat.set(new AxisAngle4f(0, 1, 0, (float) Math.toRadians(yaw)));

        Quat4f pitchQuat = new Quat4f();
        pitchQuat.set(new AxisAngle4f(1, 0, 0, (float) Math.toRadians(pitch)));

        // Кватернион наклона (крена) вокруг оси Z
        Quat4f rollQuat = new Quat4f();
        rollQuat.set(new AxisAngle4f(0, 0, 1, (float) Math.toRadians(roll)));

        // Объединяем: сначала Yaw, затем Pitch, затем Roll
        Quat4f yawPitch = new Quat4f();
        yawPitch.mul(yawQuat, pitchQuat);

        Quat4f totalRot = new Quat4f();
        totalRot.mul(yawPitch, rollQuat);

        camera.getTransform().setRotation(totalRot);
    }
    public Camera getCamera() {
        return camera;
    }
}