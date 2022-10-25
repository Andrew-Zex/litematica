package litematica.selection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import malilib.gui.BaseScreen;
import malilib.gui.util.GuiUtils;
import malilib.overlay.message.MessageDispatcher;
import malilib.overlay.message.MessageOutput;
import malilib.util.FileNameUtils;
import malilib.util.FileUtils;
import malilib.util.data.json.JsonUtils;
import malilib.util.game.wrap.EntityWrap;
import malilib.util.game.wrap.GameUtils;
import malilib.util.position.PositionUtils;
import litematica.Litematica;
import litematica.config.Configs;
import litematica.data.DataManager;
import litematica.gui.NormalModeAreaEditorScreen;
import litematica.gui.SimpleModeAreaEditorScreen;
import litematica.schematic.placement.SchematicPlacement;
import litematica.schematic.projects.SchematicProject;
import litematica.util.PositionUtils.Corner;
import litematica.util.RayTraceUtils;
import litematica.util.RayTraceUtils.RayTraceWrapper;
import litematica.util.RayTraceUtils.RayTraceWrapper.HitType;

public class SelectionManager
{
    private final Map<String, AreaSelection> selections = new HashMap<>();
    private final Map<String, AreaSelection> readOnlySelections = new HashMap<>();
    @Nullable
    private String currentSelectionId;
    @Nullable
    private GrabbedElement grabbedElement;
    private SelectionMode mode = SelectionMode.SIMPLE;

    public SelectionMode getSelectionMode()
    {
        if (DataManager.getSchematicProjectsManager().hasProjectOpen())
        {
            SchematicProject project = DataManager.getSchematicProjectsManager().getCurrentProject();
            return project != null ? project.getSelectionMode() : SelectionMode.SIMPLE;
        }

        return this.mode;
    }

    public void switchSelectionMode()
    {
        if (DataManager.getSchematicProjectsManager().hasProjectOpen())
        {
            SchematicProject project = DataManager.getSchematicProjectsManager().getCurrentProject();

            if (project != null)
            {
                project.switchSelectionMode();
            }
            else
            {
                MessageDispatcher.warning().screenOrActionbar()
                        .translate("litematica.error.schematic_projects.in_projects_mode_but_no_project_open");
            }
        }
        else
        {
            this.mode = this.mode == SelectionMode.MULTI_REGION ? SelectionMode.SIMPLE : SelectionMode.MULTI_REGION;
        }
    }

    @Nullable
    public String getCurrentSelectionId()
    {
        return this.mode == SelectionMode.MULTI_REGION ? this.currentSelectionId : null;
    }

    @Nullable
    public String getCurrentNormalSelectionId()
    {
        return this.currentSelectionId;
    }

    public boolean hasNormalSelection()
    {
        if (DataManager.getSchematicProjectsManager().hasProjectOpen())
        {
            return true;
        }

        return this.getNormalSelection(this.currentSelectionId) != null;
    }

    @Nullable
    public AreaSelection getCurrentSelection()
    {
        SchematicProject project = DataManager.getSchematicProjectsManager().getCurrentProject();

        if (project != null)
        {
            return project.getSelection();
        }

        return this.getSelection(this.currentSelectionId);
    }

    @Nullable
    public AreaSelection getSelection(@Nullable String selectionId)
    {
        if (this.mode == SelectionMode.SIMPLE)
        {
            return this.getSimpleSelection();
        }

        return this.getNormalSelection(selectionId);
    }

    protected AreaSelectionSimple getSimpleSelection()
    {
        return DataManager.getSimpleArea();
    }

    @Nullable
    protected AreaSelection getNormalSelection(@Nullable String selectionId)
    {
        return selectionId != null ? this.selections.get(selectionId) : null;
    }

    @Nullable
    public AreaSelection getOrLoadSelection(String selectionId)
    {
        AreaSelection selection = this.getNormalSelection(selectionId);

        if (selection == null)
        {
            selection = this.tryLoadSelectionFromFile(selectionId);

            if (selection != null)
            {
                this.selections.put(selectionId, selection);
            }
        }

        return selection;
    }

