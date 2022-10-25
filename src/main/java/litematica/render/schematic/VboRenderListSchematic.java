package litematica.render.schematic;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockRenderLayer;

import litematica.render.schematic.RenderChunkSchematicVbo.OverlayRenderType;

public class VboRenderListSchematic extends ChunkRenderContainerSchematic
{
    @Override
    public void renderChunkLayer(BlockRenderLayer layer)
    {
        if (this.initialized)
        {
            for (RenderChunk renderChunk : this.renderChunks)
            {
                this.renderBlocks(renderChunk.getVertexBufferByLayer(layer.ordinal()), renderChunk);
            }

            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            GlStateManager.resetColor();

            this.renderChunks.clear();
        }
    }

    @Override
    public void renderBlockOverlays(OverlayRenderType type)
    {
        if (this.initialized)
        {
            for (RenderChunkSchematicVbo renderChunk : this.overlayRenderChunks)
            {
                this.renderOverlay(renderChunk.getOverlayVertexBuffer(type), renderChunk, type.getGlMode());
            }

            OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
            GlStateManager.resetColor();

            this.overlayRenderChunks.clear();
        }
    }

    private void renderBlocks(VertexBuffer vertexBuffer, RenderChunk renderChunk)
    {
        GlStateManager.pushMatrix();

        this.preRenderChunk(renderChunk);
        //renderChunk.multModelviewMatrix();
        vertexBuffer.bindBuffer();
        this.setupArrayPointersBlocks();
        vertexBuffer.drawArrays(GL11.GL_QUADS);

        GlStateManager.popMatrix();
    }

    private void renderOverlay(VertexBuffer vertexBuffer, RenderChunk renderChunk, int glMode)
    {
        GlStateManager.pushMatrix();

        this.preRenderChunk(renderChunk);
        //renderChunk.multModelviewMatrix();
        vertexBuffer.bindBuffer();
        this.setupArrayPointersOverlay();
        vertexBuffer.drawArrays(glMode);

        GlStateManager.popMatrix();
    }

    private void setupArrayPointersBlocks()
    {
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, 0);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private void setupArrayPointersOverlay()
    {
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 16, 0);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12);
    }
}
