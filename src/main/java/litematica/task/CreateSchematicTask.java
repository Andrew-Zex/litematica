package litematica.task;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.google.common.collect.ImmutableMap;

import malilib.listener.TaskCompletionListener;
import malilib.overlay.message.MessageDispatcher;
import malilib.util.position.BlockPos;
import malilib.util.position.ChunkPos;
import malilib.util.position.IntBoundingBox;
import litematica.render.infohud.InfoHud;
import litematica.scheduler.tasks.TaskProcessChunkBase;
import litematica.schematic.ISchematic;
import litematica.schematic.util.SchematicCreationUtils;
import litematica.selection.AreaSelection;
import litematica.selection.SelectionBox;
import litematica.util.PositionUtils;

public class CreateSchematicTask extends TaskProcessChunkBase
{
    protected final ImmutableMap<String, SelectionBox> subRegions;
    protected final Set<UUID> existingEntities = new HashSet<>();
    protected final ISchematic schematic;
    protected final BlockPos origin;
    protected final boolean ignoreEntities;

    public CreateSchematicTask(ISchematic schematic,
                               AreaSelection area,
                               boolean ignoreEntities,
                               TaskCompletionListener listener)
    {
        super("litematica.hud.task_name.save_schematic");

        this.ignoreEntities = ignoreEntities;
        this.schematic = schematic;
        this.origin = area.getEffectiveOrigin();
        this.subRegions = area.getAllSelectionBoxesMap();
        this.setCompletionListener(listener);

        this.addPerChunkBoxes(area.getAllSelectionBoxes());
        this.updateInfoHudLinesMissingChunks(this.requiredChunks);
    }

    @Override
    protected boolean canProcessChunk(ChunkPos pos)
    {
        return this.areSurroundingChunksLoaded(pos, this.worldClient, 1);
    }

    @Override
    protected boolean processChunk(ChunkPos pos)
    {
        ImmutableMap<String, IntBoundingBox> volumes = PositionUtils.getBoxesWithinChunk(pos.x, pos.z, this.subRegions);
        SchematicCreationUtils.takeBlocksFromWorldWithinChunk(this.schematic, this.world, volumes, this.subRegions);

        if (this.ignoreEntities == false)
        {
            SchematicCreationUtils.takeEntitiesFromWorldWithinChunk(this.schematic, this.world, volumes,
                                                                    this.subRegions, this.existingEntities);
        }

        return true;
    }

    @Override
    protected void onStop()
    {
        if (this.finished == false)
        {
            MessageDispatcher.warning().translate("litematica.message.error.schematic_save_interrupted");
        }

        InfoHud.getInstance().removeInfoHudRenderer(this, false);

        this.notifyListener();
    }

    /*
    public static class SaveSettings
    {
        public final SimpleBooleanStorage saveBlocks              = new SimpleBooleanStorage(true);
        public final SimpleBooleanStorage saveBlockEntities       = new SimpleBooleanStorage(true);
        public final SimpleBooleanStorage saveEntities            = new SimpleBooleanStorage(true);
        public final SimpleBooleanStorage saveScheduledBlockTicks = new SimpleBooleanStorage(true);
        public final SimpleBooleanStorage saveFromClientWorld     = new SimpleBooleanStorage(true);
        public final SimpleBooleanStorage saveFromSchematicWorld  = new SimpleBooleanStorage(false);
        public final SimpleBooleanStorage exposedBlocksOnly       = new SimpleBooleanStorage(false);
    }
    */
}
