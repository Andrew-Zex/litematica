package fi.dy.masa.litematica.render.schematic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import com.google.common.collect.Sets;
import org.lwjgl.opengl.GL11;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import fi.dy.masa.malilib.render.ShapeRenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import fi.dy.masa.malilib.util.position.IntBoundingBox;
import fi.dy.masa.malilib.util.position.LayerRange;
import fi.dy.masa.malilib.util.position.SubChunkPos;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.mixin.IMixinCompiledChunk;
import fi.dy.masa.litematica.render.RenderUtils;
import fi.dy.masa.litematica.util.OverlayType;
import fi.dy.masa.litematica.util.PositionUtils;

public class RenderChunkSchematicVbo extends RenderChunk
{
    public static int schematicRenderChunksUpdated;

    private final RenderGlobalSchematic renderGlobal;
    private final VertexBuffer[] vertexBufferOverlay = new VertexBuffer[OverlayRenderType.values().length];
    private final Set<TileEntity> setTileEntities = new HashSet<>();
    private final List<IntBoundingBox> boxes = new ArrayList<>();
    private final EnumSet<OverlayRenderType> existingOverlays = EnumSet.noneOf(OverlayRenderType.class);
    private ChunkCompileTaskGeneratorSchematic compileTask;
    protected final ReentrantLock chunkRenderDataLock;

    private ChunkCacheSchematic schematicWorldView;
    private ChunkCacheSchematic clientWorldView;

    private CompiledChunkSchematic schematicChunkRenderData;

    private boolean hasOverlay = false;

    private boolean ignoreClientWorldFluids;
    private boolean overlayEnabled;
    private boolean overlayLinesEnabled;
    private boolean overlayModelLines;
    private boolean overlayModelSides;
    private boolean overlayReducedInnerSides;
    private boolean overlaySidesEnabled;
    private boolean overlayTypeExtra;
    private boolean overlayTypeMissing;
    private boolean overlayTypeWrongBlock;
    private boolean overlayTypeWrongState;
    private boolean renderColliding;
    private boolean renderAsTranslucent;
    private Color4f overlayColorExtra;
    private Color4f overlayColorMissing;
    private Color4f overlayColorWrongBlock;
    private Color4f overlayColorWrongState;

    public RenderChunkSchematicVbo(World worldIn, RenderGlobal renderGlobalIn, int indexIn)
    {
        super(worldIn, renderGlobalIn, indexIn);

        this.renderGlobal = (RenderGlobalSchematic) renderGlobalIn;
        this.chunkRenderDataLock = new ReentrantLock();
        this.schematicChunkRenderData = CompiledChunkSchematic.EMPTY;

        if (OpenGlHelper.useVbo())
        {
            for (int i = 0; i < OverlayRenderType.values().length; ++i)
            {
                this.vertexBufferOverlay[i] = new VertexBuffer(DefaultVertexFormats.POSITION_COLOR);
            }
        }
    }

    public CompiledChunkSchematic getChunkRenderData()
    {
        return this.schematicChunkRenderData;
    }

    public void setChunkRenderData(CompiledChunkSchematic data)
    {
        this.chunkRenderDataLock.lock();

        try
        {
            this.schematicChunkRenderData = data;
        }
        finally
        {
            this.chunkRenderDataLock.unlock();
        }
    }

    public boolean hasOverlay()
    {
        return this.hasOverlay;
    }

    public EnumSet<OverlayRenderType> getOverlayTypes()
    {
        return this.existingOverlays;
    }

    public VertexBuffer getOverlayVertexBuffer(OverlayRenderType type)
    {
        //if (GuiBase.isCtrlDown()) System.out.printf("getOverlayVertexBuffer: type: %s, buf: %s\n", type, this.vertexBufferOverlay[type.ordinal()]);
        return this.vertexBufferOverlay[type.ordinal()];
    }

    @Override
    public void deleteGlResources()
    {
        super.deleteGlResources();

        for (int i = 0; i < this.vertexBufferOverlay.length; ++i)
        {
            if (this.vertexBufferOverlay[i] != null)
            {
                this.vertexBufferOverlay[i].deleteGlBuffers();
            }
        }
    }

