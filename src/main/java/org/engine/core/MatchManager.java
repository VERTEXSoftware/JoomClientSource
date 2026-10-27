package org.engine.core;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import org.engine.audio.SoundBuffer;
import org.engine.audio.SoundSource;
import org.engine.network.*;
import org.engine.render.Camera;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.engine.scene.Scene;
import org.engine.scene.entity.HandGun;
import org.engine.scene.entity.NetPlayer;
import org.engine.scene.entity.Player;
import org.engine.scene.entity.SpawPoint;
import org.engine.utils.ModelLoader;
import org.engine.utils.ResourceCache;
import org.joml.Vector3f;
import io.netty.channel.ChannelHandlerContext;
import org.joml.Vector3fc;
import org.slvl.ManagerPack;

import javax.vecmath.Quat4f;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MatchManager {

    private static volatile MatchManager instance;

    private List<SoundBuffer> Musiclevel;
    private List<SoundBuffer> Musiclevelend;
    private SoundSource pointbackmusic;

    public int cur_id_player = -1;
    public int cur_model_player = 0;
    private GameHud gmhud;
    private Camera cur_cam;
    private Player cur_player=null;
    public String cur_name_player;
    private ChannelHandlerContext networkContext;
    private Random random;
    private final Map<Integer, NetPlayer> net_players = new ConcurrentHashMap<>();
    private final Map<Integer, PlayerSession> net_sessions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Object> incomingPackets = new ConcurrentLinkedQueue<>();

    // Состояние матча на клиенте
    private boolean matchInProgress = false;
    private float matchTimeLeft = 0f;
    private String currentMapName = "";
    private long hash=-1;

    public Map<Integer, NetPlayer> getNet_players() {
        return net_players;
    }

    public MatchManager() {
        instance = this; // Сохраняем ссылку на себя
        matchInProgress = false;

        try {
            File jarFile = new File(
                    MatchManager.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            hash = ManagerPack.crc64File(jarFile.getName());
        } catch (IOException e) {
            // throw new RuntimeException(e);
        } catch (URISyntaxException e) {
           // throw new RuntimeException(e);
        }
    }

    public static MatchManager getInstance() {
        if (instance == null) {
            synchronized (MatchManager.class) {
                if (instance == null) {
                    instance = new MatchManager();
                }
            }
        }
        return instance;
    }

    public void Init(Camera cam) {
        random = new Random();
        random.setSeed(System.nanoTime());
        matchInProgress = false;
        gmhud = new GameHud();
        try {
            gmhud.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.cur_cam = cam;
        this.cur_name_player = "Player";
        cur_model_player = 0;

        // Изначально матч не идет

        Musiclevel= new ArrayList<>();
        Musiclevelend= new ArrayList<>();


        pointbackmusic = new SoundSource();
    }

    public Player getLocalPlayer() { return cur_player; }

    public void CameraBasePosition(){
        if(cur_cam==null){return;}
        cur_cam.update();
        cur_cam.getTransform().origin.set(0, 10, 5);
        cur_cam.getTransform().setRotation(new Quat4f(0,0,0,1));
        cur_cam.update();
    }




    public void LoadScene(String mapName, String urlDownload, long crc64) {
        CameraBasePosition();

        this.currentMapName = mapName;
        gmhud.setNameMap(this.currentMapName);
        gmhud.setGuiMode(GameHud.TypeMode.LoadMap);

        // 1. Очистка старой сцены
        ClearScene();
        ResourceCache.cleanup();
        Musiclevel.clear();
        Musiclevelend.clear();

        Musiclevel.add(ResourceCache.getSound("sound/music/atmo01.ogg"));
        Musiclevel.add(ResourceCache.getSound("sound/music/atmo02.ogg"));
        Musiclevel.add(ResourceCache.getSound("sound/music/atmo03.ogg"));
        Musiclevel.add(ResourceCache.getSound("sound/music/atmo04.ogg"));

        Musiclevelend.add(ResourceCache.getSound("sound/music/finish01.ogg"));
        Musiclevelend.add(ResourceCache.getSound("sound/music/finish02.ogg"));

        // 2. Проверка наличия и целостности файла "maps/"+ mapName
        String mapPath = "maps/" + mapName;
        File mapFile = new File(mapPath);

        boolean needDownload = true;

        if (mapFile.exists()) {
            try {
                long actualCrc = ManagerPack.crc64File(mapPath);
                if (actualCrc == crc64) {
                    System.out.println("Файл карты найден и прошёл проверку CRC64.");
                    needDownload = false;
                } else {
                    System.out.println("CRC64 не совпадает (ожидалось " + crc64 +
                            ", получено " + actualCrc + "). Перезагрузка карты...");
                }
            } catch (IOException e) {
                System.err.println("Ошибка при чтении файла карты для проверки CRC64: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Файл карты не найден. Начинаем загрузку по URL: " + urlDownload);
        }

        if (needDownload) {
            try {
                // Создаем папки, если их еще не существует
                File parentDir = mapFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                // Выполняем загрузку файла по URL во временный файл,
                // чтобы не повредить существующую карту при обрыве соединения
                File tmpFile = new File(mapPath + ".tmp");
                URL url = new URL(urlDownload);
                try (InputStream in = url.openStream();
                     FileOutputStream out = new FileOutputStream(tmpFile)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                // Проверяем CRC64 загруженного файла
                long downloadedCrc = ManagerPack.crc64File(tmpFile.getPath());
                if (downloadedCrc != crc64) {
                    tmpFile.delete();
                    throw new IOException("CRC64 загруженного файла не совпадает (ожидалось " +
                            crc64 + ", получено " + downloadedCrc + ")");
                }

                // Атомарно заменяем старый файл новым
                if (mapFile.exists()) {
                    mapFile.delete();
                }
                if (!tmpFile.renameTo(mapFile)) {
                    throw new IOException("Не удалось переименовать временный файл карты");
                }

                System.out.println("Карта успешно загружена, проверена и сохранена в: " + mapPath);

            } catch (Exception e) {
                System.err.println("Ошибка при загрузке карты по URL: " + e.getMessage());
                e.printStackTrace();

                // --- ВОЗВРАТ В МЕНЮ И ОТКЛЮЧЕНИЕ ---

                if (networkContext != null) {
                    System.out.println("Отключение от сервера...");
                    networkContext.close();
                }

                clearLocalPlayer();
                gmhud.setGuiMode(GameHud.TypeMode.NoneRender);

                return;
            }
        }

        // 3. Загрузка объектов из файла
        List<GameObject> objects = ModelLoader.LoadLevel(mapPath);
        for (GameObject obj : objects) {
            Scene.getInstance().addGameObject(obj);
        }

        // 4. Карта загружена, отправляем серверу ClientReadyPacket
        SendReady();
    }

    // Очистка сцены перед загрузкой новой карты

    public void ClearSession(){
        this.net_sessions.clear();
        this.matchInProgress = false;
        this.networkContext = null;
    }
    public void ClearScene() {
        CameraBasePosition();

        pointbackmusic.stop();
        if (cur_player != null) {
            cur_player.Destroy();
            cur_player = null;
        }
        for (NetPlayer netPlayer : net_players.values()) {
            netPlayer.Destroy();
        }
        net_players.clear();
        // Если в Scene есть метод очистки геометрии карты, вызовите его здесь.
        Scene.getInstance().clearScene();
        Scene.getInstance().update(0);
    }

    public void Clear() {
        gmhud.dispose();
    }

    public void setNetworkContext(ChannelHandlerContext ctx) { this.networkContext = ctx; }
    public boolean isServerConnected() {
        return networkContext != null && networkContext.channel().isActive();
    }
    public void addIncomingPacket(Object packet) { incomingPackets.add(packet); }
    public void setLocalPlayerId(int id) { this.cur_id_player = id; }

    /**
     * Выбирает точку спавна, максимально удалённую от всех существующих игроков.
     * Если игроков нет — работает как раньше (случайный выбор).
     */
    private SpawPoint selectFarthestSpawnPoint(List<SpawPoint> spw) {
        // Собираем позиции всех "занятых" игроков: сетевых + локального
        List<Vector3f> occupied = new ArrayList<>();
        for (NetPlayer np : net_players.values()) {
            if (np != null && np.getTransform() != null) {
                javax.vecmath.Vector3f p = np.getTransform().origin;
                occupied.add(new Vector3f(p.x, p.y, p.z));
            }
        }
        if (cur_player != null && cur_player.getTransform() != null) {
            javax.vecmath.Vector3f p = cur_player.getTransform().origin;
            occupied.add(new Vector3f(p.x, p.y, p.z));
        }

        // Если никого нет — случайный выбор
        if (occupied.isEmpty()) {
            return spw.get(random.nextInt(spw.size()));
        }

        // Для каждой точки считаем минимальное расстояние до занятых позиций
        // и запоминаем точки с наибольшим "минимумом"
        final float NOISE_RANGE = 2.0f; // чтобы спавн не был всегда в одном месте
        float bestScore = -Float.MAX_VALUE;
        List<SpawPoint> bestPoints = new ArrayList<>();

        for (SpawPoint sp : spw) {
            javax.vecmath.Vector3f p = sp.getPosition();
            Vector3f spPos = new Vector3f(p.x, p.y, p.z);

            float minDistSq = Float.MAX_VALUE;
            for (Vector3f occ : occupied) {
                float d = spPos.distanceSquared(occ);
                if (d < minDistSq) minDistSq = d;
            }

            // Небольшой шум, чтобы при равных расстояниях выбор был случайным
            float score = minDistSq + random.nextFloat() * NOISE_RANGE;

            // Держим топ-N лучших (эпсилон для сравнения float)
            if (score > bestScore + 1e-3f) {
                bestScore = score;
                bestPoints.clear();
                bestPoints.add(sp);
            } else if (Math.abs(score - bestScore) <= 1e-3f) {
                bestPoints.add(sp);
            }
        }

        // Если несколько точек с одинаковым счётом — выбираем случайную из них
        return bestPoints.get(random.nextInt(bestPoints.size()));
    }

    private static long seedFromId(long id) {
        long z = id + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return z;
    }

    public void SpawnLocalPlayer(int id, String name) {
        this.cur_id_player = id;
        long salt = System.nanoTime();
        random = new Random(seedFromId(id) ^ salt);

        PlayerSession session = net_sessions.get(id);
        if (session != null) {
            session.id = id;
            session.health=100;
            session.name=name;
            session.model=cur_model_player;
            session.gunType=0;
        }else{
            net_sessions.put(id, new PlayerSession(id,cur_model_player, name));
        }

        gmhud.setGuiMode(GameHud.TypeMode.FPSPlayer);
        gmhud.setPlayername(name);
        if (cur_player != null) {
            cur_player.Destroy();
        }

        List<SpawPoint> spw = Scene.getInstance().getGameObjectsSpawnPoint();

        Vector3f postarget = new Vector3f(0, 0, 0);
        if (!spw.isEmpty()) {
            SpawPoint chosen = selectFarthestSpawnPoint(spw);
            javax.vecmath.Vector3f pbulle = chosen.getPosition();
            postarget.set(pbulle.x, pbulle.y, pbulle.z);
        }



        cur_player = new Player(id, cur_cam, postarget, new Vector3f(0, 0, 0), 70.0f);
        Scene.getInstance().addGameObject(cur_player);
    }

    public void SpawnNetPlayer(int id,int model, String name) {
        if (net_players.containsKey(id)) return;
        NetPlayer netPlayer = new NetPlayer(id,model, name, new Vector3f(0, 900, 0), new Vector3f(0, 0, 0), 70.0f);
        net_players.put(id, netPlayer);
        PlayerSession session = net_sessions.get(id);
        if (session != null) {
            session.id = id;
            session.health=100;
            session.name=name;
            session.model=model;
            session.gunType=0;
        }else{
            net_sessions.put(id, new PlayerSession(id,model, name));
            gmhud.addEvent("[white]" + name + "[/white] [green]ENTER[/green]");
        }

        Scene.getInstance().addGameObject(netPlayer);
    }

    public void DespawnNetPlayer(int id) {

        PlayerSession session = net_sessions.get(id);
        if(session!=null) {
            gmhud.addEvent("[white]" + session.name + "[/white] [orange]LEAVE[/orange]");

            NetPlayer netPlayer = net_players.remove(id);
            net_sessions.remove(id);
            if (netPlayer != null) {
                netPlayer.health = 0;
                netPlayer.Destroy();
            }
        }
    }

    public void clearLocalPlayer() {
        this.cur_id_player=-1;
        if(this.cur_player!=null){
            this.cur_player.Destroy();
            this.cur_player = null;
        }

        this.net_players.clear(); // если нужно очистить всех
    }
    // --- ОТСЫЛКА ДАННЫХ НА СЕРВЕР ---

    public void SendLogin(String name) {
        this.cur_name_player = name;
        if (networkContext != null) { networkContext.writeAndFlush(new LoginPacket(name,cur_model_player,hash)); }
    }

    // Отправка готовности после загрузки карты
    public void SendReady() {
        gmhud.setGuiMode(GameHud.TypeMode.WaitingMatch);
        if (networkContext != null) {
            System.out.println("Карта загружена. Отправка ClientReadyPacket...");
            networkContext.writeAndFlush(new ClientReadyPacket());
        }
    }

    public void SendData() {
        // Не отправляем данные, если матч не идет (мы в лобби или на экране статистики)
        if (!matchInProgress) return;

        if (cur_player == null || networkContext == null || cur_id_player == -1) return;
        Transform bulletTransform = cur_player.getTransform();
        javax.vecmath.Vector3f bulletPos = bulletTransform.origin;
        bulletTransform = cur_player.getCamera().getTransform();
        Quat4f bulletRot = new Quat4f();
        bulletTransform.getRotation(bulletRot);
        float rotY = (float) Math.atan2(2.0f * (bulletRot.y * bulletRot.w + bulletRot.x * bulletRot.z), 1.0f - 2.0f * (bulletRot.y * bulletRot.y + bulletRot.x * bulletRot.x));
        float yawDegrees = (float) Math.toDegrees(rotY);
        // ИЗМЕНЕНО: health в пакете — только справочная информация, сервер его игнорирует
        // (здоровье игроков хранится на сервере и рассылается обратно в state-пакетах).
        int health = cur_player.getHealth();
        int gunType = cur_player.getCurrentWeaponIndex();
        PlayerStatePacket packet = new PlayerStatePacket(cur_id_player, bulletPos.x, bulletPos.y, bulletPos.z, yawDegrees, health, gunType);
        networkContext.writeAndFlush(packet);
    }

    public void SendAttack(int idEnemy,int type, int damage, float x, float y, float z,float px, float py, float pz) {
        if (!matchInProgress) return;
        if (networkContext != null && cur_id_player != -1 && cur_id_player != idEnemy) {
            networkContext.writeAndFlush(new PlayerAttackPacket(cur_id_player, idEnemy,type, damage,x,y,z,px,py,pz));
        }
    }

    public void CurretPlayerRandomPickUp(){
        if(cur_player == null) {
            return;
        }

        // Получаем текущее оружие игрока
        HandGun hndg = cur_player.getWeaponsClass();
        HandGun.TypeGun currentGun = (hndg != null) ? hndg.getActiveWeapon() : null;

        // Собираем список кандидатов на выпадение
        List<HandGun.TypeGun> candidates = new ArrayList<>();
        for (HandGun.TypeGun gun : HandGun.TypeGun.values()) {
            // Пропускаем базовое оружие (HandGuy) и текущее активное оружие
            if (gun == HandGun.TypeGun.HandGuy) continue;
            if (gun == currentGun) continue;

            candidates.add(gun);
        }

        // Если список пуст (например, у игрока только базовое оружие, а других видов в игре нет),
        // просто прерываем выполнение
        if (candidates.isEmpty()) {
            return;
        }

        Random random = new Random();
        // Выбираем случайный индекс из списка доступных оружий
        int randomIndex = random.nextInt(candidates.size());
        HandGun.TypeGun randomGun = candidates.get(randomIndex);

        // Вызываем метод подбора у игрока, передавая ему новое оружие
        cur_player.PickUpWeapon(randomGun);


    }
    public void SendDeath() {
        if (!matchInProgress) return;
        if (networkContext != null && cur_id_player != -1 && cur_player != null) {
            networkContext.writeAndFlush(new PlayerDeathPacket(cur_id_player, -1));
        }
    }

    // --- ЧТЕНИЕ И ОБРАБОТКА ДАННЫХ ОТ СЕРВЕРА ---

    public void ReadData() {
        // Обрабатываем не больше 10 пакетов за один кадр (60 FPS = 600 пакетов в сек)
        while (!incomingPackets.isEmpty()) {
            Object packet = incomingPackets.poll();

            if (packet instanceof LoginResponsePacket) {
                handleLoginResponse((LoginResponsePacket) packet);
            } else if (packet instanceof SpawnPacket) {
                SpawnNetPlayer(((SpawnPacket) packet).id,((SpawnPacket) packet).model, ((SpawnPacket) packet).name);
            } else if (packet instanceof DespawnPacket) {
                DespawnNetPlayer(((DespawnPacket) packet).id);
            } else if (packet instanceof PlayerStatePacket) {
                handlePlayerState((PlayerStatePacket) packet);
            } else if (packet instanceof PlayerAttackPacket) {
                handlePlayerAttack((PlayerAttackPacket) packet);
            } else if (packet instanceof PlayerDeathPacket) {
                handlePlayerDeath((PlayerDeathPacket) packet);
            } else if (packet instanceof PlayerRespawnPacket) {
                handlePlayerRespawn((PlayerRespawnPacket) packet);
            } else if (packet instanceof MapInfoPacket) {
                handleMapInfo((MapInfoPacket) packet);
            } else if (packet instanceof MatchStartPacket) {
                handleMatchStart((MatchStartPacket) packet);
            } else if (packet instanceof MatchEndPacket) {
                handleMatchEnd();
            } else if (packet instanceof PlayerStatsPacket) {
                handlePlayerStats((PlayerStatsPacket) packet);
            }
        }
    }

    private void handleLoginResponse(LoginResponsePacket p) {
        System.out.println("Получен ID от сервера: " + p.id);
        random.setSeed(System.nanoTime()+ p.id);
        // Локальный игрок пока не спавнится. Ждем начала матча (MatchStartPacket).
        // Мы только запоминаем ID.
        this.cur_id_player = p.id;
        this.cur_name_player = p.name;
        gmhud.setPlayername(p.name);
        gmhud.setGuiMode(GameHud.TypeMode.NoneRender);
    }

    // Обработка инфы о карте
    private void handleMapInfo(MapInfoPacket p) {
        System.out.println("Сервер прислал карту: " + p.mapName + " | URL: " + p.urlDownload);
        // Запускаем загрузку/скачивание карты
        LoadScene(p.mapName, p.urlDownload,p.hashcrc64);

    }

    // Начало матча
    private void handleMatchStart(MatchStartPacket p) {
        pointbackmusic.setAmbient(true);
        pointbackmusic.setVolume(0.2f);
        pointbackmusic.playRnd( Musiclevel,true);
        Scene.getInstance().update(0);
        matchInProgress = true;
        gmhud.setTimeRemaining(p.timeSeconds);
        System.out.println("Матч начался! Осталось секунд: " + p.timeSeconds);

        // Спавним локального игрока (теперь точно можно)
        if (cur_player == null) {
            SpawnLocalPlayer(cur_id_player, cur_name_player);
        }

        // Переключаем HUD в боевой режим
        gmhud.setGuiMode(GameHud.TypeMode.FPSPlayer);
    }

    // Конец матча
    private void handleMatchEnd() {
        pointbackmusic.stop();
        pointbackmusic.playRnd(Musiclevelend,false);
        matchInProgress = false;
        System.out.println("Матч окончен!");

        // Удаляем локального игрока (замораживаем игру)
        if (cur_player != null) {

            cur_player.health = 0;
            cur_player.Destroy();
            cur_player = null;
        }
        // Удаляем сетевых игроков (опционально, чтобы очистить сцену до следующей карты)
        for (NetPlayer netPlayer : net_players.values()) {
            netPlayer.health = 0;
            netPlayer.Destroy();
        }
        net_players.clear();

        // Показываем экран статистики в HUD (вам нужно добавить этот режим в GameHud)
        gmhud.setGuiMode(GameHud.TypeMode.EndMatch);
    }

    // Прием статистики от сервера
    private void handlePlayerStats(PlayerStatsPacket p) {
        System.out.println("STATS: " + p.name + " | K: " + p.kills + " | D: " + p.deaths);
        // Передаем данные в HUD для отрисовки таблицы лидеров
        gmhud.AddLiaderTable(p.id, p.name, p.kills, p.deaths);
    }


    private void handlePlayerDeath(PlayerDeathPacket p) {
        PlayerSession a = net_sessions.get(p.id);
        PlayerSession b = net_sessions.get(p.killerId);

        String namea = (a==null)?"":a.name;
        String nameb = (b==null)?"":b.name;

        if(p.killerId==-1){
            gmhud.addEvent("[white]" + namea + "[/white] [red]DEATH[/red]");
        }else{
            gmhud.addEvent("[white]" + nameb + "[/white] [red]KILL[/red] [white]" + namea + "[/white]");
        }

        if (p.id == cur_id_player) {
            gmhud.setGuiMode(GameHud.TypeMode.DeadPlayer);
            if (cur_player != null) {
                // cur_player.setDamage(1000);
                cur_player.Destroy();
                cur_player = null;
            }
            System.out.println("Локальный игрок погиб. Ожидание респавна...");
        } else {
            NetPlayer netPlayer = net_players.get(p.id);
            if (netPlayer != null) {
                netPlayer.health=0;
                net_players.remove(p.id);
                netPlayer.Destroy();
            }
        }
    }

    public void RenderGUI(int windowWidth,int windowHeight){
        if(cur_player!=null) {
            gmhud.setHealth(cur_player.health);
            gmhud.setAmmo(cur_player.getAmmo());
        }else{
            gmhud.setHealth(0);
            gmhud.setAmmo(0);
        }
        gmhud.render(windowWidth, windowHeight);
    }

    private void handlePlayerRespawn(PlayerRespawnPacket p) {
        PlayerSession session = net_sessions.get(p.id);

        if(session!=null){
            session.health=100;
            session.name=p.name;
            session.model=p.model;
            gmhud.addEvent("[white]" + p.name + "[/white] [cyan]RESPAWN[/cyan]");
        }

        if (p.id == cur_id_player) {
            SpawnLocalPlayer(p.id, p.name);
        } else {

            NetPlayer netPlayer = net_players.get(p.id);
            if (netPlayer != null) {

                net_players.remove(p.id);
                netPlayer.Destroy();
                SpawnNetPlayer(p.id, p.model, p.name);
            } else {

                SpawnNetPlayer(p.id, p.model, p.name);
            }
        }
    }

    private void handlePlayerState(PlayerStatePacket p) {
        // ИЗМЕНЕНО: здоровье игроков хранит СЕРВЕР и рассылает его в state-пакетах
        // (в т.ч. самому игроку). Если пакет про нашего локального игрока —
        // применяем ТОЛЬКО серверное здоровье, позицию не трогаем.
        if (p.id == cur_id_player) {
            if (cur_player != null && p.health > 0) {
                cur_player.health = p.health;
            }
            PlayerSession session = net_sessions.get(p.id);
            if (session != null && p.health > 0) {
                session.health = p.health;
            }
            return;
        }

        NetPlayer netPlayer = net_players.get(p.id);
        if (netPlayer != null) {
            netPlayer.applyNetworkState(p.x, p.y, p.z, p.rotY, p.health,p.gunType);
            PlayerSession session = net_sessions.get(p.id);
            if (session != null) {
                session.health = p.health;
                session.gunType = p.gunType;
            }
        }
    }

    private void handlePlayerAttack(PlayerAttackPacket p) {

        if(cur_id_player!=p.id){
            NetPlayer target = net_players.get(p.id);
            if(target!=null){
                if(p.type==-1) {
                    target.AttackDrop();
                }else{
                    target.AttackAnim();
                }
            }
        }

        if (p.idEnemy == cur_id_player) {
            if (cur_player != null) {
                // ИЗМЕНЕНО: урон не применяем локально — серверное здоровье придет
                // в ближайшем state-пакете (до 50 мс). Здесь только отброс от попадания.

                RigidBody rggi = cur_player.getRigidBody();
                if(rggi!=null) {
                    javax.vecmath.Vector3f linearVelocity = new javax.vecmath.Vector3f() ;
                    javax.vecmath.Vector3f linearVelocityattack = new javax.vecmath.Vector3f(p.aX,p.aY,p.aZ) ;
                    rggi.getLinearVelocity(linearVelocity);
                    linearVelocity.add(linearVelocityattack);

                    final float MAX = 60f;
                    linearVelocity.x = Math.max(-MAX, Math.min(MAX, linearVelocity.x));
                    linearVelocity.y = Math.max(-MAX, Math.min(MAX, linearVelocity.y));
                    linearVelocity.z = Math.max(-MAX, Math.min(MAX, linearVelocity.z));

                    rggi.setLinearVelocity(linearVelocity);
                }
            }
        } else {
            NetPlayer target = net_players.get(p.idEnemy);
            if (target != null) {
                target.setDamage(p.damage);
            }
        }
    }
}
