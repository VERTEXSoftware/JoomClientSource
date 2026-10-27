package org.engine.core;

import org.engine.audio.SoundBuffer;
import org.engine.audio.SoundSource;
import org.engine.network.GameClient;
import org.engine.render.*;
import org.engine.scene.GameObject;
import org.engine.scene.Scene;
import org.engine.scene.entity.HandGun;
import org.engine.scene.entity.NetPlayer;
import org.engine.scene.entity.Player;
import org.engine.utils.ModelLoader;
import org.engine.utils.ResourceCache;
import org.engine.utils.ResourceManager;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.slvl.ManagerPack;

import javax.vecmath.Quat4f;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class Engine {

    private enum GameState {
        MENU, CONNECTING, PLAYING
    }

    private List<GameObject> menuCharacterMesh;
    private List<GameObject> menuScene;
    private int currentMenuModelIndex = -1;

    private Window window;
    private Renderer renderer;


    private MainMenu mainMenu;
    private GameState gameState = GameState.MENU;

    private GameClient gameClient;
    private Camera camera;

    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;
    private boolean running = false;

    private long lastNetworkSendTime = 0;
    private final long sendIntervalMs = 50;
    private float timeSinceLastShot = 0.0f;
    private final long shotCooldownMs = 1000;

    private long connectStartTime = 0;
    private static final long CONNECT_TIMEOUT_MS = 15000;

    private String pendingServerIp = null;
    private int pendingServerPort = 12789;

    List<String> modelpack;
    // Флаг ошибки подключения
    private volatile boolean connectionFailed = false;
    private SoundBuffer Musicmenu;
    private SoundSource pointbackmusic;

    // === SWAY === поля для покачивания камеры в меню
    private float camShakeTime = 0f;
    private final Vector3f camBasePos = new Vector3f(0, 10, 5); // базовая позиция меню
    private static final float CAM_SWAY_AMP_X = 0.35f;   // амплитуда по X
    private static final float CAM_SWAY_AMP_Y = 0.18f;   // амплитуда по Y (вертикальное дыхание)
    private static final float CAM_SWAY_AMP_Z = 0.25f;   // амплитуда по Z
    private static final float CAM_SWAY_SPEED = 1.2f;    // скорость (рад/сек)
    // === SWAY ===

    private final GLFWMouseButtonCallbackI mouseButtonCallback = (handle, button, action, mods) -> {
        if (gameState != GameState.MENU && gameState != GameState.CONNECTING) return;
        if (action != GLFW_PRESS) return;
        if (mainMenu.isSplashActive()) return;   // игнорируем клики во время баннера

        double[] mx = new double[1];
        double[] my = new double[1];
        glfwGetCursorPos(handle, mx, my);

        MainMenu.ClickAction clickAction = mainMenu.onMouseClick(mx[0], my[0]);
        handleClickAction(clickAction);
    };

    private final GLFWKeyCallbackI keyCallback = (handle, key, scancode, action, mods) -> {
        if (gameState == GameState.PLAYING) {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                returnToMenu();
            }
            return;
        }
        if (mainMenu.isSplashActive()) return;   // игнорируем ввод во время баннера
        if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            // Передаём action, чтобы MainMenu мог отличить PRESS от REPEAT
            // (например, для Ctrl+V вставлять только один раз)
            mainMenu.onKeyPressed(key, mods, action);
        }
    };

    private final GLFWCharCallbackI charCallback = (handle, codepoint) -> {
        if (gameState == GameState.MENU && !mainMenu.isSplashActive()) {
            mainMenu.onCharTyped((char) codepoint);
        }
    };

    void LoadMenuLevel(){
        Scene.getInstance().update(0);
        cameraRestartPos();
        if(menuScene!=null){
            Scene.getInstance().removeListGameObject(menuScene);
            menuScene.clear();
            menuScene=null;
        }
        Scene.getInstance().update(0);

        Shader nepl = ResourceCache.getShader("shader/vertex.glsl", "shader/fragment.glsl");
        // Ставим модель по центру координат (0,0,0)
        menuScene = ModelLoader.load(
                "model/menu.dae", nepl, 0.0f, false,
                new Vector3f(0, 5, 0),
                new Vector3f(0, 0, 0),
                new Vector3f(10, 10, 10),false
        );
        Scene.getInstance().addListGameObject(menuScene);
        Scene.getInstance().update(0);
    }

    void applyProfile(){
        MainMenu.Settings settings = mainMenu.getSettings();

        MatchManager.getInstance().cur_name_player = settings.playerName;
        MatchManager.getInstance().cur_model_player = settings.playerModel;
    }

    private void handleClickAction(MainMenu.ClickAction action) {
        if (action == MainMenu.ClickAction.NONE) return;

        switch (action) {
            case GO_SETTINGS:
                // запоминаем текущие настройки, чтобы BACK мог их вернуть
                mainMenu.beginEditSession();
                mainMenu.setScreen(MainMenu.Screen.Settings);
                break;
            case GO_PROFILE:
                // запоминаем текущий профиль (ник + модель), чтобы BACK мог их вернуть
                mainMenu.beginEditSession();
                mainMenu.setScreen(MainMenu.Screen.Profile);
                break;
            case GO_SERVERS: mainMenu.setScreen(MainMenu.Screen.Servers); break;
            case GO_MAIN: mainMenu.setScreen(MainMenu.Screen.Main); gameClient.stop(); break;
            case GO_EXIT: running = false; break;
            case APPLY_SETTINGS:
                applySettings();
                mainMenu.commitChanges(); // применено — откат не нужен
                mainMenu.setScreen(MainMenu.Screen.Main);
                mainMenu.saveConfig();
                break;
            case SAVE_PROFILE:
                applyProfile();
                mainMenu.commitChanges(); // сохранено — откат не нужен
                mainMenu.setScreen(MainMenu.Screen.Main);
                mainMenu.saveConfig();
                break;
            case DISCARD_CHANGES: {
                // BACK — возвращаем значения, которые были до входа в экран
                MainMenu.Screen from = mainMenu.getScreen();
                mainMenu.discardChanges();
                if (from == MainMenu.Screen.Settings) {
                    // Настройки применяются "вживую" (окно, громкость, vsync, fps),
                    // поэтому после отката переприменяем старые значения.
                    applySettings();
                }
                mainMenu.setScreen(MainMenu.Screen.Main);
                break;
            }
            case CONNECT_SERVER:
                String ip = mainMenu.getSelectedServerIp();
                if (ip != null) startConnecting(ip);
                break;
            case TOGGLE_FULLSCREEN:
                mainMenu.getSettings().fullscreen = !mainMenu.getSettings().fullscreen;
                break;
            case TOGGLE_VSYNC:
                mainMenu.getSettings().vsync = !mainMenu.getSettings().vsync;
                break;
            case MODEL_PREV:
                mainMenu.getSettings().playerModel = (mainMenu.getSettings().playerModel - 1 + modelpack.size()) % modelpack.size();
                updateMenuCharacter(); // Перезагружаем модель
                break;
            case MODEL_NEXT:
                mainMenu.getSettings().playerModel = (mainMenu.getSettings().playerModel + 1) % modelpack.size();
                updateMenuCharacter(); // Перезагружаем модель
                break;
            case RES_PREV: mainMenu.changeResolution(-1); break;
            case RES_NEXT: mainMenu.changeResolution(1);  break;
            case FPS_PREV: mainMenu.changeFps(-1);        break;
            case FPS_NEXT: mainMenu.changeFps(1);         break;

            default: break;
        }
    }

    public void start() {




        modelpack = new java.util.ArrayList<>();
        modelpack.add("model/kinger.dae");
        modelpack.add("model/ghost.dae");
        modelpack.add("model/bishop.dae");
        modelpack.add("model/dicer.dae");
        modelpack.add("model/karen.dae");
        modelpack.add("model/crow.dae");
        modelpack.add("model/mettaton.dae");
        modelpack.add("model/kerfus.dae");
        modelpack.add("model/physicle.dae");
        modelpack.add("model/clearbot.dae");
        modelpack.add("model/creeper.dae");
        modelpack.add("model/scp173.dae");

        ResourceManager.initGlobalPack("data.slop");

        window = new Window("JOOM", 1920, 1080);
        window.init();
        window.setCursorDisabled(false);
        renderer = new Renderer();

        mainMenu = new MainMenu();
        try {
            mainMenu.init();
        } catch (Exception e) {
            System.err.println("Failed to init MainMenu: " + e.getMessage());
            e.printStackTrace();
        }

        AudioManager.init();

        Musicmenu = ResourceCache.getSound("sound/music/menu01.ogg");

        pointbackmusic = new SoundSource();
        pointbackmusic.setAmbient(true);
        pointbackmusic.setVolume(0.4f);
        pointbackmusic.play(Musicmenu, true);

        camera = new Camera(new Vector3f(0, 10, 5), new Vector3f(0, 0, 0), 90.0f, window.getWidth(), window.getHeight(), 0.1f, 1000.0f);
        try { renderer.init(camera); } catch (Exception e) { e.printStackTrace(); }

        MatchManager.getInstance().Init(camera);

        applySettings();
        applyProfile();
        LoadMenuLevel();

        long glfwWindow = window.getWindowHandle();
        if (glfwWindow != 0) {
            glfwSetMouseButtonCallback(glfwWindow, mouseButtonCallback);
            glfwSetKeyCallback(glfwWindow, keyCallback);
            glfwSetCharCallback(glfwWindow, charCallback);
            glfwSetInputMode(glfwWindow, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }

        running = true;
        System.out.println("Engine started, showing main menu (splash first)...");


        loop();
    }

    private void loop() {
        while (!window.shouldClose() && running) {
            Time.getInstance().update();
            float delta = Time.getInstance().getDeltaSecond();

            if (menuCharacterMesh != null) {
                for (GameObject go : menuCharacterMesh) {
                    if (go != null) {
                        go.SetVisible(false);
                    }
                }
            }


            switch (gameState) {
                case MENU:
                    updateMenu(delta);
                    updateMenuCameraSway(delta);
                    renderMenu();
                    break;
                case CONNECTING:
                    updateConnecting(delta);
                    updateMenuCameraSway(delta);
                    renderMenu();
                    break;
                case PLAYING:
                    update(delta);
                    render();
                    break;
            }
            window.update();
        }
        cleanup();
    }


    private void updateMenuCharacter() {

        boolean shouldShow = (gameState == GameState.MENU && mainMenu.getScreen() == MainMenu.Screen.Profile);

        if (!shouldShow) {
            if (menuCharacterMesh != null) {
                Scene.getInstance().removeListGameObject(menuCharacterMesh);
                menuCharacterMesh.clear();
                menuCharacterMesh = null;
                currentMenuModelIndex = -1;
            }
            Scene.getInstance().update(0);
            return;
        }

        int targetModel = mainMenu.getSettings().playerModel;
        if (targetModel != currentMenuModelIndex) {
            // Удаляем старую модель
            if (menuCharacterMesh != null) {
                Scene.getInstance().removeListGameObject(menuCharacterMesh);
                menuCharacterMesh.clear();
            }

            String path = "model/kinger.dae";
            if (targetModel >= 0 && targetModel < modelpack.size()) {
                path = modelpack.get(targetModel);
            }



            Shader nepl = ResourceCache.getShader("shader/vertex.glsl", "shader/fragment.glsl");
            // Ставим модель по центру координат (0,0,0)
            menuCharacterMesh = ModelLoader.load(
                    path, nepl, 0.0f, false,
                    new Vector3f(0, 8, -1),
                    new Vector3f(0, 0, 0),
                    new Vector3f(3, 3, 3),false
            );
            Scene.getInstance().addListGameObject(menuCharacterMesh);
            Scene.getInstance().update(0);

            currentMenuModelIndex = targetModel;
        }

    }

    private final float SPIN_DEGREES_PER_SEC = 50.0f; // 50°/сек ≈ 7.2 сек на оборот
    private final float SPIN_RADIANS_PER_SEC = (float) Math.toRadians(SPIN_DEGREES_PER_SEC);
    private final float TWO_PI = (float) (Math.PI * 2);

    private float spinAngle = 0.0f;

    // === SWAY === покачивание камеры в меню
    private void updateMenuCameraSway(float delta) {
        if (camera == null) return;

        camShakeTime += delta * CAM_SWAY_SPEED;

        // Комбинация двух частот — движение выглядит менее «механическим»
        float dx = (float) Math.sin(camShakeTime * 1.0) * CAM_SWAY_AMP_X;
        float dy = (float) Math.sin(camShakeTime * 0.7 + 1.3) * CAM_SWAY_AMP_Y;
        float dz = (float) Math.cos(camShakeTime * 0.85 + 0.5) * CAM_SWAY_AMP_Z;

        camera.getTransform().origin.set(
                camBasePos.x + dx,
                camBasePos.y + dy,
                camBasePos.z + dz
        );

        // Небольшой наклон камеры, чтобы покачивание читалось
        float yaw   = (float) Math.sin(camShakeTime * 0.6) * 0.015f; // ~0.9°
        float pitch = (float) Math.sin(camShakeTime * 0.9 + 0.8) * 0.010f;

        Quat4f qYaw = new Quat4f();
        qYaw.set(new javax.vecmath.AxisAngle4f(0, 1, 0, yaw));
        Quat4f qPitch = new Quat4f();
        qPitch.set(new javax.vecmath.AxisAngle4f(1, 0, 0, pitch));

        Quat4f q = new Quat4f();
        q.mul(qYaw, qPitch); // yaw * pitch
        camera.getTransform().setRotation(q);

        camera.update();
    }
    // === SWAY ===

    private void updateMenu(float delta) {
        double mx = window.getMouseX();
        double my = window.getMouseY();
        mainMenu.setMousePosition((float) mx, (float) my);

        // НОВОЕ: тикаем баннер, пока он активен
        mainMenu.updateSplash(delta);

        // Пока сплэш активен — не даём слайдерам/модели/кликам работать
        if (mainMenu.isSplashActive()) {
            return;
        }

        mainMenu.updateSliderDrag(mx, window.isMouseButtonDown(GLFW_MOUSE_BUTTON_1));

        // === SWAY === покачивание камеры в меню

        // === SWAY ===

        updateMenuCharacter();



        // Медленно вращаем 3D-модель для демонстрации
        if (menuCharacterMesh != null) {
            spinAngle += SPIN_RADIANS_PER_SEC * delta;
            if (spinAngle >= TWO_PI) {
                spinAngle -= TWO_PI;
            }
            javax.vecmath.AxisAngle4f axisAngle = new javax.vecmath.AxisAngle4f(0, 1, 0, spinAngle);
            javax.vecmath.Quat4f quat = new javax.vecmath.Quat4f();
            quat.set(axisAngle);

            for (GameObject go : menuCharacterMesh) {
                if (go != null) {
                    go.SetVisible(true);
                    go.getTransform().setRotation(quat);
                }
            }
        }
        if(camera!=null) {
            AudioManager.updateListener(camera.getCameraPosition(),new Vector3f(0,0,0),camera.getForward(),camera.getUp());
        }
    }

    private void renderMenu() {

        renderer.clear();
        renderer.beginFrame();



        for (GameObject go : Scene.getInstance().getGameObjects()) {
            renderer.submit(go);
        }
        renderer.flush();

        mainMenu.beginFrameHitboxes();
        mainMenu.render(window.getWidth(), window.getHeight());
    }

    private void startConnecting(String ipWithPort) {
        try {
            String[] parts = ipWithPort.split(":");
            pendingServerIp = parts[0];
            pendingServerPort = (parts.length > 1) ? Integer.parseInt(parts[1]) : 12789;
        } catch (Exception e) {
            pendingServerIp = "127.0.0.1";
            pendingServerPort = 12789;
        }

        System.out.println("Connecting to " + pendingServerIp + ":" + pendingServerPort + "...");
        gameState = GameState.CONNECTING;
        mainMenu.setScreen(MainMenu.Screen.Connecting);
        connectStartTime = System.currentTimeMillis();
        connectionFailed = false;

        String nick = mainMenu.getSettings().playerName;
        if (nick == null || nick.isEmpty()) {
            nick = "Player_" + (int) (Math.random() * 1000);
        }
        final String finalNick = nick;

        gameClient = new GameClient();
        new Thread(() -> {
            try {
                gameClient.start(pendingServerIp, pendingServerPort);
            } catch (Exception e) {
                System.err.println("Network thread crashed: " + e.getMessage());
                connectionFailed = true;
            }
        }).start();

        // Ждем 500мс, чтобы Netty успел подключиться, иначе пакет потеряется
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            MatchManager.getInstance().SendLogin(finalNick);
        }).start();
    }

    private void updateConnecting(float delta) {
        double mx = window.getMouseX();
        double my = window.getMouseY();
        mainMenu.setMousePosition((float) mx, (float) my);

        // ВАЖНО: Читаем пакеты во время подключения! Иначе не получим ID и спавн.
        MatchManager.getInstance().ReadData();

        // Если поток сети упал — выкидываем в меню
        if (connectionFailed) {
            System.err.println("Connection failed!");
            returnToMenu();
            return;
        }

        // Проверяем, появился ли локальный игрок (сервер прислал MatchStartPacket)
        if (MatchManager.getInstance().cur_id_player >0) {
            System.out.println("Spawned in game! Switching to PLAYING state.");
            enterGame();
            return;
        }

        if (System.currentTimeMillis() - connectStartTime > CONNECT_TIMEOUT_MS) {
            System.err.println("Connection timeout!");
            returnToMenu();
        }
    }

    void cameraRestartPos(){
        if(camera!=null) {
            camera.update();
            // === SWAY === сбрасываем в базовую позицию меню
            camera.getTransform().origin.set(camBasePos.x, camBasePos.y, camBasePos.z);
            // === SWAY ===
            camera.getTransform().setRotation(new Quat4f(0, 0, 0, 1));
            camera.update();
        }
    }

    private void enterGame() {
        Scene.getInstance().update(0);
        pointbackmusic.stop();
        if(menuCharacterMesh!=null){
            Scene.getInstance().removeListGameObject(menuCharacterMesh);
            menuCharacterMesh.clear();
            menuCharacterMesh=null;
        }
        gameState = GameState.PLAYING;
        firstMouse = true;

        Player local = MatchManager.getInstance().getLocalPlayer();
        if (local != null) {
            local.setMouseSensitivity(mainMenu.getSettings().mouseSensitivity);
        }

        long glfwWindow = window.getWindowHandle();
        if (glfwWindow != 0) {
            glfwSetInputMode(glfwWindow, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }

        if(menuScene!=null){
            Scene.getInstance().removeListGameObject(menuScene);
            menuScene.clear();
            menuScene = null;
        }

        Scene.getInstance().update(0);
    }

    private void returnToMenu() {

        if (gameClient != null) {
            try { gameClient.stop(); } catch (Exception ignored) {}
            gameClient = null;
        }
        if(menuCharacterMesh!=null){
            Scene.getInstance().removeListGameObject(menuCharacterMesh);
            menuCharacterMesh.clear();
            menuCharacterMesh=null;
        }

        if(menuScene!=null){
            Scene.getInstance().removeListGameObject(menuScene);
            menuScene.clear();
            menuScene=null;
        }

        Scene.getInstance().update(0);

        MatchManager.getInstance().clearLocalPlayer();
        MatchManager.getInstance().ClearScene();
        MatchManager.getInstance().ClearSession();


        cameraRestartPos();

        // === SWAY === сбрасываем фазу покачивания
        camShakeTime = 0f;
        // === SWAY ===

        LoadMenuLevel();

        Musicmenu = ResourceCache.getSound("sound/music/menu01.ogg");

        pointbackmusic.setAmbient(true);
        pointbackmusic.setVolume(0.4f);
        pointbackmusic.play( Musicmenu,true);

        gameState = GameState.MENU;
        mainMenu.setScreen(MainMenu.Screen.Main);

        long glfwWindow = window.getWindowHandle();
        if (glfwWindow != 0) {
            glfwSetInputMode(glfwWindow, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
    }

    private void update(float deltaTime) {
        MatchManager.getInstance().ReadData();
        Scene.getInstance().update(deltaTime);
        processInput(deltaTime);

        if (!MatchManager.getInstance().isServerConnected()) {
            System.err.println("Потеряно соединение с сервером! Возврат в главное меню.");
            returnToMenu();
            return;
        }

        if(camera!=null) {
            AudioManager.updateListener(camera.getCameraPosition(),new Vector3f(0,0,0),camera.getForward(),camera.getUp());
        }

        if (System.currentTimeMillis() - lastNetworkSendTime >= sendIntervalMs) {
            MatchManager.getInstance().SendData();
            lastNetworkSendTime = System.currentTimeMillis();
        }
    }
boolean rendercul =false;
    private void processInput(float deltaTime) {
        Player player = MatchManager.getInstance().getLocalPlayer();
        if (player == null) return;
        if (player.health <= 0) return;

        double currentMouseX = window.getMouseX();
        double currentMouseY = window.getMouseY();
        if (!firstMouse) {
            float dx = (float) (-(currentMouseX - lastMouseX) * player.GetMouseSensitivity());
            float dy = (float) ((currentMouseY - lastMouseY) * player.GetMouseSensitivity());
            player.processMouseInput(dx, dy);
            lastMouseX = currentMouseX;
            lastMouseY = currentMouseY;
        } else {
            lastMouseX = currentMouseX;
            lastMouseY = currentMouseY;
            firstMouse = false;
        }

        float moveDirX = 0f, moveDirZ = 0f;
        boolean jumpPressed = false;
        boolean dropPressed = false;

        timeSinceLastShot += deltaTime;
        if (window.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_1)) {
            HandGun hndg = player.getWeaponsClass();
            if (hndg != null) {
                float currentCooldownSec = Math.max(0.1f, (shotCooldownMs - hndg.getRelodspeed()) / 1000.0f);
                float dmgvel = hndg.getDmgVelocity();
                if (timeSinceLastShot >= currentCooldownSec) {
                    timeSinceLastShot = 0.0f;

                    GameObject obj = player.RaycastShot(hndg.getDistantion());
                    if (obj instanceof NetPlayer target) {
                        javax.vecmath.Vector3f posb = target.getPosition();
                        javax.vecmath.Vector3f posa = player.getPosition();
                        posb.sub(posa);
                        posb.normalize();
                        MatchManager.getInstance().SendAttack(target.id, hndg.getCurrentWeaponIndex(), hndg.getDamage(),posb.x * 10.0f*dmgvel, posb.y * 10.0f*dmgvel, posb.z * 10.0f*dmgvel,0,0,0);
                    }else{
                        MatchManager.getInstance().SendAttack(-1, hndg.getCurrentWeaponIndex(), hndg.getDamage(),0, 0, 0,0,0,0);
                    }
                    player.MinusAmmo();
                }
            }
        }

        if (window.isKeyDown(GLFW.GLFW_KEY_D)) moveDirX += 1f;
        if (window.isKeyDown(GLFW.GLFW_KEY_A)) moveDirX -= 1f;
        if (window.isKeyDown(GLFW.GLFW_KEY_W)) moveDirZ += 1f;
        if (window.isKeyDown(GLFW.GLFW_KEY_S)) moveDirZ -= 1f;






        float length = (float) Math.sqrt(moveDirX * moveDirX + moveDirZ * moveDirZ);
        if (length > 0) {
            moveDirX /= length;
            moveDirZ /= length;
        }

        jumpPressed = window.isKeyDown(GLFW.GLFW_KEY_SPACE);
        dropPressed = window.isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL);

        player.processKeyboardInput(new Vector3f(moveDirX, 0f, moveDirZ), jumpPressed, dropPressed);

        if (dropPressed && player.IsDropDamage()) {
            Map<Integer, NetPlayer> netplr = MatchManager.getInstance().getNet_players();
            javax.vecmath.Vector3f selfPos = player.getPosition();

            player.DropAudioPlay();

            for (Map.Entry<Integer, NetPlayer> entry : netplr.entrySet()) {
                NetPlayer other = entry.getValue();
                if (other == null) continue;
                javax.vecmath.Vector3f otherPos = other.getPosition();
                if (otherPos == null) continue;

                float dx = otherPos.x - selfPos.x;
                float dy = otherPos.y - selfPos.y;
                float dz = otherPos.z - selfPos.z;
                float distSq = dx * dx + dy * dy + dz * dz;

                if (distSq <= 250) {
                    javax.vecmath.Vector3f posb = other.getPosition();
                    javax.vecmath.Vector3f posa = player.getPosition();
                    posb.sub(posa);
                    posb.normalize();
                    MatchManager.getInstance().SendAttack(other.id, -1,5, posb.x * 10.0f, posb.y * 100.0f, posb.z * 10.0f,0,0,0);
                }
            }
            MatchManager.getInstance().SendAttack(-1, -1,10, 0, 0, 0,0,0,0);
        }
    }

    private void render() {
        renderer.clear();
        renderer.beginFrame();
        for (GameObject go : Scene.getInstance().getGameObjects()) {
            renderer.submit(go);
        }
        renderer.flush();

        MatchManager.getInstance().RenderGUI(window.getWidth(), window.getHeight());
        renderer.renderset3d();
    }

    private void applySettings() {
        long glfwWindow = window.getWindowHandle();
        if (glfwWindow == 0) return;

        MainMenu.Settings settings = mainMenu.getSettings();
        AudioManager.setGlobalVolume(settings.masterVolume);
        // Применяем VSync
        glfwSwapInterval(settings.vsync ? 1 : 0);
        Time.getInstance().setTargetFPS(settings.fps);

        // Применяем полноэкранный режим и разрешение
        window.setVideoMode(settings.fullscreen, settings.width, settings.height);
        camera.resize(settings.width, settings.height);
    }


    private void cleanup() {
        if (mainMenu != null) {
            mainMenu.discardChanges(); // не сохраняем неприменённые правки при выходе из игры
            mainMenu.saveConfig(); // Сохраняем всё при выходе
            mainMenu.dispose();
        }
        if (gameClient != null) gameClient.stop();
        if (menuScene!= null) {
            Scene.getInstance().removeListGameObject(menuScene);
            menuScene.clear();
            menuScene = null;
        }
        if (menuCharacterMesh!= null) {
            Scene.getInstance().removeListGameObject(menuCharacterMesh);
            menuCharacterMesh.clear();
            menuCharacterMesh = null;
        }
        Scene.getInstance().clearScene();
        ResourceManager.dispose();
        ResourceCache.cleanup();
        AudioManager.cleanup();
        window.cleanup();
    }
}