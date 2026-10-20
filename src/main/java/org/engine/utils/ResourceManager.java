package org.engine.utils;

import org.slvl.ManagerPack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;

public class ResourceManager {

    private static ManagerPack.NodeTable globalPack = null; // Для data.slvl
    private static ManagerPack.NodeTable levelPack = null;  // Для текущего уровня (wad)

    /**
     * Инициализация глобального архива (data.slvl). Вызывать при старте движка.
     */
    public static void initGlobalPack(String slvlPath) {
        if (slvlPath != null && !slvlPath.isEmpty()) {
            // Закрываем старый пак, если он был открыт
            if (globalPack != null) {
                globalPack.close();
            }

            globalPack = new ManagerPack.NodeTable();

            if (!ManagerPack.openPack(globalPack, slvlPath)) {
                System.err.println("Не удалось открыть глобальный архив: " + slvlPath);
                globalPack = null;
            } else {
                System.out.println("[ResourceManager] Глобальный архив загружен: " + slvlPath
                        + " (Файлов: " + globalPack.getCount() + ", mmap: " + globalPack.isMapped() + ")");
            }
        }
    }

    /**
     * Загрузка архива уровня. Вызывать перед загрузкой объектов уровня.
     */
    public static void setLevelPack(String slvlPath) {
        // Сначала выгружаем предыдущий пак уровня
        unloadLevelPack();

        if (slvlPath != null && !slvlPath.isEmpty()) {
            levelPack = new ManagerPack.NodeTable();
            if (!ManagerPack.openPack(levelPack, slvlPath)) {
                System.err.println("Не удалось открыть архив уровня: " + slvlPath);
                levelPack = null;
            } else {
                System.out.println("[ResourceManager] Архив уровня загружен: " + slvlPath
                        + " (Файлов: " + levelPack.getCount() + ", mmap: " + levelPack.isMapped() + ")");
            }
        }
    }

    /**
     * Выгрузить архив текущего уровня (очистить память и закрыть файл).
     */
    public static void unloadLevelPack() {
        if (levelPack != null) {
            levelPack.close(); // закрываем файл и освобождаем mmap
            levelPack = null;
            System.out.println("[ResourceManager] Архив уровня выгружен из памяти.");
        }
    }

    /**
     * Полная очистка и закрытие менеджера ресурсов (при закрытии игры).
     */
    public static void dispose() {
        if (globalPack != null) {
            globalPack.close(); // закрываем файл и освобождаем mmap
            globalPack = null;
        }
        unloadLevelPack();
        System.out.println("[ResourceManager] Все архивы выгружены, менеджер очищен.");
    }

    /**
     * Нормализация пути: префиксы file:/, обратные слеши, ведущие слеши.
     */
    public static String cleanPath(String path) {
        if (path == null || path.isEmpty()) return "";
        String p = path.replace('\\', '/');
        if (p.startsWith("file:///")) p = p.substring(7);
        else if (p.startsWith("file://")) p = p.substring(6);
        else if (p.startsWith("file:/")) p = p.substring(5);
        else if (p.startsWith("file:")) p = p.substring(5);
        if (!p.isEmpty() && p.charAt(0) == '/') p = p.substring(1);
        return p;
    }

    /**
     * Получает ресурс с каскадным поиском:
     * 1. В архиве уровня (filePathFromSLVL)
     * 2. В data.slvl
     * 3. В ресурсах JAR
     *
     * ВНИМАНИЕ (zero-copy): из пака буфер возвращается как read-only срез mmap —
     * его нельзя изменять (put() бросит ReadOnlyBufferException) и нельзя использовать
     * после close() соответствующего пака. Из JAR возвращается обычная изменяемая копия.
     * Для изменяемой копии из пака: ManagerPack.loadFileFromMemoryCopy(...)
     */
    public static ByteBuffer getResource(String path) {
        if (path == null || path.isEmpty()) return null;

        String cleanPath = cleanPath(path);

        // 1. Поиск в архиве уровня
        if (levelPack != null) {
            ByteBuffer buffer = ManagerPack.loadFileFromMemory(levelPack, cleanPath);
            if (buffer != null) return buffer;
        }

        // 2. Поиск в data.slvl
        if (globalPack != null) {
            ByteBuffer buffer = ManagerPack.loadFileFromMemory(globalPack, cleanPath);
            if (buffer != null) return buffer;
        }

        // 3. Поиск в ресурсах JAR (пробуем все возможные ClassLoader'ы)
        InputStream is = null;

        try {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(cleanPath);
        } catch (Exception ignored) {}

        if (is == null) {
            try {
                is = ResourceManager.class.getClassLoader().getResourceAsStream(cleanPath);
            } catch (Exception ignored) {}
        }

        if (is == null) {
            try {
                is = ClassLoader.getSystemResourceAsStream(cleanPath);
            } catch (Exception ignored) {}
        }

        if (is == null) {
            try {
                is = ResourceManager.class.getResourceAsStream("/" + cleanPath);
            } catch (Exception ignored) {}
        }

        // Если хоть один способ сработал
        if (is != null) {
            try {
                byte[] bytes = is.readAllBytes();
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(bytes);
                buffer.flip();
                return buffer;
            } catch (IOException e) {
                System.err.println("Ошибка чтения ресурса из JAR: " + cleanPath);
            } finally {
                try { is.close(); } catch (IOException ignored) {}
            }
        }

        System.err.println("[ResourceManager] Ресурс не найден нигде: " + cleanPath);
        return null;
    }

    /**
     * Параллельный прогрев списка ресурсов в страничном кэше ОС.
     * Вызывать ПЕРЕД массовой загрузкой уровня — убирает штраф на холодные
     * страницы диска. Не создаёт GL-объектов, потокобезопасно.
     *
     * Пример:
     *   ResourceManager.setLevelPack(path);
     *   ResourceManager.preloadResources(ManagerPack.getNodeTable(levelPack));
     *   // далее — обычная загрузка объектов уровня
     */
    public static void preloadResources(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) return;
        paths.parallelStream().forEach(p -> {
            String c = cleanPath(p);
            if (levelPack != null)  ManagerPack.warmUp(levelPack, c);
            if (globalPack != null) ManagerPack.warmUp(globalPack, c);
        });
    }

    /**
     * Поиск ресурса, а если не найден — возврат файла ошибки
     */
    public static ByteBuffer getResourceOrError(String path, String errorResourcePath) {
        ByteBuffer res = getResource(path);
        if (res == null) {
            System.err.println("Ресурс не найден: " + path + ". Загружается файл по умолчанию: " + errorResourcePath);
            res = getResource(errorResourcePath);
        }
        return res;
    }
}