    @Nullable
    public AreaSelection getOrLoadSelectionReadOnly(String selectionId)
    {
        AreaSelection selection = this.getNormalSelection(selectionId);

        if (selection == null)
        {
            selection = this.readOnlySelections.get(selectionId);

            if (selection == null)
            {
                selection = this.tryLoadSelectionFromFile(selectionId);

                if (selection != null)
                {
                    this.readOnlySelections.put(selectionId, selection);
                }
            }
        }

        return selection;
    }

    @Nullable
    private AreaSelection tryLoadSelectionFromFile(String selectionId)
    {
        return tryLoadSelectionFromFile(Paths.get(selectionId));
    }

    @Nullable
    public static AreaSelection tryLoadSelectionFromFile(Path file)
    {
        JsonElement el = JsonUtils.parseJsonFile(file);

        if (el != null && el.isJsonObject())
        {
            return AreaSelection.fromJson(el.getAsJsonObject());
        }

        return null;
    }

    public boolean removeSelection(String selectionId)
    {
        if (selectionId != null && this.selections.remove(selectionId) != null)
        {
            if (selectionId.equals(this.currentSelectionId))
            {
                this.currentSelectionId = null;
            }

            Path file = Paths.get(selectionId);

            if (Files.exists(file))
            {
                FileUtils.delete(file);
            }

            return true;
        }

        return false;
    }

    public boolean renameSelection(String selectionId, String newName, MessageOutput output)
    {
        Path dir = Paths.get(selectionId);
        dir = dir.getParent();

        return this.renameSelection(dir, selectionId, newName, output);
    }

    public boolean renameSelection(Path dir, String selectionId, String newName, MessageOutput output)
    {
        return this.renameSelection(dir, selectionId, newName, false, output);
    }

    public boolean renameSelection(Path dir, String selectionId, String newName, boolean copy, MessageOutput output)
    {
        Path file = Paths.get(selectionId);

        if (Files.isRegularFile(file))
        {
            String newFileName = FileNameUtils.generateSafeFileName(newName);

            if (newFileName.isEmpty())
            {
                String key = "litematica.error.area_selection.rename.invalid_safe_file_name";
                MessageDispatcher.error().type(output).translate(key, newFileName);
                return false;
            }

            Path newFile = dir.resolve(newFileName + ".json");

            if (Files.exists(newFile) == false && (copy || FileUtils.move(file, newFile)))
            {
                String newId = newFile.toAbsolutePath().toString();
                AreaSelection selection;

                if (copy)
                {
                    try
                    {
                        Files.copy(file, newFile);
                    }
                    catch (Exception e)
                    {
                        MessageDispatcher.error().console(e).type(output)
                                .translate("litematica.error.area_selection.copy_failed");
                        return false;
                    }

                    selection = this.getOrLoadSelection(newId);
                }
                else
                {
                    selection = this.selections.remove(selectionId);
                }

                if (selection != null)
                {
                    renameSubRegionBoxIfSingle(selection, newName);
                    selection.setName(newName);

                    this.selections.put(newId, selection);

                    if (selectionId.equals(this.currentSelectionId))
                    {
                        this.currentSelectionId = newId;
                    }

                    return true;
                }
            }
            else
            {
                MessageDispatcher.error().type(output)
                        .translate("litematica.error.area_selection.rename.already_exists",
                                   newFile.getFileName().toString());
            }
        }

        return false;
    }

    public void setCurrentSelection(@Nullable String selectionId)
    {
        this.currentSelectionId = selectionId;

        if (this.currentSelectionId != null)
        {
            this.getOrLoadSelection(this.currentSelectionId);
        }
    }