    public void resortTransparency(float x, float y, float z, ChunkCompileTaskGeneratorSchematic generator)
    {
        CompiledChunkSchematic compiledChunk = (CompiledChunkSchematic) generator.getCompiledChunk();
        BufferBuilderCache buffers = generator.getBufferCache();
        BufferBuilder.State bufferState = compiledChunk.getBlockBufferState(BlockRenderLayer.TRANSLUCENT);

        if (bufferState != null)
        {
            /*if (Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.getBooleanValue())
            {
                RegionRenderCacheBuilder buffers = generator.getRegionRenderCacheBuilder();

                for (BlockRenderLayer layer : BlockRenderLayer.values())
                {
                    BufferBuilder buffer = buffers.getWorldRendererByLayer(layer);

                    this.preRenderBlocks(buffer, this.getPosition());
                    buffer.setVertexState(compiledChunk.getState());
                    this.postRenderBlocks(layer, x, y, z, buffer, compiledChunk);
                }
            }
            else */if (compiledChunk.isLayerEmpty(BlockRenderLayer.TRANSLUCENT) == false)
            {
                BufferBuilder buffer = buffers.getWorldRendererByLayer(BlockRenderLayer.TRANSLUCENT);

                this.preRenderBlocks(buffer, this.getPosition());
                buffer.setVertexState(bufferState);
                this.postRenderBlocks(BlockRenderLayer.TRANSLUCENT, x, y, z, buffer, compiledChunk);
            }
        }

        //if (GuiBase.isCtrlDown()) System.out.printf("resortTransparency\n");
        if (this.overlayEnabled)
        {
            OverlayRenderType type = OverlayRenderType.QUAD;
            bufferState = compiledChunk.getOverlayBufferState(type);

            if (bufferState != null && compiledChunk.isOverlayTypeEmpty(type) == false)
            {
                BufferBuilder buffer = buffers.getOverlayBuffer(type);

                this.preRenderOverlay(buffer, type.getGlMode());
                buffer.setVertexState(bufferState);
                this.postRenderOverlay(type, x, y, z, buffer, compiledChunk);
            }
        }
    }

    public void rebuildChunk(float x, float y, float z, ChunkCompileTaskGeneratorSchematic generator)
    {
        CompiledChunkSchematic data = new CompiledChunkSchematic();
        generator.getLock().lock();

        try
        {
            if (generator.getStatus() != ChunkCompileTaskGeneratorSchematic.Status.COMPILING)
            {
                return;
            }

            generator.setCompiledChunk(data);
        }
        finally
        {
            generator.getLock().unlock();
        }

        //if (GuiBase.isCtrlDown()) System.out.printf("rebuildChunk pos: %s gen: %s\n", this.getPosition(), generator);
        Set<TileEntity> tileEntities = new HashSet<>();
        BlockPos posChunk = this.getPosition();
        LayerRange range = DataManager.getRenderLayerRange();

        this.existingOverlays.clear();
        this.hasOverlay = false;

        synchronized (this.boxes)
        {
            if (this.boxes.isEmpty() == false &&
                (this.schematicWorldView.isEmpty() == false || this.clientWorldView.isEmpty() == false) &&
                 range.intersects(new SubChunkPos(posChunk.getX() >> 4, posChunk.getY() >> 4, posChunk.getZ() >> 4)))
            {
                ++schematicRenderChunksUpdated;

                boolean[] usedLayers = new boolean[BlockRenderLayer.values().length];
                BufferBuilderCache buffers = generator.getBufferCache();

                for (IntBoundingBox box : this.boxes)
                {
                    box = range.getClampedBox(box);

                    // The rendered layer(s) don't intersect this sub-volume
                    if (box == null)
                    {
                        continue;
                    }

                    BlockPos posFrom = new BlockPos(box.minX, box.minY, box.minZ);
                    BlockPos posTo   = new BlockPos(box.maxX, box.maxY, box.maxZ);

                    for (BlockPos.MutableBlockPos posMutable : BlockPos.getAllInBoxMutable(posFrom, posTo))
                    {
                        this.renderBlocksAndOverlay(posMutable, tileEntities, usedLayers, data, buffers);
                    }
                }

                for (BlockRenderLayer layerTmp : BlockRenderLayer.values())
                {
                    if (usedLayers[layerTmp.ordinal()])
                    {
                        ((IMixinCompiledChunk) data).invokeSetLayerUsed(layerTmp);
                    }

                    if (data.isLayerStarted(layerTmp))
                    {
                        this.postRenderBlocks(layerTmp, x, y, z, buffers.getWorldRendererByLayer(layerTmp), data);
                    }
                }

                if (this.hasOverlay)
                {
                    //if (GuiBase.isCtrlDown()) System.out.printf("postRenderOverlays\n");
                    for (OverlayRenderType type : this.existingOverlays)
                    {
                        if (data.isOverlayTypeStarted(type))
                        {
                            data.setOverlayTypeUsed(type);
                            this.postRenderOverlay(type, x, y, z, buffers.getOverlayBuffer(type), data);
                        }
                    }
                }
            }
        }

        this.getLockCompileTask().lock();

        try
        {
            Set<TileEntity> set = Sets.newHashSet(tileEntities);
            Set<TileEntity> set1 = Sets.newHashSet(this.setTileEntities);
            set.removeAll(this.setTileEntities);
            set1.removeAll(tileEntities);
            this.setTileEntities.clear();
            this.setTileEntities.addAll(tileEntities);
            this.renderGlobal.updateTileEntities(set1, set);
        }
        finally
        {
            this.getLockCompileTask().unlock();
        }
    }

