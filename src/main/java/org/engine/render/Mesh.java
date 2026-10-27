package org.engine.render;

import org.engine.scene.AABB;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class Mesh {
    private final int vaoId;
    private final int vertexCount;
    private final List<Integer> vboIdList;
    private final int iboId;

    private final float[] positions;
    private final int[] indices;

    // Локальный AABB
    private final AABB localAABB;
    private boolean useAABB;

    public boolean IsUseAABB(){
        return useAABB;
    }



    public Mesh(float[] positions, float[] texCoords, int[] indices, boolean useAABB) {
        this.vertexCount = indices.length;
        this.vboIdList = new ArrayList<>();
        this.useAABB = useAABB;
        this.positions = positions;
        this.indices = indices;

        // Вычисляем локальный AABB на основе вершин
        this.localAABB = new AABB();
        for (int i = 0; i < positions.length; i += 3) {
            this.localAABB.expand(positions[i], positions[i + 1], positions[i + 2]);
        }


        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Position VBO
        int posVboId = GL15.glGenBuffers();
        vboIdList.add(posVboId);

        FloatBuffer posBuffer = MemoryUtil.memCallocFloat(positions.length);
        posBuffer.put(positions).flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, posVboId);
        GL15.glBufferData(
                GL15.GL_ARRAY_BUFFER,
                posBuffer,
                GL15.GL_STATIC_DRAW
        );

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(
                0,
                3,
                GL11.GL_FLOAT,
                false,
                0,
                0
        );

        // Texture coordinates
        int texVboId = GL15.glGenBuffers();
        vboIdList.add(texVboId);

        FloatBuffer texBuffer = MemoryUtil.memCallocFloat(texCoords.length);
        texBuffer.put(texCoords).flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, texVboId);
        GL15.glBufferData(
                GL15.GL_ARRAY_BUFFER,
                texBuffer,
                GL15.GL_STATIC_DRAW
        );

        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(
                1,
                2,
                GL11.GL_FLOAT,
                false,
                0,
                0
        );

        // Index buffer
        iboId = GL15.glGenBuffers();

        IntBuffer indicesBuffer =
                MemoryUtil.memCallocInt(indices.length);

        indicesBuffer.put(indices).flip();

        GL15.glBindBuffer(
                GL15.GL_ELEMENT_ARRAY_BUFFER,
                iboId
        );

        GL15.glBufferData(
                GL15.GL_ELEMENT_ARRAY_BUFFER,
                indicesBuffer,
                GL15.GL_STATIC_DRAW
        );

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

        MemoryUtil.memFree(posBuffer);
        MemoryUtil.memFree(texBuffer);
        MemoryUtil.memFree(indicesBuffer);
    }

    public void render() {
        GL30.glBindVertexArray(vaoId);

        GL11.glDrawElements(
                GL11.GL_TRIANGLES,
                vertexCount,
                GL11.GL_UNSIGNED_INT,
                0
        );

        GL30.glBindVertexArray(0);
    }

    public void cleanup() {
        for (int vboId : vboIdList) {
            GL15.glDeleteBuffers(vboId);
        }

        GL15.glDeleteBuffers(iboId);
        GL30.glDeleteVertexArrays(vaoId);
    }

    public float[] getVertices() {
        return positions;
    }

    public int[] getIndices() {
        return indices;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    // Геттер для локального AABB
    public AABB getLocalAABB() {
        return localAABB;
    }
}