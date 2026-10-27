package org.engine.scene;

import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.dynamics.RigidBody;
import org.engine.physics.PhysicsWorld;
import org.engine.scene.entity.NetPlayer;
import org.engine.scene.entity.Player;
import org.engine.scene.entity.SpawPoint;
import org.engine.scene.entity.Trigger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Scene {
    private static volatile Scene instance;

    private PhysicsWorld physics;
    private List<GameObject> gameObjects;

    // Новый список для объектов, которые нужно удалить
    private List<GameObject> objectsToRemove;
    private List<GameObject> objectsToAdd;

    private Scene() {
        this.gameObjects = new ArrayList<>();
        this.objectsToRemove = new ArrayList<>(); // Инициализируем мусорку
        this.objectsToAdd = new ArrayList<>();
        physics = new PhysicsWorld();
        physics.init();
    }

    public static Scene getInstance() {
        if (instance == null) {
            synchronized (Scene.class) {
                if (instance == null) {
                    instance = new Scene();
                }
            }
        }
        return instance;
    }

    public void addGameObject(GameObject gameObject) {
        objectsToAdd.add(gameObject);

    }

    public void addListGameObject(List<GameObject> gameObjects) {
        if(gameObjects==null){return;}
        // Добавляем в рендер
        objectsToAdd.addAll(gameObjects);

    }

    public NetPlayer getNetPlayerFromID(int id){

        for (GameObject go : gameObjects) {
                if (go instanceof NetPlayer player) {
                    if (player.id == id) {
                        System.out.println(player.id);
                    return player;
                }
            }
        }
        return null;
    }

    public void removeGameObjectFromID(int id) {
        for (GameObject go : gameObjects) {
            if (go.id == id) {
                objectsToRemove.add(go);
                return;
            }
        }

    }


    /**
     * Помечает объект на удаление. Не удаляет сразу!
     */
    public void removeGameObject(GameObject gameObject) {
        // Добавляем в список на удаление, чтобы сделать это безопасно после цикла
        objectsToRemove.add(gameObject);
    }

    public void removeListGameObject(List<GameObject> gameObjects) {
        // Добавляем в список на удаление, чтобы сделать это безопасно после цикла
        objectsToRemove.addAll(gameObjects);
        gameObjects.clear();
    }

    /**
     * Обновляет логику всех объектов на сцене.
     * @param deltaTime время, прошедшее с прошлого кадра
     */
    public void update(float deltaTime) {
        physics.step(deltaTime);


        // 1. Обновляем все объекты
        for (GameObject go : gameObjects) {
            go.update();

            Trigger trigger = getTrigger(go);

            if(trigger!=null) {
                Set<RigidBody> overlappingBodies = new HashSet<>();

                // Получаем количество "манифолдов" (контактов) в этом кадре
                int numManifolds = physics.dynamicsWorld.getDispatcher().getNumManifolds();
                for (int i = 0; i < numManifolds; i++) {
                    PersistentManifold manifold = physics.dynamicsWorld.getDispatcher().getManifoldByIndexInternal(i);

                    // Получаем тела, участвующие в контакте
                    RigidBody bodyA = (RigidBody) manifold.getBody0();
                    RigidBody bodyB = (RigidBody) manifold.getBody1();

                    // Если одно из тел — наш триггер, а второе — динамическое (не триггер)
                    if (bodyA == trigger.getRigidBody() && bodyB.getCollisionFlags() != CollisionFlags.NO_CONTACT_RESPONSE) {
                        overlappingBodies.add(bodyB);
                    } else if (bodyB == trigger.getRigidBody() && bodyA.getCollisionFlags() != CollisionFlags.NO_CONTACT_RESPONSE) {
                        overlappingBodies.add(bodyA);
                    }
                }

                // 3. Обновляем триггер найденными пересечениями
                trigger.updateOverlaps(overlappingBodies);
            }
        }

        // 2. Если кто-то "умер" во время обновления, безопасно удаляем его после цикла


        // Потом удаляем мёртвые
        if (!objectsToRemove.isEmpty()) {
            for (GameObject go : objectsToRemove) {
                if (go.getRigidBody() != null) {
                    physics.dynamicsWorld.removeRigidBody(go.getRigidBody());
                }
                gameObjects.remove(go);
                go.cleanup();
            }
            objectsToRemove.clear();
        }
        if (!objectsToAdd.isEmpty()) {
            for (GameObject go : objectsToAdd) {
                gameObjects.add(go);
                if (go.getRigidBody() != null) {
                    physics.dynamicsWorld.addRigidBody(go.getRigidBody(),
                            (short) go.getCollisionFilterGroup(),
                            (short) go.getCollisionFilterMask());

                }
            }
            objectsToAdd.clear();
        }
    }

    public Player getPlayer(GameObject obj) {
        if (obj instanceof Player player) {
            return player;
        }
        return null;
    }

    public Trigger getTrigger(GameObject obj) {
        if (obj instanceof Trigger player) {
            return player;
        }
        return null;
    }

    public Player getPlayerByRigidBody(RigidBody body) {
        if (body == null) {
            return null;
        }

        for (GameObject go : gameObjects) {
            if (body.equals(go.getRigidBody())) {
                if (go instanceof Player player) {
                    return player;
                }
            }
        }

        return null;
    }

    public NetPlayer getNetPlayerByRigidBody(RigidBody body) {
        if (body == null) {
            return null;
        }

        for (GameObject go : gameObjects) {
            if (body.equals(go.getRigidBody())) {
                if (go instanceof NetPlayer player) {
                    return player;
                }
            }
        }

        return null;
    }


    public GameObject getGameObjectByRigidBody(RigidBody body) {
        if (body == null) {
            return null;
        }

        for (GameObject go : gameObjects) {
            if (body.equals(go.getRigidBody())) {
                return go;
            }
        }

        return null;
    }

    public List<SpawPoint> getGameObjectsSpawnPoint() {
        List<SpawPoint> result = new ArrayList<>();
        for (GameObject obj : gameObjects) {
            if (obj instanceof SpawPoint sp) {
                    result.add(sp);

            }
        }
        return result;
    }


    public List<GameObject> getGameObjects() {
        return gameObjects;
    }

    public PhysicsWorld GetPhysicsWorld(){
        return physics;
    }

    public void clearScene() {
        // 1. Полностью очищаем все списки объектов
        this.gameObjects.clear();
        this.objectsToAdd.clear();
        this.objectsToRemove.clear();

        // 2. Полностью пересоздаем физический мир!
        // Это единственный 100% способ избежать багов JBullet (createFunc is null)
        // при массовом удалении объектов с активными коллизиями.
        this.physics = new PhysicsWorld();
        this.physics.init();

        System.out.println("Сцена и физический мир полностью очищены.");
    }
}