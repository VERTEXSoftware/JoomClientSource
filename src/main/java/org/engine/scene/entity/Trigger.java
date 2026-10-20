package org.engine.scene.entity;

import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.dynamics.RigidBody;
import org.engine.core.MatchManager;
import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.engine.scene.Scene;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

public class Trigger extends GameObject {

    public enum TypeTrigger {
        PlayerKill,
        PlayerDamage,
        PlayerJump,
        WeaponsPickUp
    }

    private final TypeTrigger typeTrigger;

    // Списки для отслеживания входа/выхода объектов из триггера
    private final Set<RigidBody> currentBodies = new HashSet<>();
    private final Set<RigidBody> previousBodies = new HashSet<>();

    public Trigger(String name,Mesh mesh,
            Vector3f startPosition,
            Vector3f startRotation,
            Vector3f startScale,
            TypeTrigger typeTrigger
    ) {
        // Вызываем конструктор родителя.
        // Передаем createph = true и mass = 1.0f,
        // чтобы родитель создал ConvexHullShape (выпуклую форму),
        // которая корректно работает с динамическими объектами.
        super(0, name,mesh, null, null, true, 0.0f, startPosition, startRotation, startScale);

        this.typeTrigger = typeTrigger;

        RigidBody rb = getRigidBody();
        if (rb != null) {
            // 1. Делаем тело кинематическим (оно не будет падать от гравитации)
            // И отключаем физический ответ на коллизии (через него можно проходить)
            rb.setCollisionFlags(
                    rb.getCollisionFlags() |
                            CollisionFlags.KINEMATIC_OBJECT |
                            CollisionFlags.NO_CONTACT_RESPONSE
            );

            // 2. Отключаем гравитацию для триггера
            rb.setGravity(new javax.vecmath.Vector3f(0, 0, 0));

            // 3. Полностью замораживаем его перемещение
            //rb.setLinearFactor(new javax.vecmath.Vector3f(0, 0, 0));
            //rb.setAngularFactor(new javax.vecmath.Vector3f(0, 0, 0));
        }
    }

    public TypeTrigger getTypeTrigger() {
        return typeTrigger;
    }

    /**
     * Этот метод ДОЛЖЕН вызываться каждый кадр из вашего главного цикла физики,
     * передавая ему список тел, которые сейчас пересекаются с триггером.
     *
     * Как получить overlappingBodies: см. пример ниже.
     */
    public void updateOverlaps(Set<RigidBody> overlappingBodies) {
        // Сохраняем объекты, которые были внутри в прошлом кадре
        previousBodies.clear();
        previousBodies.addAll(currentBodies);

        // Обновляем список текущих объектов
        currentBodies.clear();
        currentBodies.addAll(overlappingBodies);

        // 1. Проверяем, кто только что ВОШЕЛ в триггер
        for (RigidBody body : currentBodies) {
            if (!previousBodies.contains(body)) {
                onTriggerEnter(body);
            }
        }

        // 2. Проверяем, кто только что ВЫШЕЛ из триггера
        for (RigidBody body : previousBodies) {
            if (!currentBodies.contains(body)) {
                onTriggerExit(body);
            }
        }
    }

    // Методы-события, которые можно переопределять в наследниках
    public void onTriggerEnter(RigidBody other) {
       // System.out.println("Объект вошел в триггер: " + typeTrigger);
        // Здесь можно применять урон, убивать игрока и т.д.
        applyEffect(other);
        applyEffectBot(other);
    }

    public void onTriggerExit(RigidBody other) {
      //  System.out.println("Объект вышел из триггера: " + typeTrigger);
    }

    private void applyEffect(RigidBody other) {


        Player plr = Scene.getInstance().getPlayerByRigidBody(other);

       if(plr==null){return;}

        switch (typeTrigger) {
            case PlayerKill:
                plr.health=0;
                MatchManager.getInstance().SendDeath();
                break;
            case PlayerDamage:
                // gameManager.damagePlayer(10);
                break;
            case PlayerJump:
                // other.applyCentralImpulse(new javax.vecmath.Vector3f(0, 10, 0));
                break;
            case WeaponsPickUp:

                break;
        }
    }

    private void applyEffectBot(RigidBody other) {


        NetPlayer plr = Scene.getInstance().getNetPlayerByRigidBody(other);

        if(plr==null){return;}

        switch (typeTrigger) {
            case PlayerKill:
                plr.health=0;
                break;
            case PlayerDamage:
                // gameManager.damagePlayer(10);
                break;
            case PlayerJump:
                // other.applyCentralImpulse(new javax.vecmath.Vector3f(0, 10, 0));
                break;
            case WeaponsPickUp:

                break;
        }
    }
}