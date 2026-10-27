package org.engine.utils;

import org.engine.render.Mesh;
import org.engine.render.Shader;
import org.engine.render.Texture;
import org.engine.scene.GameObject;
import org.engine.scene.entity.SpawPoint;
import org.engine.scene.entity.Trigger;
import org.engine.scene.entity.WeaponPickupTrigger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.assimp.AIString;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class ModelLoader {

    public static List<GameObject> LoadLevel(String filePathFromSLVL) {
        // 1. Указываем ResourceManager, какой пак использовать для этого уровня
        ResourceManager.setLevelPack(filePathFromSLVL);

        // 2. Инициализируем шейдеры через глобальный кеш
        Shader sceneShader = ResourceCache.getShader("shader/vertex.glsl", "shader/fragment.glsl");

        // 3. Настройки трансформации уровня
        float mass = 0.0f;
        boolean collision = true;
        Vector3f pos = new Vector3f(0, 0, 0);
        Vector3f rot = new Vector3f(0, 0, 0);
        Vector3f scale = new Vector3f(20, 20, 20);

        // 4. Загрузка карты "scene.dae"
        List<GameObject> levelObjects = ModelLoader.load(
                "scene.dae",
                sceneShader,
                mass,
                collision,
                pos,
                rot,
                scale,true
        );

        return levelObjects;
    }

    public static List<GameObject> load(String filePath, Shader shader, float mass, boolean phys, Vector3f startPosition, Vector3f startRotation, Vector3f startScale,boolean useaabb) {
        int flags = Assimp.aiProcess_Triangulate | Assimp.aiProcess_FlipUVs | Assimp.aiProcess_JoinIdenticalVertices;

        String extension = "";
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = filePath.substring(dotIndex);
        }

        ByteBuffer modelBuffer = ResourceManager.getResourceOrError(filePath, "error.dae");
        if (modelBuffer == null) {
            throw new RuntimeException("Ошибка загрузки модели: " + filePath + " - файл не найден, а error.dae отсутствует в ресурсах.");
        }

        AIScene scene = Assimp.aiImportFileFromMemory(modelBuffer, flags, extension);

        if (scene == null) {
            throw new RuntimeException("Ошибка загрузки модели: " + filePath + " - " + Assimp.aiGetErrorString());
        }

        if (scene.mRootNode() == null) {
            Assimp.aiReleaseImport(scene);
            throw new RuntimeException("В модели нет корневого узла: " + filePath);
        }

        List<GameObject> gameObjects = new ArrayList<>();

        // Передаем filePath для формирования ключей кеша мешей
        processNode(filePath, scene, scene.mRootNode(), new Matrix4f(), phys, shader, mass, startPosition, startRotation, startScale, gameObjects,useaabb);

        Assimp.aiReleaseImport(scene);
        return gameObjects;
    }

    public static Mesh loadMesh(String filePath) {
        // Ключ для одиночной загрузки меша
        String singleMeshKey = filePath + "::SINGLE_MESH_LOAD";
        Mesh cachedMesh = ResourceCache.getMesh(singleMeshKey);
        if (cachedMesh != null) {
            return cachedMesh;
        }

        int flags = Assimp.aiProcess_Triangulate | Assimp.aiProcess_FlipUVs | Assimp.aiProcess_JoinIdenticalVertices;

        String extension = "";
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = filePath.substring(dotIndex);
        }

        ByteBuffer modelBuffer = ResourceManager.getResourceOrError(filePath, "error.dae");
        if (modelBuffer == null) {
            throw new RuntimeException("Ошибка загрузки меша: " + filePath + " - файл не найден, а error.dae отсутствует в ресурсах.");
        }

        AIScene scene = Assimp.aiImportFileFromMemory(modelBuffer, flags, extension);

        if (scene == null) {
            throw new RuntimeException("Ошибка загрузки меша: " + filePath + " - " + Assimp.aiGetErrorString());
        }

        if (scene.mNumMeshes() <= 0) {
            Assimp.aiReleaseImport(scene);
            throw new RuntimeException("В файле не найдено мешей: " + filePath);
        }

        PointerBuffer meshBuffer = scene.mMeshes();
        AIMesh aiMesh = AIMesh.create(meshBuffer.get(0));

        Matrix4f transform = new Matrix4f();
        if (scene.mRootNode() != null) {
            transform = toJomlMatrix(scene.mRootNode().mTransformation());
        }

        float[] positions = extractPositions(aiMesh, transform);
        float[] texCoords = extractTexCoords(aiMesh);
        int[] indices = extractIndices(aiMesh);

        Mesh mesh = new Mesh(positions, texCoords, indices, true);

        // Сохраняем в кеш
        ResourceCache.putMesh(singleMeshKey, mesh);

        Assimp.aiReleaseImport(scene);
        return mesh;
    }

    private static void processNode(String filePath, AIScene scene, AINode node, Matrix4f parentTransform, boolean phys, Shader shader, float mass, Vector3f startPosition, Vector3f startRotation, Vector3f startScale, List<GameObject> gameObjects,boolean useaabb) {
        if (node == null) return;

        Matrix4f nodeTransform = toJomlMatrix(node.mTransformation());
        Matrix4f globalTransform = new Matrix4f(parentTransform).mul(nodeTransform);

        String nodeName = node.mName().dataString();

        int numMeshes = node.mNumMeshes();
        if (numMeshes > 0) {
            PointerBuffer meshBuffer = scene.mMeshes();
            IntBuffer meshIndices = node.mMeshes();

            for (int i = 0; i < numMeshes; i++) {
                int meshIndex = meshIndices.get(i);
                AIMesh aiMesh = AIMesh.create(meshBuffer.get(meshIndex));

                // Формируем уникальный ключ для кеша меша:
                // Путь к файлу + Имя узла + Индекс меша + Хеш матрицы трансформации (т.к. вершины запекаются)
                String meshKey = filePath + "::" + nodeName + "::" + meshIndex + "::" + globalTransform.hashCode();

                Mesh mesh = ResourceCache.getMesh(meshKey);
                if (mesh == null) {
                    float[] positions = extractPositions(aiMesh, globalTransform);
                    float[] texCoords = extractTexCoords(aiMesh);
                    int[] indices = extractIndices(aiMesh);
                    mesh = new Mesh(positions, texCoords, indices, useaabb);
                    ResourceCache.putMesh(meshKey, mesh);
                }

                // Получаем текстуру через глобальный кеш
                Texture texture = getTextureForMesh(scene, aiMesh);

                Vector3f meshPosition = new Vector3f(
                        globalTransform.m30() * startScale.x,
                        globalTransform.m31() * startScale.y,
                        globalTransform.m32() * startScale.z
                );

                boolean notvisible = nodeName.contains("NoDraw");
                Shader shdtmp = notvisible ? null : shader;

                if (nodeName.contains("TriggerKill")) {
                    Trigger trg = new Trigger("TriggerKill", mesh, startPosition, startRotation, startScale, Trigger.TypeTrigger.PlayerKill);
                    trg.SetIgnoreRaycast(true);
                    gameObjects.add(trg);
                } else if (nodeName.contains("SpawnPlayer")) {
                    gameObjects.add(new SpawPoint("SpawnPlayer", meshPosition));
                } else if (nodeName.contains("SpawnWeapon")) {
                    WeaponPickupTrigger trg = new WeaponPickupTrigger("SpawnWeapon", mesh, startPosition, meshPosition, startRotation, startScale);
                    trg.SetIgnoreRaycast(true);
                    gameObjects.add(trg);
                } else if (nodeName.contains("NoCollision")) {
                    GameObject gmb = new GameObject(0, "noc_struct", mesh, shdtmp, texture, false, 0.0f, startPosition, startRotation, startScale);
                    gmb.SetVisible(!notvisible);
                    gameObjects.add(gmb);
                } else {
                    GameObject gmb = new GameObject(0, "struct", mesh, shdtmp, texture, phys, mass, startPosition, startRotation, startScale);
                    gmb.SetVisible(!notvisible);
                    gameObjects.add(gmb);
                }
            }
        }

        int numChildren = node.mNumChildren();
        PointerBuffer children = node.mChildren();
        for (int i = 0; i < numChildren; i++) {
            AINode child = AINode.create(children.get(i));
            processNode(filePath, scene, child, globalTransform, phys, shader, mass, startPosition, startRotation, startScale, gameObjects,useaabb);
        }
    }

    private static Matrix4f toJomlMatrix(AIMatrix4x4 aiMatrix) {
        Matrix4f mat = new Matrix4f();
        mat.m00(aiMatrix.a1()); mat.m10(aiMatrix.a2()); mat.m20(aiMatrix.a3()); mat.m30(aiMatrix.a4());
        mat.m01(aiMatrix.b1()); mat.m11(aiMatrix.b2()); mat.m21(aiMatrix.b3()); mat.m31(aiMatrix.b4());
        mat.m02(aiMatrix.c1()); mat.m12(aiMatrix.c2()); mat.m22(aiMatrix.c3()); mat.m32(aiMatrix.c4());
        mat.m03(aiMatrix.d1()); mat.m13(aiMatrix.d2()); mat.m23(aiMatrix.d3()); mat.m33(aiMatrix.d4());
        return mat;
    }

    private static Texture getTextureForMesh(AIScene scene, AIMesh aiMesh) {
        int materialIndex = aiMesh.mMaterialIndex();
        if (materialIndex < 0 || materialIndex >= scene.mNumMaterials()) {
            return null;
        }

        PointerBuffer materialBuffer = scene.mMaterials();
        AIMaterial material = AIMaterial.create(materialBuffer.get(materialIndex));

        AIString path = AIString.calloc();
        try {
            int result = Assimp.aiGetMaterialTexture(material, Assimp.aiTextureType_DIFFUSE, 0, path, (IntBuffer) null, null, null, null, null, null);

            if (result == Assimp.aiReturn_SUCCESS) {
                String texturePath = path.dataString();

                if (texturePath != null && !texturePath.isEmpty()) {
                    // Очистка пути
                    if (texturePath.startsWith("file:///")) texturePath = texturePath.substring(7);
                    else if (texturePath.startsWith("file://")) texturePath = texturePath.substring(6);
                    else if (texturePath.startsWith("file:/")) texturePath = texturePath.substring(5);
                    else if (texturePath.startsWith("file:")) texturePath = texturePath.substring(5);

                    if (texturePath.startsWith("/") || texturePath.startsWith("\\")) {
                        texturePath = texturePath.substring(1);
                    }

                    // Обращение к глобальному кешу текстур
                    return ResourceCache.getTexture(texturePath);
                }
            }
        } finally {
            path.free();
        }

        return null;
    }

    private static float[] extractPositions(AIMesh aiMesh, Matrix4f transform) {
        int count = aiMesh.mNumVertices();
        AIVector3D.Buffer buffer = aiMesh.mVertices();
        float[] positions = new float[count * 3];

        Vector3f pos = new Vector3f();
        for (int i = 0; i < count; i++) {
            AIVector3D vec = buffer.get(i);
            pos.set(vec.x(), vec.y(), vec.z());

            transform.transformPosition(pos);

            positions[i * 3] = pos.x();
            positions[i * 3 + 1] = pos.y();
            positions[i * 3 + 2] = pos.z();
        }
        return positions;
    }

    private static float[] extractTexCoords(AIMesh aiMesh) {
        int count = aiMesh.mNumVertices();
        if (aiMesh.mTextureCoords(0) == null) {
            return new float[count * 2];
        }

        AIVector3D.Buffer buffer = aiMesh.mTextureCoords(0);
        float[] texCoords = new float[count * 2];

        for (int i = 0; i < count; i++) {
            AIVector3D vec = buffer.get(i);
            texCoords[i * 2] = vec.x();
            texCoords[i * 2 + 1] = vec.y();
        }
        return texCoords;
    }

    private static int[] extractIndices(AIMesh aiMesh) {
        int faceCount = aiMesh.mNumFaces();
        AIFace.Buffer faceBuffer = aiMesh.mFaces();

        int[] indices = new int[faceCount * 3];

        for (int i = 0; i < faceCount; i++) {
            AIFace face = faceBuffer.get(i);
            IntBuffer idxBuffer = face.mIndices();

            int idxPos = i * 3;
            int numIndices = Math.min(face.mNumIndices(), 3);

            for (int j = 0; j < numIndices; j++) {
                indices[idxPos + j] = idxBuffer.get(j);
            }
        }
        return indices;
    }
}