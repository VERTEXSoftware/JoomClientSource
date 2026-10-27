package org.engine.scene.entity;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import org.engine.audio.SoundBuffer;
import org.engine.audio.SoundSource;
import org.engine.core.MatchManager;
import org.engine.core.Time;
import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.engine.scene.Scene;
import org.engine.utils.ModelLoader;
import org.engine.utils.ResourceCache;
import org.joml.Vector3f;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Quat4f;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WeaponPickupTrigger extends Trigger {
    float speed = 2f;
    private Quat4f currentVisualRotation = new Quat4f(0, 0, 0, 1);
    private List<GameObject> visualizecrate;
    private Vector3f posviewbox;

    // Переменные для таймера задержки
    private float TimeDelay = 0.0f;
    private boolean IsPick = true;
    private static final float COOLDOWN_MS = 60;
    float bobSpeed  = 3f;
    float bobHeight = 1.0f;
    float bobTime = 0f;
    Shader sceneShader;


    private List<SoundBuffer> createdrop;
    private SoundSource soundSource;

    public WeaponPickupTrigger(String name, Mesh mesh, Vector3f startPosition, Vector3f startview, Vector3f startRotation, Vector3f startScale) {
        // Передаем в базовый конструктор тип WeaponsPickUp

        super(name, mesh, startPosition, startRotation, startScale, TypeTrigger.WeaponsPickUp);
        posviewbox = new Vector3f(0,0,0);
        posviewbox.set(startview);

        createdrop = new ArrayList<>();
        createdrop.add(ResourceCache.getSound("sound/prop/wood_crate_break1.ogg"));
        createdrop.add(ResourceCache.getSound("sound/prop/wood_crate_break2.ogg"));
        createdrop.add(ResourceCache.getSound("sound/prop/wood_crate_break3.ogg"));

        soundSource = new SoundSource();

            sceneShader = ResourceCache.getShader("shader/vertex.glsl", "shader/fragment.glsl");
            visualizecrate = ModelLoader.load(
                    "model/pickup.dae",
                    sceneShader,
                    0.0f,
                    false,
                    startview,
                    new Vector3f(0, 0, 0),
                    new Vector3f(2f, 2f, 2f),true);

            List<GameObject> objects = ModelLoader.load(
                "model/pickupdrop.dae",
                sceneShader,
                2.0f,
                true,
                posviewbox,
                new Vector3f(0, 0, 0),
                new Vector3f(2f, 2f, 2f),true);

        objects.clear();

        Scene.getInstance().addListGameObject(visualizecrate);



    }
    public void DieCube() {


        // Берём текущее вращение визуального куба и конвертируем в эйлеровы углы
        Vector3f spawnRotation = quatToEuler(currentVisualRotation);

        if(soundSource!=null) {
            soundSource.setPosition(posviewbox);
            soundSource.playRnd(createdrop, false);
        }

        List<GameObject> objects = ModelLoader.load(
                "model/pickupdrop.dae",
                sceneShader,
                2.0f,
                true,
                posviewbox,
                spawnRotation,
                new Vector3f(2f, 2f, 2f),true);

        for (GameObject obj : objects) {
            obj.setTimerDestoroy(10.0f);
            Scene.getInstance().addGameObject(obj);
        }
    }

    // Хелпер: кватернион → углы Эйлера (YXZ или XYZ — подбери под свой движок)
    private Vector3f quatToEuler(Quat4f q) {
        org.joml.Quaternionf jq = new org.joml.Quaternionf(q.x, q.y, q.z, q.w);
        org.joml.Vector3f euler = new org.joml.Vector3f();
        jq.getEulerAnglesYXZ(euler); // или getEulerAnglesXYZ — в зависимости от конвенции движка
        return new Vector3f(euler.x, euler.y, euler.z);
    }

    @Override
    public void update() {
        super.update();

        float deltaTime = Time.getInstance().getDeltaSecond();

        if(TimeDelay>0){
            TimeDelay-=deltaTime*10.0f;
            IsPick=false;
        }else{
            TimeDelay=0.0f;
            IsPick=true;
        }


        float anglePerFrame = speed * deltaTime;
        bobTime += deltaTime;
        Quat4f spin = new Quat4f();
        spin.set(new AxisAngle4f(0, 1, 0, anglePerFrame));


        currentVisualRotation.mul(spin, currentVisualRotation);
        currentVisualRotation.normalize();


        for (GameObject obj : visualizecrate) {
            if (obj != null) {
                obj.update();
                obj.SetVisible(IsPick);
                Transform t = obj.getTransform();
                javax.vecmath.Vector3f pos = new javax.vecmath.Vector3f(t.origin);


                t.setRotation(currentVisualRotation);

                float offsetY = (float)Math.sin(bobTime * bobSpeed) * bobHeight;
                pos.y = posviewbox.y() + offsetY;
                t.origin.set(pos);

                // сохраняем текущее вращение

            }
        }


    }
    // Переопределяем метод входа в триггер
    @Override
    public void onTriggerEnter(RigidBody other) {
        // 1. Проверяем кулдаун. Если прошло меньше 10 секунд - выходим из метода.
        if (!IsPick) {
            return;
        }

        // 2. Пытаемся выдать оружие локальному игроку
        Player localPlayer = Scene.getInstance().getPlayerByRigidBody(other);
        if (localPlayer != null) {
            MatchManager.getInstance().CurretPlayerRandomPickUp();

            TimeDelay=COOLDOWN_MS;
            IsPick=false;
            DieCube();

            return;
        }

        // 3. Пытаемся выдать оружие сетевому игроку (боту)
        NetPlayer netPlayer = Scene.getInstance().getNetPlayerByRigidBody(other);
        if (netPlayer != null) {

            TimeDelay=COOLDOWN_MS;
            IsPick=false;
            DieCube();

        }
    }


}