    /**
     * Creates a new schematic selection and returns the name of it
     * @return
     */
    public String createNewSelection(Path dir, final String nameIn)
    {
        String name = nameIn;
        String safeName = FileNameUtils.generateSafeFileName(name);
        Path file = dir.resolve(safeName + ".json");
        String selectionId = file.toAbsolutePath().toString();
        int i = 1;

        while (i < 1000 && (safeName.isEmpty() || this.selections.containsKey(selectionId) || Files.exists(file)))
        {
            name = nameIn + " " + i;
            safeName = FileNameUtils.generateSafeFileName(name);
            file = dir.resolve(safeName + ".json");
            selectionId = file.toAbsolutePath().toString();
            i++;
        }

        AreaSelection selection = new AreaSelection();
        selection.setName(name);
        BlockPos pos = EntityWrap.getCameraEntityBlockPos();
        selection.createNewSubRegionBox(pos, name);

        this.selections.put(selectionId, selection);
        this.currentSelectionId = selectionId;

        JsonUtils.writeJsonToFile(selection.toJson(), file);

        return this.currentSelectionId;
    }

    public boolean createNewSubRegion(Minecraft mc, boolean printMessage)
    {
        AreaSelection selection = this.getCurrentSelection();

        if (selection != null && mc.player != null)
        {
            BlockPos pos = EntityWrap.getCameraEntityBlockPos();

            if (selection.createNewSubRegionBox(pos, selection.getName()) != null)
            {
                if (printMessage)
                {
                    String posStr = String.format("x: %d, y: %d, z: %d", pos.getX(), pos.getY(), pos.getZ());
                    MessageDispatcher.success().screenOrActionbar()
                            .translate("litematica.message.added_selection_box", posStr);
                }

                return true;
            }
        }

        return false;
    }

    public boolean createNewSubRegionIfNotExists(String name)
    {
        AreaSelection selection = this.getCurrentSelection();
        Entity cameraEntity = GameUtils.getCameraEntity();

        if (selection != null && cameraEntity != null)
        {
            if (selection.getSubRegionBox(name) != null)
            {
                MessageDispatcher.error().translate("litematica.error.area_editor.create_sub_region.exists", name);
                return false;
            }

            BlockPos pos = EntityWrap.getCameraEntityBlockPos();

            if (selection.createNewSubRegionBox(pos, name) != null)
            {
                String posStr = String.format("x: %d, y: %d, z: %d", pos.getX(), pos.getY(), pos.getZ());
                MessageDispatcher.success().translate("litematica.message.added_selection_box", posStr);

                return true;
            }
        }

        return false;
    }

    public boolean createSelectionFromPlacement(Path dir, SchematicPlacement placement, String name)
    {
        String safeName = FileNameUtils.generateSafeFileName(name);

        if (safeName.isEmpty())
        {
            MessageDispatcher.error().translate("litematica.error.area_selection.rename.invalid_safe_file_name", safeName);
            return false;
        }

        Path file = dir.resolve(safeName + ".json");
        String selectionId = file.toAbsolutePath().toString();
        AreaSelection selection = this.getOrLoadSelectionReadOnly(selectionId);

        if (selection == null)
        {
            selection = AreaSelection.fromPlacement(placement);
            renameSubRegionBoxIfSingle(selection, name);
            selection.setName(name);

            this.selections.put(selectionId, selection);
            this.currentSelectionId = selectionId;

            JsonUtils.writeJsonToFile(selection.toJson(), file);

            return true;
        }

        MessageDispatcher.error().translate("litematica.error.area_selection.create_failed", safeName);

        return false;
    }

    public boolean changeSelection(World world, Entity entity, int maxDistance)
    {
        AreaSelection area = this.getCurrentSelection();

        if (area != null)
        {
            RayTraceWrapper trace = RayTraceUtils.getWrappedRayTraceFromEntity(world, entity, maxDistance);

            if (trace.getHitType() == HitType.SELECTION_BOX_CORNER || trace.getHitType() == HitType.SELECTION_BOX_BODY || trace.getHitType() == HitType.SELECTION_ORIGIN)
            {
                this.changeSelection(area, trace);
                return true;
            }
            else if (trace.getHitType() == HitType.MISS)
            {
                area.clearCurrentSelectedCorner();
                area.setSelectedSubRegionBox(null);
                area.setOriginSelected(false);
                return true;
            }
        }

        return false;
    }