    protected void renderBlocksAndOverlay(BlockPos pos, Set<TileEntity> tileEntities, boolean[] usedLayers, CompiledChunkSchematic data, BufferBuilderCache buffers)
    {
        IBlockState stateSchematic = this.schematicWorldView.getBlockState(pos);
        IBlockState stateClient    = this.clientWorldView.getBlockState(pos);
        stateSchematic = stateSchematic.getActualState(this.schematicWorldView, pos);
        stateClient = stateClient.getActualState(this.clientWorldView, pos);
        Block blockSchematic = stateSchematic.getBlock();
        Block blockClient = stateClient.getBlock();
        boolean clientHasAir = blockClient == Blocks.AIR;
        boolean schematicHasAir = blockSchematic == Blocks.AIR;

        if (clientHasAir && schematicHasAir)
        {
            return;
        }

        // Schematic has a block, client has air
        if (clientHasAir || (stateSchematic != stateClient && this.renderColliding))
        {
            if (blockSchematic.hasTileEntity())
            {
                this.addTileEntity(pos, data, tileEntities);
            }

            BlockRenderLayer layer = this.renderAsTranslucent ? BlockRenderLayer.TRANSLUCENT : blockSchematic.getRenderLayer();
            int layerIndex = layer.ordinal();

            if (stateSchematic.getRenderType() != EnumBlockRenderType.INVISIBLE)
            {
                BufferBuilder bufferSchematic = buffers.getWorldRendererByLayerId(layerIndex);

                if (data.isLayerStarted(layer) == false)
                {
                    data.setLayerStarted(layer);
                    this.preRenderBlocks(bufferSchematic, this.getPosition());
                }

                usedLayers[layerIndex] |= this.renderGlobal.renderBlock(stateSchematic, pos, this.schematicWorldView, bufferSchematic);
            }
        }

        if (this.overlayEnabled)
        {
            OverlayType type = this.getOverlayType(stateSchematic, stateClient);
            Color4f overlayColor = this.getOverlayColor(type);

            if (overlayColor != null)
            {
                this.renderOverlay(pos, stateSchematic, type, overlayColor, data, buffers);
            }
        }
    }

