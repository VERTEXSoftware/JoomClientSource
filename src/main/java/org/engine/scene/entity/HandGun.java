package org.engine.scene.entity;

import com.bulletphysics.linearmath.Transform;
import org.engine.audio.SoundBuffer;
import org.engine.audio.SoundSource;
import org.engine.core.Time;
import org.engine.render.Shader;
import org.engine.scene.GameObject;
import org.engine.scene.Scene;
import org.engine.utils.ModelLoader;
import org.engine.utils.ResourceCache;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandGun extends GameObject {

    public enum TypeGun {
        HandGuy,
        RevolverGun,
        ShotGun,
        AkaGun
    }

    // Внутренний класс для хранения данных конкретного оружия
    private static class WeaponData {
        List<GameObject> parts;
        List<SoundBuffer> audios;
        Vector3f back_anim;
        int distantion;
        int damage;
        float relodspeed;
        int maxAmmo;
        float dmgvelocity;
        float kickStrength; // сила угловой отдачи (рад)

        public WeaponData(List<GameObject> parts, String[] aud, Vector3f back_anim, int distantion, int damage, float relodspeed, int maxAmmo, float damgvelocity, float kickStrength) {
            this.parts = parts;
            this.audios = new ArrayList<>();
            for (String s : aud) {
                SoundBuffer sound = ResourceCache.getSound(s);
                if (sound != null) {
                    this.audios.add(sound);
                }
            }

            this.back_anim = back_anim;
            this.distantion = distantion;
            this.damage = damage;
            this.relodspeed = relodspeed;
            this.maxAmmo = maxAmmo;
            this.dmgvelocity = damgvelocity;
            this.kickStrength = kickStrength;
        }
    }

    // Хранилище всего загруженного оружия
    private Map<TypeGun, WeaponData> weapons = new HashMap<>();
    private TypeGun activeWeapon = TypeGun.HandGuy;

    // Текущие характеристики активного оружия
    private Vector3f back_anim = new Vector3f(0,0,1);
    private Vector3f norm_anim = new Vector3f(0,0,0);
    private Vector3f orig_anim = new Vector3f(0,0,0);
    private Vector3f pickup_anim = new Vector3f(0,-15,0);
    private int distantion = 10;
    private int damage = 10;
    private float relodspeed = 10;
    private int maxAmmo = 10;
    private float dmgvelocity;

    // --- Угловая отдача (поворот вокруг оси Right) ---
    private float kickAngle = 0.0f;          // текущий угол (рад)
    private float kickAngleTarget = 0.0f;    // целевой угол (рад)
    private float kickRecoverSpeed = 12.0f;  // скорость возврата
    private float kickStrength = 0.12f;      // сила отдачи (рад), ~7 градусов

    private SoundSource soundSource;
    private SoundBuffer pickupsound;

    public HandGun() {
        super(0,
                "HandGun",
                null,
                null,
                null,
                false,
                0.0f,
                new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 0),
                new Vector3f(1, 1, 1)
        );

        soundSource = new SoundSource();
        soundSource.setGain(0.8f);

        Shader gunshd = ResourceCache.getShader("shader/vertex.glsl", "shader/fragment.glsl");

        weapons.put(TypeGun.HandGuy, new WeaponData(
                ModelLoader.load("model/hands_knifegun.dae", gunshd,  0.0f, false, new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(4, 4, 4),true),
                new String[]{"sound/player/knife01.ogg"}, new Vector3f(0,0,-1), 6, 15, 700.0f, 0, 1.0f, 0.10f
        ));

        weapons.put(TypeGun.RevolverGun, new WeaponData(
                ModelLoader.load("model/hands_magnumgun.dae", gunshd,  0.0f, false, new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(4, 4, 4),true),
                new String[]{"sound/player/revolver01.ogg","sound/player/revolver01.ogg"}, new Vector3f(0,0,1), 400, 20, 400.0f, 12, 3.0f, 0.18f
        ));

        weapons.put(TypeGun.ShotGun, new WeaponData(
                ModelLoader.load("model/hands_shootgun.dae", gunshd,  0.0f, false, new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(4, 4, 4),true),
                new String[]{"sound/player/shotgun_fire1.ogg","sound/player/shotgun_fire2.ogg"}, new Vector3f(0,0,1), 300, 15, 300.0f, 16, 3.0f, 0.28f
        ));

        weapons.put(TypeGun.AkaGun, new WeaponData(
                ModelLoader.load("model/hands_akagun.dae", gunshd,  0.0f, false, new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(4, 4, 4),true),
                new String[]{"sound/player/aka_fire1.ogg","sound/player/aka_fire2.ogg"}, new Vector3f(0,0,1), 400, 5, 800.0f, 60, 1.2f, 0.07f
        ));

        // Делаем все модели изначально невидимыми
        for (WeaponData data : weapons.values()) {
            if (data.parts != null) {
                for (GameObject part : data.parts) {
                    part.SetVisible(false);
                }
            }
        }

        // Устанавливаем оружие по умолчанию
        switchWeapon(TypeGun.HandGuy);
        pickupsound = ResourceCache.getSound("sound/player/pickup.ogg");
    }

    public int getCurrentWeaponIndex() {
        if (activeWeapon == null) {
            return -1;
        }
        return activeWeapon.ordinal();
    }

    public TypeGun getWeaponByIndex(int index) {
        TypeGun[] guns = TypeGun.values();
        if (index >= 0 && index < guns.length) {
            return guns[index];
        }
        return null;
    }

    public void switchWeaponByIndex(int index) {
        TypeGun gun = getWeaponByIndex(index);
        if (gun != null) {
            switchWeapon(gun);
        }
    }

    public void switchWeapon(TypeGun type) {
        if (!weapons.containsKey(type)) return;

        // Скрываем текущее оружие
        if (activeWeapon != null) {
            WeaponData oldData = weapons.get(activeWeapon);
            if (oldData != null && oldData.parts != null) {
                for (GameObject part : oldData.parts) {
                    part.SetVisible(false);
                }
            }
        }

        // Устанавливаем новое активное оружие
        activeWeapon = type;
        WeaponData newData = weapons.get(activeWeapon);

        // Показываем новое оружие
        if (newData != null && newData.parts != null) {
            for (GameObject part : newData.parts) {
                part.SetVisible(true);
            }
        }

        // Обновляем текущие характеристики
        if (newData != null) {
            this.back_anim = newData.back_anim;
            this.distantion = newData.distantion;
            this.damage = newData.damage;
            this.relodspeed = newData.relodspeed;
            this.maxAmmo = newData.maxAmmo;
            this.dmgvelocity = newData.dmgvelocity;
            this.kickStrength = newData.kickStrength;
        }

        // Сбрасываем анимацию отдачи в исходное состояние
        orig_anim.set(pickup_anim);
        kickAngle = 0.0f;
        kickAngleTarget = 0.0f;

        if (soundSource != null && pickupsound != null) {
            soundSource.play(pickupsound, false);
        }
    }

    public void Add() {
        Scene scene = Scene.getInstance();
        for (WeaponData data : weapons.values()) {
            if (data.parts != null && !data.parts.isEmpty()) {
                scene.addListGameObject(data.parts);
            }
        }
    }

    public void shotstart() {
        orig_anim.set(back_anim);

        // Угловая отдача: оружие "клюёт" вокруг оси Right
        kickAngleTarget = kickStrength;

        if (soundSource != null) {
            if (activeWeapon != null) {
                WeaponData tmpData = weapons.get(activeWeapon);
                if (tmpData != null && !tmpData.audios.isEmpty()) {
                    soundSource.playRnd(tmpData.audios, false);
                }
            }
        }
    }

    public void update() {
        super.update();
        float deltaTime = Time.getInstance().getDeltaSecond();

        // Обновляем позицию источника звука
        if (soundSource != null) {
            javax.vecmath.Vector3f pos = this.getPosition();
            soundSource.setPosition(pos.x, pos.y, pos.z);
        }

        if (activeWeapon != null) {
            WeaponData data = weapons.get(activeWeapon);
            if (data != null && data.parts != null) {

                // --- Угловая отдача через lerp к целевому углу ---
                // При выстреле цель = kickStrength, иначе возвращаемся к 0.
                // После "кика" цель сбрасываем в 0, чтобы lerp тянул обратно.
                kickAngle = lerp(kickAngle, kickAngleTarget, Math.min(1.0f, deltaTime * kickRecoverSpeed));

                // Как только приблизились к цели-кику — начинаем возврат к нулю
                if (kickAngleTarget != 0.0f && Math.abs(kickAngle - kickAngleTarget) < 0.01f) {
                    kickAngleTarget = 0.0f;
                }
                if (Math.abs(kickAngle) < 0.001f) kickAngle = 0.0f;

                for (GameObject part : data.parts) {
                    if (!part.IsVisible()) continue;

                    part.update();

                    javax.vecmath.Vector3f localOffset = new javax.vecmath.Vector3f(orig_anim.x, orig_anim.y, orig_anim.z);
                    javax.vecmath.Vector3f orgPos = this.getPosition();

                    javax.vecmath.Vector3f worldOffset = new javax.vecmath.Vector3f();
                    this.getTransform().basis.transform(localOffset, worldOffset);

                    javax.vecmath.Vector3f gunPos = new javax.vecmath.Vector3f(orgPos);
                    gunPos.add(worldOffset);

                    part.getTransform().origin.set(gunPos);

                    // Поворот камеры
                    javax.vecmath.Quat4f camRot = new javax.vecmath.Quat4f();
                    this.getTransform().getRotation(camRot);

                    // Поворот отдачи вокруг локальной оси Right (X)
                    javax.vecmath.Quat4f kickRot = new javax.vecmath.Quat4f();
                    kickRot.set(new javax.vecmath.AxisAngle4f(1.0f, 0.0f, 0.0f, kickAngle));

                    // Итоговый поворот = camRot * kickRot
                    javax.vecmath.Quat4f finalRot = new javax.vecmath.Quat4f(camRot);
                    finalRot.mul(kickRot);

                    part.getTransform().setRotation(finalRot);
                }

                // lerp изменяет сам вектор, переприсваивание не нужно
                orig_anim.lerp(norm_anim, deltaTime * 10.0f);
            }
        }
    }

    // Линейная интерполяция для float
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public void Remove() {
        Scene scene = Scene.getInstance();
        for (WeaponData data : weapons.values()) {
            if (data.parts != null) {
                scene.removeListGameObject(data.parts);
            }
        }

        if (soundSource != null) {
            soundSource.cleanup();
        }
    }

    // Геттеры для получения текущих характеристик активного оружия
    public int getDamage() { return damage; }
    public int getDistantion() { return distantion; }
    public float getRelodspeed() { return relodspeed; }
    public int getMaxAmmo() { return maxAmmo; }
    public TypeGun getActiveWeapon() { return activeWeapon; }
    public float getDmgVelocity() { return dmgvelocity; }
}