    private void changeSelection(AreaSelection area, RayTraceWrapper trace)
    {
        area.clearCurrentSelectedCorner();

        if (trace.getHitType() == HitType.SELECTION_BOX_CORNER || trace.getHitType() == HitType.SELECTION_BOX_BODY)
        {
            SelectionBox box = trace.getHitSelectionBox();
            area.setSelectedSubRegionBox(box.getName());
            area.setOriginSelected(false);
            box.setSelectedCorner(trace.getHitCorner());
        }
        else if (trace.getHitType() == HitType.SELECTION_ORIGIN)
        {
            area.setSelectedSubRegionBox(null);
            area.setOriginSelected(true);
        }
    }

    public boolean hasSelectedElement()
    {
        AreaSelection area = this.getCurrentSelection();
        return area != null && (area.getSelectedSubRegionBox() != null || area.isOriginSelected());
    }

    public boolean hasSelectedOrigin()
    {
        AreaSelection area = this.getCurrentSelection();
        return area != null && area.isOriginSelected();
    }

    public void moveSelectedElement(EnumFacing direction, int amount)
    {
        AreaSelection area = this.getCurrentSelection();

        if (area != null)
        {
            area.moveSelectedElement(direction, amount);
        }
    }

    public boolean hasGrabbedElement()
    {
        return this.grabbedElement != null;
    }

    public boolean grabElement(Minecraft mc, int maxDistance)
    {
        World world = mc.world;
        Entity entity = GameUtils.getCameraEntity();
        AreaSelection area = this.getCurrentSelection();

        if (area != null && area.getAllSubRegionBoxes().size() > 0)
        {
            RayTraceWrapper trace = RayTraceUtils.getWrappedRayTraceFromEntity(world, entity, maxDistance);

            if (trace.getHitType() == HitType.SELECTION_BOX_CORNER || trace.getHitType() == HitType.SELECTION_BOX_BODY)
            {
                this.changeSelection(area, trace);
                this.grabbedElement = new GrabbedElement(
                        area,
                        trace.getHitSelectionBox(),
                        trace.getHitCorner(),
                        trace.getHitVec(),
                        entity.getPositionEyes(1f).distanceTo(trace.getHitVec()));
                MessageDispatcher.generic().customHotbar().translate("litematica.message.grabbed_element_for_moving");
                return true;
            }
        }

        return false;
    }

    public void setPositionOfCurrentSelectionToRayTrace(Minecraft mc, Corner corner, boolean moveEntireSelection, double maxDistance)
    {
        AreaSelection area = this.getCurrentSelection();

        if (area != null)
        {
            boolean movingCorner = area.getSelectedSubRegionBox() != null && corner != Corner.NONE;
            boolean movingOrigin = area.isOriginSelected();

            if (movingCorner || movingOrigin)
            {
                Entity entity = GameUtils.getCameraEntity();
                BlockPos pos = RayTraceUtils.getTargetedPosition(mc.world, entity, maxDistance, true);

                if (pos == null)
                {
                    return;
                }

                if (movingOrigin)
                {
                    this.moveSelectionOrigin(area, pos, moveEntireSelection);
                }
                // Moving a corner
                else
                {
                    int cornerIndex = corner.ordinal();

                    if (corner == Corner.CORNER_1 || corner == Corner.CORNER_2)
                    {
                        area.setSelectedSubRegionCornerPos(pos, corner);
                    }

                    if (Configs.Generic.CHANGE_SELECTED_CORNER.getBooleanValue())
                    {
                        area.getSelectedSubRegionBox().setSelectedCorner(corner);
                    }

                    String posStr = String.format("x: %d, y: %d, z: %d", pos.getX(), pos.getY(), pos.getZ());
                    MessageDispatcher.generic().customHotbar().translate("litematica.message.set_selection_box_point",
                                                                         cornerIndex, posStr);
                }
            }
        }
    }

    public void moveSelectionOrigin(AreaSelection area, BlockPos newOrigin, boolean moveEntireSelection)
    {
        if (moveEntireSelection)
        {
            area.moveEntireSelectionTo(newOrigin, true);
        }
        else
        {
            BlockPos old = area.getEffectiveOrigin();
            area.setExplicitOrigin(newOrigin);

            String posStrOld = String.format("x: %d, y: %d, z: %d", old.getX(), old.getY(), old.getZ());
            String posStrNew = String.format("x: %d, y: %d, z: %d", newOrigin.getX(), newOrigin.getY(), newOrigin.getZ());
            MessageDispatcher.success().screenOrActionbar()
                    .translate("litematica.message.moved_area_origin", posStrOld, posStrNew);
        }
    }