    protected void renderOverlay(BlockPos pos, IBlockState stateSchematic, OverlayType type, Color4f overlayColor, CompiledChunkSchematic data, BufferBuilderCache buffers)
    {
        boolean missing = type == OverlayType.MISSING;

        if (this.overlaySidesEnabled)
        {
            BufferBuilder bufferOverlayQuads = buffers.getOverlayBuffer(OverlayRenderType.QUAD);

            if (data.isOverlayTypeStarted(OverlayRenderType.QUAD) == false)
            {
                data.setOverlayTypeStarted(OverlayRenderType.QUAD);
                this.preRenderOverlay(bufferOverlayQuads, OverlayRenderType.QUAD);
            }

            if (this.overlayReducedInnerSides)
            {
                BlockPos.PooledMutableBlockPos posMutable = BlockPos.PooledMutableBlockPos.retain();

                for (int i = 0; i < 6; ++i)
                {
                    EnumFacing side = PositionUtils.FACING_ALL[i];
                    posMutable.setPos(pos.getX() + side.getXOffset(), pos.getY() + side.getYOffset(), pos.getZ() + side.getZOffset());
                    IBlockState adjStateSchematic = this.schematicWorldView.getBlockState(posMutable);
                    IBlockState adjStateClient    = this.clientWorldView.getBlockState(posMutable);

                    OverlayType typeAdj = this.getOverlayType(adjStateSchematic, adjStateClient);

                    // Only render the model-based outlines or sides for missing blocks
                    if (missing && this.overlayModelSides)
                    {
                        IBakedModel bakedModel = this.renderGlobal.getModelForState(stateSchematic);

                        if (type.getRenderPriority() > typeAdj.getRenderPriority() ||
                            stateSchematic.getBlockFaceShape(this.schematicWorldView, pos, side) != BlockFaceShape.SOLID)
                        {
                            RenderUtils.drawBlockModelQuadOverlayBatched(bakedModel, stateSchematic, pos, side, overlayColor, 0, bufferOverlayQuads);
                        }
                    }
                    else
                    {
                        if (type.getRenderPriority() > typeAdj.getRenderPriority())
                        {
                            ShapeRenderUtils.renderBlockPosSideQuad(pos, side, 0, overlayColor, bufferOverlayQuads);
                        }
                    }
                }

                posMutable.release();
            }
            else
            {
                // Only render the model-based outlines or sides for missing blocks
                if (missing && this.overlayModelSides)
                {
                    IBakedModel bakedModel = this.renderGlobal.getModelForState(stateSchematic);
                    RenderUtils.drawBlockModelQuadOverlayBatched(bakedModel, stateSchematic, pos, overlayColor, 0, bufferOverlayQuads);
                }
                else
                {
                    ShapeRenderUtils.renderBlockPosSideQuads(pos, 0, overlayColor, bufferOverlayQuads);
                }
            }
        }

        if (this.overlayLinesEnabled)
        {
            BufferBuilder bufferOverlayOutlines = buffers.getOverlayBuffer(OverlayRenderType.OUTLINE);

            if (data.isOverlayTypeStarted(OverlayRenderType.OUTLINE) == false)
            {
                data.setOverlayTypeStarted(OverlayRenderType.OUTLINE);
                this.preRenderOverlay(bufferOverlayOutlines, OverlayRenderType.OUTLINE);
            }

            overlayColor = new Color4f(overlayColor.r, overlayColor.g, overlayColor.b, 1f);

            if (this.overlayReducedInnerSides)
            {
                OverlayType[][][] adjTypes = new OverlayType[3][3][3];
                BlockPos.PooledMutableBlockPos posMutable = BlockPos.PooledMutableBlockPos.retain();

                for (int y = 0; y <= 2; ++y)
                {
                    for (int z = 0; z <= 2; ++z)
                    {
                        for (int x = 0; x <= 2; ++x)
                        {
                            if (x != 1 || y != 1 || z != 1)
                            {
                                posMutable.setPos(pos.getX() + x - 1, pos.getY() + y - 1, pos.getZ() + z - 1);
                                IBlockState adjStateSchematic = this.schematicWorldView.getBlockState(posMutable);
                                IBlockState adjStateClient    = this.clientWorldView.getBlockState(posMutable);
                                adjTypes[x][y][z] = this.getOverlayType(adjStateSchematic, adjStateClient);
                            }
                            else
                            {
                                adjTypes[x][y][z] = type;
                            }
                        }
                    }
                }

                posMutable.release();

                // Only render the model-based outlines or sides for missing blocks
                if (missing && this.overlayModelLines)
                {
                    IBakedModel bakedModel = this.renderGlobal.getModelForState(stateSchematic);

                    // FIXME: how to implement this correctly here... >_>
                    if (stateSchematic.isFullCube())
                    {
                        this.renderOverlayReducedEdges(pos, adjTypes, type, overlayColor, bufferOverlayOutlines);
                    }
                    else
                    {
                        RenderUtils.drawBlockModelOutlinesBatched(bakedModel, stateSchematic, pos, overlayColor, bufferOverlayOutlines);
                    }
                }
                else
                {
                    this.renderOverlayReducedEdges(pos, adjTypes, type, overlayColor, bufferOverlayOutlines);
                }
            }
            else
            {
                // Only render the model-based outlines or sides for missing blocks
                if (missing && this.overlayModelLines)
                {
                    IBakedModel bakedModel = this.renderGlobal.getModelForState(stateSchematic);
                    RenderUtils.drawBlockModelOutlinesBatched(bakedModel, stateSchematic, pos, overlayColor, bufferOverlayOutlines);
                }
                else
                {
                    ShapeRenderUtils.renderBlockPosEdgeLines(pos, 0, overlayColor, bufferOverlayOutlines);
                }
            }
        }
    }