    public void handleCuboidModeMouseClick(Minecraft mc, double maxDistance, boolean isRightClick, boolean moveEntireSelection)
    {
        AreaSelection selection = this.getCurrentSelection();

        if (selection != null)
        {
            if (selection.isOriginSelected())
            {
                Entity entity = GameUtils.getCameraEntity();
                BlockPos newOrigin = RayTraceUtils.getTargetedPosition(mc.world, entity, maxDistance, true);

                if (newOrigin != null)
                {
                    this.moveSelectionOrigin(selection, newOrigin, moveEntireSelection);
                }
            }
            // Right click in Cuboid mode: Reset the area to the clicked position
            else if (isRightClick)
            {
                this.resetSelectionToClickedPosition(mc, maxDistance);
            }
            // Left click in Cuboid mode: Grow the selection to contain each clicked position
            else
            {
                this.growSelectionToContainClickedPosition(mc, maxDistance);
            }
        }
    }

    private void resetSelectionToClickedPosition(Minecraft mc, double maxDistance)
    {
        AreaSelection area = this.getCurrentSelection();

        if (area != null && area.getSelectedSubRegionBox() != null)
        {
            Entity entity = GameUtils.getCameraEntity();
            BlockPos pos = RayTraceUtils.getTargetedPosition(mc.world, entity, maxDistance, true);

            if (pos != null)
            {
                area.setSelectedSubRegionCornerPos(pos, Corner.CORNER_1);
                area.setSelectedSubRegionCornerPos(pos, Corner.CORNER_2);
            }
        }
    }

    private void growSelectionToContainClickedPosition(Minecraft mc, double maxDistance)
    {
        AreaSelection sel = this.getCurrentSelection();

        if (sel != null && sel.getSelectedSubRegionBox() != null)
        {
            Entity entity = GameUtils.getCameraEntity();
            BlockPos pos = RayTraceUtils.getTargetedPosition(mc.world, entity, maxDistance, true);

            if (pos != null)
            {
                Box box = sel.getSelectedSubRegionBox();
                BlockPos pos1 = box.getPos1();
                BlockPos pos2 = box.getPos2();

                if (pos1 == null)
                {
                    pos1 = pos;
                }

                if (pos2 == null)
                {
                    pos2 = pos;
                }

                BlockPos posMin = PositionUtils.getMinCorner(PositionUtils.getMinCorner(pos1, pos2), pos);
                BlockPos posMax = PositionUtils.getMaxCorner(PositionUtils.getMaxCorner(pos1, pos2), pos);

                sel.setSelectedSubRegionCornerPos(posMin, Corner.CORNER_1);
                sel.setSelectedSubRegionCornerPos(posMax, Corner.CORNER_2);
            }
        }
    }

    public static void renameSubRegionBoxIfSingle(AreaSelection selection, String newName)
    {
        List<SelectionBox> boxes = selection.getAllSubRegionBoxes();

        // If the selection had only one box with the exact same name as the area selection itself,
        // then also rename that box to the new name.
        if (boxes.size() == 1 && boxes.get(0).getName().equals(selection.getName()))
        {
            selection.renameSubRegionBox(selection.getName(), newName);
        }
    }

    public void releaseGrabbedElement()
    {
        this.grabbedElement = null;
    }

    public void changeGrabDistance(Entity entity, double amount)
    {
        if (this.grabbedElement != null)
        {
            this.grabbedElement.changeGrabDistance(amount);
            this.grabbedElement.moveElement(entity);
        }
    }

    public void moveGrabbedElement(Entity entity)
    {
        if (this.grabbedElement != null)
        {
            this.grabbedElement.moveElement(entity);
        }
    }

    public void clear()
    {
        this.mode = Configs.Generic.DEFAULT_AREA_SELECTION_MODE.getValue();
        this.grabbedElement = null;
        this.currentSelectionId = null;
        this.selections.clear();
        this.readOnlySelections.clear();
    }

    @Nullable
    public BaseScreen getEditGui()
    {
        AreaSelection selection = this.getCurrentSelection();

        if (selection == null)
        {
            MessageDispatcher.warning().screenOrActionbar().translate("litematica.error.area_editor.open_gui.no_selection");
            return null;
        }

        if (this.getSelectionMode() == SelectionMode.MULTI_REGION)
        {
            return new NormalModeAreaEditorScreen(selection);
        }
        else if (this.getSelectionMode() == SelectionMode.SIMPLE)
        {
            return new SimpleModeAreaEditorScreen(selection);
        }

        return null;
    }

    public void openAreaEditorScreenWithParent()
    {
        this.openEditGui(GuiUtils.getCurrentScreen());
    }

    public void openEditGui(@Nullable GuiScreen parent)
    {
        BaseScreen gui = this.getEditGui();

        if (gui != null)
        {
            gui.setParent(parent);
            BaseScreen.openScreen(gui);
        }
    }

    public void loadFromJson(JsonObject obj)
    {
        this.clear();

        if (JsonUtils.hasString(obj, "current"))
        {
            String currentId = obj.get("current").getAsString();
            AreaSelection selection = this.tryLoadSelectionFromFile(currentId);

            if (selection != null)
            {
                this.selections.put(currentId, selection);
                this.setCurrentSelection(currentId);
            }
        }

        if (JsonUtils.hasString(obj, "mode"))
        {
            String name = obj.get("mode").getAsString();
            this.mode = SelectionMode.findValueByName(name, SelectionMode.VALUES);
        }
    }

    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();

        obj.add("mode", new JsonPrimitive(this.mode.getName()));

        try
        {
            for (Map.Entry<String, AreaSelection> entry : this.selections.entrySet())
            {
                JsonUtils.writeJsonToFile(entry.getValue().toJson(), Paths.get(entry.getKey()));
            }
        }
        catch (Exception e)
        {
            Litematica.logger.warn("Exception while writing area selections to disk", e);
        }

        AreaSelection current = this.currentSelectionId != null ? this.selections.get(this.currentSelectionId) : null;

        // Clear the loaded selections, except for the currently selected one
        this.selections.clear();
        this.readOnlySelections.clear();

        if (current != null)
        {
            obj.add("current", new JsonPrimitive(this.currentSelectionId));
            this.selections.put(this.currentSelectionId, current);
        }

        return obj;
    }

    private static class GrabbedElement
    {
        private final AreaSelection area;
        public final SelectionBox grabbedBox;
        public final SelectionBox originalBox;
        public final Vec3d grabPosition;
        public final Corner grabbedCorner;
        public double grabDistance;

        private GrabbedElement(AreaSelection area, SelectionBox box, Corner corner, Vec3d grabPosition, double grabDistance)
        {
            this.area = area;
            this.grabbedBox = box;
            this.grabbedCorner = corner;
            this.grabPosition = grabPosition;
            this.grabDistance = grabDistance;
            this.originalBox = new SelectionBox(box.getPos1(), box.getPos2(), "");
        }

        public void changeGrabDistance(double amount)
        {
            this.grabDistance += amount;
        }

        public void moveElement(Entity entity)
        {
            Vec3d newLookPos = entity.getPositionEyes(1f).add(entity.getLook(1f).scale(this.grabDistance));
            Vec3d change = newLookPos.subtract(this.grabPosition);

            if ((this.grabbedCorner == Corner.NONE || this.grabbedCorner == Corner.CORNER_1) && this.grabbedBox.getPos1() != null)
            {
                BlockPos pos = this.originalBox.getPos1().add(change.x, change.y, change.z);
                this.area.setSubRegionCornerPos(this.grabbedBox, Corner.CORNER_1, pos);
            }

            if ((this.grabbedCorner == Corner.NONE || this.grabbedCorner == Corner.CORNER_2) && this.grabbedBox.getPos2() != null)
            {
                BlockPos pos = this.originalBox.getPos2().add(change.x, change.y, change.z);
                this.area.setSubRegionCornerPos(this.grabbedBox, Corner.CORNER_2, pos);
            }
        }
    }
}