    protected void renderOverlayReducedEdges(BlockPos pos, OverlayType[][][] adjTypes, OverlayType typeSelf, Color4f overlayColor, BufferBuilder bufferOverlayOutlines)
    {
        OverlayType[] neighborTypes = new OverlayType[4];
        Vec3i[] neighborPositions = new Vec3i[4];
        int lines = 0;

        for (EnumFacing.Axis axis : PositionUtils.AXES_ALL)
        {
            for (int corner = 0; corner < 4; ++corner)
            {
                Vec3i[] offsets = PositionUtils.getEdgeNeighborOffsets(axis, corner);
                int index = -1;
                boolean hasCurrent = false;

                // Find the position(s) around a given edge line that have the shared greatest rendering priority
                for (int i = 0; i < 4; ++i)
                {
                    Vec3i offset = offsets[i];
                    OverlayType type = adjTypes[offset.getX() + 1][offset.getY() + 1][offset.getZ() + 1];

                    // type NONE
                    if (type == OverlayType.NONE)
                    {
                        continue;
                    }

                    // First entry, or sharing at least the current highest found priority
                    if (index == -1 || type.getRenderPriority() >= neighborTypes[index - 1].getRenderPriority())
                    {
                        // Actually a new highest priority, add it as the first entry and rewind the index
                        if (index < 0 || type.getRenderPriority() > neighborTypes[index - 1].getRenderPriority())
                        {
                            index = 0;
                        }
                        // else: Same priority as a previous entry, append this position

                        //System.out.printf("plop 0 axis: %s, corner: %d, i: %d, index: %d, type: %s\n", axis, corner, i, index, type);
                        neighborPositions[index] = new Vec3i(pos.getX() + offset.getX(), pos.getY() + offset.getY(), pos.getZ() + offset.getZ());
                        neighborTypes[index] = type;
                        // The self position is the first (offset = [0, 0, 0]) in the arrays
                        hasCurrent |= (i == 0);
                        ++index;
                    }
                }

                //System.out.printf("plop 1 index: %d, pos: %s\n", index, pos);
                // Found something to render, and the current block is among the highest priority for this edge
                if (index > 0 && hasCurrent)
                {
                    Vec3i posTmp = new Vec3i(pos.getX(), pos.getY(), pos.getZ());
                    int ind = -1;

                    for (int i = 0; i < index; ++i)
                    {
                        Vec3i tmp = neighborPositions[i];
                        //System.out.printf("posTmp: %s, tmp: %s\n", posTmp, tmp);

                        // Just prioritize the position to render a shared highest priority edge by the coordinates
                        if (tmp.getX() <= posTmp.getX() && tmp.getY() <= posTmp.getY() && tmp.getZ() <= posTmp.getZ())
                        {
                            posTmp = tmp;
                            ind = i;
                        }
                    }

                    // The current position is the one that should render this edge
                    if (posTmp.getX() == pos.getX() && posTmp.getY() == pos.getY() && posTmp.getZ() == pos.getZ())
                    {
                        //System.out.printf("plop 2 index: %d, ind: %d, pos: %s, off: %s\n", index, ind, pos, posTmp);
                        RenderUtils.drawBlockBoxEdgeBatchedLines(pos, axis, corner, overlayColor, bufferOverlayOutlines);
                        lines++;
                    }
                }
            }
        }
        //System.out.printf("typeSelf: %s, pos: %s, lines: %d\n", typeSelf, pos, lines);
    }

    protected OverlayType getOverlayType(IBlockState stateSchematic, IBlockState stateClient)
    {
        if (stateSchematic == stateClient)
        {
            return OverlayType.NONE;
        }
        else
        {
            boolean clientHasAir = stateClient.getBlock() == Blocks.AIR;
            boolean schematicHasAir = stateSchematic.getBlock() == Blocks.AIR;

            if (schematicHasAir)
            {
                return (clientHasAir || (this.ignoreClientWorldFluids && stateClient.getMaterial().isLiquid())) ? OverlayType.NONE : OverlayType.EXTRA;
            }
            else
            {
                if (clientHasAir || (this.ignoreClientWorldFluids && stateClient.getMaterial().isLiquid()))
                {
                    return OverlayType.MISSING;
                }
                // Wrong block
                else if (stateSchematic.getBlock() != stateClient.getBlock())
                {
                    return OverlayType.WRONG_BLOCK;
                }
                // Wrong state
                else
                {
                    return OverlayType.WRONG_STATE;
                }
            }
        }
    }

    @Nullable
    protected Color4f getOverlayColor(OverlayType overlayType)
    {
        switch (overlayType)
        {
            case MISSING:
                if (this.overlayTypeMissing)
                {
                    return this.overlayColorMissing;
                }
                break;
            case EXTRA:
                if (this.overlayTypeExtra)
                {
                    return this.overlayColorExtra;
                }
                break;
            case WRONG_BLOCK:
                if (this.overlayTypeWrongBlock)
                {
                    return this.overlayColorWrongBlock;
                }
                break;
            case WRONG_STATE:
                if (this.overlayTypeWrongState)
                {
                    return this.overlayColorWrongState;
                }
                break;
            default:
        }

        return null;
    }

    private void addTileEntity(BlockPos pos, CompiledChunk compiledChunk, Set<TileEntity> tileEntities)
    {
        TileEntity te = this.schematicWorldView.getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);

        if (te != null)
        {
            TileEntitySpecialRenderer<TileEntity> tesr = TileEntityRendererDispatcher.instance.<TileEntity>getRenderer(te);

            if (tesr != null)
            {
                compiledChunk.addTileEntity(te);

                if (tesr.isGlobalRenderer(te))
                {
                    tileEntities.add(te);
                }
            }
        }
    }

    private void preRenderBlocks(BufferBuilder buffer, BlockPos pos)
    {
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    private void postRenderBlocks(BlockRenderLayer layer, float x, float y, float z, BufferBuilder buffer, CompiledChunkSchematic compiledChunk)
    {
        if (layer == BlockRenderLayer.TRANSLUCENT && compiledChunk.isLayerEmpty(layer) == false)
        {
            buffer.sortVertexData(x, y, z);
            compiledChunk.setBlockBufferState(layer, buffer.getVertexState());
        }

        buffer.finishDrawing();
    }

    private void preRenderOverlay(BufferBuilder buffer, OverlayRenderType type)
    {
        this.existingOverlays.add(type);
        this.hasOverlay = true;

        BlockPos pos = this.getPosition();
        buffer.begin(type.getGlMode(), DefaultVertexFormats.POSITION_COLOR);
        buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    private void preRenderOverlay(BufferBuilder buffer, int glMode)
    {
        BlockPos pos = this.getPosition();
        buffer.begin(glMode, DefaultVertexFormats.POSITION_COLOR);
        buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());
    }

    private void postRenderOverlay(OverlayRenderType type, float x, float y, float z, BufferBuilder buffer, CompiledChunkSchematic compiledChunk)
    {
        if (type == OverlayRenderType.QUAD && compiledChunk.isOverlayTypeEmpty(type) == false)
        {
            buffer.sortVertexData(x, y, z);
            compiledChunk.setOverlayBufferState(type, buffer.getVertexState());
        }

        buffer.finishDrawing();
    }

    public ChunkCompileTaskGeneratorSchematic makeCompileTaskChunkSchematic()
    {
        this.getLockCompileTask().lock();
        ChunkCompileTaskGeneratorSchematic generator = null;

        try
        {
            //if (GuiBase.isCtrlDown()) System.out.printf("makeCompileTaskChunk()\n");
            this.finishCompileTask();
            this.compileTask = new ChunkCompileTaskGeneratorSchematic(this, ChunkCompileTaskGeneratorSchematic.Type.REBUILD_CHUNK, this.getDistanceSq());
            this.rebuildWorldView();
            generator = this.compileTask;
        }
        finally
        {
            this.getLockCompileTask().unlock();
        }

        return generator;
    }

    @Nullable
    public ChunkCompileTaskGeneratorSchematic makeCompileTaskTransparencySchematic()
    {
        this.getLockCompileTask().lock();

        try
        {
            if (this.compileTask == null || this.compileTask.getStatus() != ChunkCompileTaskGeneratorSchematic.Status.PENDING)
            {
                if (this.compileTask != null && this.compileTask.getStatus() != ChunkCompileTaskGeneratorSchematic.Status.DONE)
                {
                    this.compileTask.finish();
                }

                this.compileTask = new ChunkCompileTaskGeneratorSchematic(this, ChunkCompileTaskGeneratorSchematic.Type.RESORT_TRANSPARENCY, this.getDistanceSq());
                this.compileTask.setCompiledChunk(this.schematicChunkRenderData);

                return this.compileTask;
            }
        }
        finally
        {
            this.getLockCompileTask().unlock();
        }

        return null;
    }

    protected void finishCompileTask()
    {
        this.getLockCompileTask().lock();

        try
        {
            if (this.compileTask != null && this.compileTask.getStatus() != ChunkCompileTaskGeneratorSchematic.Status.DONE)
            {
                this.compileTask.finish();
                this.compileTask = null;
            }
        }
        finally
        {
            this.getLockCompileTask().unlock();
        }
    }

    private void rebuildWorldView()
    {
        synchronized (this.boxes)
        {
            this.ignoreClientWorldFluids = Configs.Visuals.IGNORE_EXISTING_FLUIDS.getBooleanValue();
            this.overlayEnabled = Configs.Visuals.SCHEMATIC_OVERLAY.getBooleanValue();
            this.overlayReducedInnerSides = Configs.Visuals.OVERLAY_REDUCED_INNER_SIDES.getBooleanValue();
            this.overlayLinesEnabled = Configs.Visuals.SCHEMATIC_OVERLAY_OUTLINES.getBooleanValue();
            this.overlayModelLines = Configs.Visuals.SCHEMATIC_OVERLAY_MODEL_OUTLINE.getBooleanValue();
            this.overlayModelSides = Configs.Visuals.SCHEMATIC_OVERLAY_MODEL_SIDES.getBooleanValue();
            this.overlaySidesEnabled = Configs.Visuals.SCHEMATIC_OVERLAY_SIDES.getBooleanValue();
            this.renderColliding = Configs.Visuals.RENDER_COLLIDING_SCHEMATIC_BLOCKS.getBooleanValue();
            this.renderAsTranslucent = Configs.Visuals.TRANSLUCENT_SCHEMATIC_RENDERING.getBooleanValue();
            this.overlayTypeExtra = Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_EXTRA.getBooleanValue();
            this.overlayTypeMissing = Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_MISSING.getBooleanValue();
            this.overlayTypeWrongBlock = Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_WRONG_BLOCK.getBooleanValue();
            this.overlayTypeWrongState = Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_WRONG_STATE.getBooleanValue();

            this.overlayColorExtra = Configs.Colors.SCHEMATIC_OVERLAY_EXTRA.getColor();
            this.overlayColorMissing = Configs.Colors.SCHEMATIC_OVERLAY_MISSING.getColor();
            this.overlayColorWrongBlock = Configs.Colors.SCHEMATIC_OVERLAY_WRONG_BLOCK.getColor();
            this.overlayColorWrongState = Configs.Colors.SCHEMATIC_OVERLAY_WRONG_STATE.getColor();

            this.schematicWorldView = new ChunkCacheSchematic(this.getWorld(), this.getPosition(), 2);
            this.clientWorldView    = new ChunkCacheSchematic(Minecraft.getMinecraft().world, this.getPosition(), 2);

            BlockPos pos = this.getPosition();
            SubChunkPos subChunk = new SubChunkPos(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
            this.boxes.clear();
            this.boxes.addAll(DataManager.getSchematicPlacementManager().getTouchedBoxesInSubChunk(subChunk));
        }
    }

    public enum OverlayRenderType
    {
        OUTLINE     (GL11.GL_LINES),
        QUAD        (GL11.GL_QUADS);

        private final int glMode;

        private OverlayRenderType(int glMode)
        {
            this.glMode = glMode;
        }

        public int getGlMode()
        {
            return this.glMode;
        }
    }
}
