package fi.dy.masa.litematica.gui;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu;
import fi.dy.masa.litematica.gui.widgets.WidgetListPlacementSubRegions;
import fi.dy.masa.litematica.gui.widgets.WidgetPlacementSubRegion;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.BaseListScreen;
import fi.dy.masa.malilib.gui.TextInputScreen;
import fi.dy.masa.malilib.gui.button.BaseButton;
import fi.dy.masa.malilib.gui.button.GenericButton;
import fi.dy.masa.malilib.gui.button.OnOffButton;
import fi.dy.masa.malilib.gui.button.ButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.gui.util.GuiUtils;
import fi.dy.masa.malilib.gui.widget.WidgetCheckBox;
import fi.dy.masa.malilib.gui.widget.WidgetTextFieldBase;
import fi.dy.masa.malilib.gui.widget.WidgetTextFieldInteger;
import fi.dy.masa.malilib.message.MessageType;
import fi.dy.masa.malilib.message.MessageUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.malilib.util.PositionUtils.CoordinateType;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiPlacementConfiguration  extends BaseListScreen<SubRegionPlacement, WidgetPlacementSubRegion, WidgetListPlacementSubRegions>
                                        implements ISelectionListener<SubRegionPlacement>
{
    private final SchematicPlacement placement;
    private GenericButton buttonResetPlacement;
    private WidgetTextFieldBase textFieldRename;

    public GuiPlacementConfiguration(SchematicPlacement placement)
    {
        super(10, 62);
        this.placement = placement;
        this.title = StringUtils.translate("litematica.gui.title.configure_schematic_placement");
    }

    @Override
    protected int getListWidth()
    {
        return this.width - 150;
    }

    @Override
    protected int getListHeight()
    {
        return this.height - 84;
    }

    @Override
    public void initGui()
    {
        super.initGui();

        int scaledWidth = GuiUtils.getScaledWindowWidth();
        int width = Math.min(300, scaledWidth - 200);
        int x = 12;
        int y = 22;

        this.textFieldRename = new WidgetTextFieldBase(x, y + 1, width, 18, this.placement.getName());
        this.addWidget(this.textFieldRename);
        this.createButton(x + width + 4, y, -1, ButtonListener.Type.RENAME_PLACEMENT);

        String label = StringUtils.translate("litematica.gui.label.schematic_placement.sub_regions", this.placement.getSubRegionCount());
        this.addLabel(x + 2, y + 31, 0xFFFFFFFF, label);

        x = scaledWidth - 154;
        x -= this.createButton(x, y + 22, -1, ButtonListener.Type.TOGGLE_ALL_REGIONS_OFF) + 2;
        this.createButton(x, y + 22, -1, ButtonListener.Type.TOGGLE_ALL_REGIONS_ON);

        width = 120;
        x = this.width - width - 10;

        this.createButtonOnOff(x, y, width - 22, this.placement.isEnabled(), ButtonListener.Type.TOGGLE_ENABLED);
        this.createButton(x + width - 20, y, -1, ButtonListener.Type.COPY_PASTE_SETTINGS);
        y += 21;

        this.createButtonOnOff(x, y, width - 22, this.placement.isLocked(), ButtonListener.Type.TOGGLE_LOCKED);
        this.createButton(x + width - 20, y + 2, 20, ButtonListener.Type.TOGGLE_ENCLOSING_BOX);
        y += 21;

        this.createButtonOnOff(x, y, width, this.placement.ignoreEntities(), ButtonListener.Type.TOGGLE_ENTITIES);
        y += 23;
        x += 2;

        label = StringUtils.translate("litematica.gui.label.placement_settings.placement_origin");
        this.addLabel(x, y, 0xFFFFFFFF, label);
        y += 10;

        this.createCoordinateInput(x, y, 70, CoordinateType.X);
        this.createButton(x + 85, y + 1, -1, ButtonListener.Type.NUDGE_COORD_X);
        y += 18;

        this.createCoordinateInput(x, y, 70, CoordinateType.Y);
        this.createButton(x + 85, y + 1, -1, ButtonListener.Type.NUDGE_COORD_Y);
        y += 18;

        this.createCoordinateInput(x, y, 70, CoordinateType.Z);
        this.createButton(x + 85, y + 1, -1, ButtonListener.Type.NUDGE_COORD_Z);
        y += 20;
        x -= 2;

        this.createButton(x, y, width, ButtonListener.Type.MOVE_TO_PLAYER);
        y += 21;

        this.createButton(x, y, width, ButtonListener.Type.ROTATE);
        y += 21;

        this.createButton(x, y, width, ButtonListener.Type.MIRROR);
        y += 21;

        this.createButton(x, y, width, ButtonListener.Type.RESET_SUB_REGIONS);
        y += 21;

        this.createButtonOnOff(x, y, width, this.placement.getGridSettings().isEnabled(), ButtonListener.Type.GRID_SETTINGS);

        ButtonListenerChangeMenu.ButtonType type;

        // Move these buttons to the bottom (left) of the screen, if the height isn't enough for them
        // to fit below the other buttons
        if (GuiUtils.getScaledWindowHeight() < 328)
        {
            x = 10;
            y = this.height - 22;

            x += this.createButton(x, y, -1, ButtonListener.Type.OPEN_MATERIAL_LIST_GUI) + 1;
            x += this.createButton(x, y, -1, ButtonListener.Type.OPEN_VERIFIER_GUI) + 1;

            type = ButtonListenerChangeMenu.ButtonType.SCHEMATIC_PLACEMENTS;
            label = StringUtils.translate(type.getLabelKey());
            int buttonWidth = this.getStringWidth(label) + 10;
            GenericButton button = new GenericButton(x, y, buttonWidth, 20, label);
            this.addButton(button, new ButtonListenerChangeMenu(type, this.getParent()));
        }
        else
        {
            y += 32;
            this.createButton(x, y, width, ButtonListener.Type.OPEN_MATERIAL_LIST_GUI);
            y += 21;

            this.createButton(x, y, width, ButtonListener.Type.OPEN_VERIFIER_GUI);
            y += 32;

            type = ButtonListenerChangeMenu.ButtonType.SCHEMATIC_PLACEMENTS;
            label = StringUtils.translate(type.getLabelKey());
            int buttonWidth = this.getStringWidth(label) + 10;
            x = this.width - buttonWidth - 9;
            GenericButton button = new GenericButton(x, y, buttonWidth, 20, label);
            this.addButton(button, new ButtonListenerChangeMenu(type, this.getParent()));
        }

        this.updateElements();
    }

    private void createCoordinateInput(int x, int y, int width, CoordinateType type)
    {
        x += this.addLabel(x, y + 5, 0xFFFFFFFF, type.name() + ":").getWidth() + 4;

        BlockPos pos = this.placement.getOrigin();
        int value = PositionUtils.getCoordinate(pos, type);

        WidgetTextFieldInteger textField = new WidgetTextFieldInteger(x, y + 1, width, 16, value);
        textField.setUpdateListenerAlways(true);
        this.addTextField(textField, new TextFieldListener(type, this.placement, this));

        String hover = StringUtils.translate("litematica.hud.schematic_placement.hover_info.lock_coordinate");
        x += width + 20;
        WidgetCheckBox cb = new WidgetCheckBox(x, y + 3, LitematicaGuiIcons.CHECKBOX_UNSELECTED, LitematicaGuiIcons.CHECKBOX_SELECTED, "", hover);
        cb.setChecked(this.placement.isCoordinateLocked(type), false);
        cb.setListener(new CoordinateLockListener(type, this.placement));
        this.addWidget(cb);
    }

    private int createButtonOnOff(int x, int y, int width, boolean isCurrentlyOn, ButtonListener.Type type)
    {
        OnOffButton button = new OnOffButton(x, y, width, false, type.getTranslationKey(), isCurrentlyOn);
        button.addHoverString(type.getHoverText());
        this.addButton(button, new ButtonListener(type, this.placement, this));

        return button.getWidth();
    }

    private int createButton(int x, int y, int width, ButtonListener.Type type)
    {
        ButtonListener listener = new ButtonListener(type, this.placement, this);
        String label = "";

        switch (type)
        {
            case TOGGLE_ENCLOSING_BOX:
            {
                IGuiIcon icon = this.placement.shouldRenderEnclosingBox() ? LitematicaGuiIcons.ENCLOSING_BOX_ENABLED : LitematicaGuiIcons.ENCLOSING_BOX_DISABLED;
                boolean enabled = this.placement.shouldRenderEnclosingBox();
                String str = (enabled ? TXT_GREEN : TXT_RED) + StringUtils.translate("litematica.message.value." + (enabled ? "on" : "off")) + TXT_RST;
                String hover = StringUtils.translate("litematica.gui.button.schematic_placement.hover.enclosing_box", str);

                this.addButton(new GenericButton(x, y, icon, hover), listener);

                return icon.getWidth();
            }

            case ROTATE:
            {
                String value = PositionUtils.getRotationNameShort(this.placement.getRotation());
                label = type.getDisplayName(value);
                break;
            }

            case MIRROR:
            {
                String value = PositionUtils.getMirrorName(this.placement.getMirror());
                label = type.getDisplayName(value);
                break;
            }

            case NUDGE_COORD_X:
            case NUDGE_COORD_Y:
            case NUDGE_COORD_Z:
            {
                String hover = StringUtils.translate("litematica.gui.button.hover.plus_minus_tip");
                GenericButton button = new GenericButton(x, y, LitematicaGuiIcons.BUTTON_PLUS_MINUS_16, hover);
                this.addButton(button, listener);
                return width;
            }

            case COPY_PASTE_SETTINGS:
            {
                GenericButton button = new GenericButton(x, y, 20, 20, "", LitematicaGuiIcons.DUPLICATE, type.getHoverText());
                return this.addButton(button, listener).getWidth();
            }

            default:
                label = type.getDisplayName();
        }

        if (width == -1)
        {
            width = this.getStringWidth(label) + 10;
        }

        // These are right-aligned
        if (type == ButtonListener.Type.TOGGLE_ALL_REGIONS_OFF || type == ButtonListener.Type.TOGGLE_ALL_REGIONS_ON)
        {
            x -= width;
        }

        GenericButton button = new GenericButton(x, y, width, 20, label);

        this.addButton(button, listener);

        if (type == ButtonListener.Type.RESET_SUB_REGIONS)
        {
            this.buttonResetPlacement = button;
        }

        return width;
    }

    private void updateElements()
    {
        String label = StringUtils.translate("litematica.gui.button.schematic_placement.reset_sub_region_placements");;
        boolean enabled = true;

        if (this.placement.isRegionPlacementModified())
        {
            label = TXT_GOLD + label + TXT_RST;
        }
        else
        {
            enabled = false;
        }

        this.buttonResetPlacement.setDisplayString(label);
        this.buttonResetPlacement.setEnabled(enabled);
    }

    public SchematicPlacement getSchematicPlacement()
    {
        return this.placement;
    }

    @Override
    protected ISelectionListener<SubRegionPlacement> getSelectionListener()
    {
        return this;
    }

    @Override
    public void onSelectionChange(SubRegionPlacement entry)
    {
        this.placement.setSelectedSubRegionName(entry != null && entry.getName().equals(this.placement.getSelectedSubRegionName()) == false ? entry.getName() : null);
    }

    @Override
    protected WidgetListPlacementSubRegions createListWidget(int listX, int listY)
    {
        return new WidgetListPlacementSubRegions(listX, listY, this.getListWidth(), this.getListHeight(), this);
    }

    private static class ButtonListener implements ButtonActionListener
    {
        private final GuiPlacementConfiguration parent;
        private final SchematicPlacementManager manager;
        private final SchematicPlacement placement;
        private final Type type;

        public ButtonListener(Type type, SchematicPlacement placement, GuiPlacementConfiguration parent)
        {
            this.parent = parent;
            this.manager = DataManager.getSchematicPlacementManager();
            this.placement = placement;
            this.type = type;
        }

        @Override
        public void actionPerformedWithButton(BaseButton button, int mouseButton)
        {
            Minecraft mc = Minecraft.getMinecraft();
            int amount = mouseButton == 1 ? -1 : 1;
            if (BaseScreen.isShiftDown()) { amount *= 8; }
            if (BaseScreen.isAltDown()) { amount *= 4; }
            BlockPos oldOrigin = this.placement.getOrigin();
            this.parent.setNextMessageType(MessageType.ERROR);

            switch (this.type)
            {
                case RENAME_PLACEMENT:
                    this.placement.setName(this.parent.textFieldRename.getText());
                    break;

                case ROTATE:
                {
                    boolean reverse = mouseButton == 1;
                    Rotation rotation = fi.dy.masa.malilib.util.PositionUtils.cycleRotation(this.placement.getRotation(), reverse);
                    this.manager.setRotation(this.placement, rotation, this.parent);
                    break;
                }

                case MIRROR:
                {
                    boolean reverse = mouseButton == 1;
                    Mirror mirror = fi.dy.masa.malilib.util.PositionUtils.cycleMirror(this.placement.getMirror(), reverse);
                    this.manager.setMirror(this.placement, mirror, this.parent);
                    break;
                }

                case COPY_PASTE_SETTINGS:
                {
                    if (isShiftDown())
                    {
                        if (mouseButton == 1)
                        {
                            // Ctrl + Shift + Right click: load settings from clip board
                            if (isCtrlDown())
                            {
                                String str = GuiScreen.getClipboardString();

                                if (this.manager.loadPlacementSettings(this.placement, str, this.parent))
                                {
                                    MessageUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "litematica.message.placement.settings_loaded_from_clipboard");
                                }
                            }
                        }
                        // Shift + left click: Copy settings to clip board
                        else
                        {
                            String str = JsonUtils.jsonToString(this.placement.baseSettingsToJson(true), true);
                            GuiScreen.setClipboardString(str);
                            MessageUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "litematica.message.placement.settings_copied_to_clipboard");
                        }
                    }
                    else
                    {
                        JsonObject origJson = this.placement.baseSettingsToJson(true);

                        openPopupGui(new TextInputScreen("litematica.gui.title.schematic_placement.copy_or_load_settings", origJson.toString(), this.parent, (str) -> {
                            JsonElement el = JsonUtils.parseJsonFromString(str);

                            if (el != null && el.isJsonObject() && el.getAsJsonObject().equals(origJson) == false)
                            {
                                if (this.manager.loadPlacementSettings(this.placement, el.getAsJsonObject(), this.parent))
                                {
                                    MessageUtils.showGuiOrInGameMessage(MessageType.SUCCESS, "litematica.message.placement.settings_loaded_from_clipboard");
                                }
                            }

                            return true;
                        }));
                    }

                    break;
                }

                case MOVE_TO_PLAYER:
                {
                    BlockPos pos = new BlockPos(mc.player.getPositionVector());
                    this.manager.setOrigin(this.placement, pos, this.parent);
                    break;
                }

                case NUDGE_COORD_X:
                    this.manager.setOrigin(this.placement, oldOrigin.add(amount, 0, 0), this.parent);
                    break;

                case NUDGE_COORD_Y:
                    this.manager.setOrigin(this.placement, oldOrigin.add(0, amount, 0), this.parent);
                    break;

                case NUDGE_COORD_Z:
                    this.manager.setOrigin(this.placement, oldOrigin.add(0, 0, amount), this.parent);
                    break;

                case TOGGLE_ENABLED:
                    this.manager.toggleEnabled(this.placement);
                    break;

                case TOGGLE_LOCKED:
                    this.placement.toggleLocked();
                    break;

                case TOGGLE_ENTITIES:
                    this.manager.toggleIgnoreEntities(this.placement);
                    break;

                case TOGGLE_ENCLOSING_BOX:
                    this.placement.toggleRenderEnclosingBox();
                    break;

                case RESET_SUB_REGIONS:
                    this.manager.resetAllSubRegionsToSchematicValues(this.placement, this.parent);
                    break;

                case TOGGLE_ALL_REGIONS_ON:
                case TOGGLE_ALL_REGIONS_OFF:
                {
                    boolean state = this.type == Type.TOGGLE_ALL_REGIONS_ON;
                    this.manager.setSubRegionsEnabled(this.placement, state, this.parent.getListWidget().getCurrentEntries());
                    break;
                }

                case OPEN_VERIFIER_GUI:
                {
                    GuiSchematicVerifier gui = new GuiSchematicVerifier(this.placement);
                    gui.setParent(this.parent);
                    BaseScreen.openGui(gui);
                    break;
                }

                case OPEN_MATERIAL_LIST_GUI:
                {
                    MaterialListBase materialList = this.placement.getMaterialList();
                    GuiMaterialList gui = new GuiMaterialList(materialList);
                    DataManager.setMaterialList(materialList); // Remember the last opened material list for the hotkey to (re-) open it
                    gui.setParent(this.parent);
                    BaseScreen.openGui(gui);
                    break;
                }

                case GRID_SETTINGS:
                {
                    if (BaseScreen.isShiftDown())
                    {
                        this.placement.getGridSettings().toggleEnabled();
                        this.manager.updateGridPlacementsFor(this.placement);
                    }
                    else
                    {
                        GuiPlacementGridSettings gui = new GuiPlacementGridSettings(this.placement, this.parent);
                        BaseScreen.openPopupGui(gui);
                    }
                    break;
                }
            }

            this.parent.initGui(); // Re-create buttons/text fields
        }

        public enum Type
        {
            RENAME_PLACEMENT        ("litematica.gui.button.rename"),
            ROTATE                  ("litematica.gui.button.rotation_value"),
            MIRROR                  ("litematica.gui.button.mirror_value"),
            COPY_PASTE_SETTINGS     ("", "litematica.gui.button.hover.schematic_placement.copy_paste_settings"),
            MOVE_TO_PLAYER          ("litematica.gui.button.move_to_player"),
            GRID_SETTINGS           ("litematica.gui.button.schematic_placement.grid_settings", "litematica.gui.button.schematic_placement.hover.grid_settings_shift_to_toggle"),
            NUDGE_COORD_X           (""),
            NUDGE_COORD_Y           (""),
            NUDGE_COORD_Z           (""),
            TOGGLE_ENABLED          ("litematica.gui.button.schematic_placements.placement_enabled"),
            TOGGLE_LOCKED           ("litematica.gui.button.schematic_placements.locked", "litematica.gui.button.schematic_placement.hover.lock"),
            TOGGLE_ENTITIES         ("litematica.gui.button.schematic_placement.ignore_entities"),
            TOGGLE_ENCLOSING_BOX    (""),
            TOGGLE_ALL_REGIONS_ON   ("litematica.gui.button.schematic_placement.toggle_all_on"),
            TOGGLE_ALL_REGIONS_OFF  ("litematica.gui.button.schematic_placement.toggle_all_off"),
            RESET_SUB_REGIONS       (""),
            OPEN_VERIFIER_GUI       ("litematica.gui.button.schematic_verifier"),
            OPEN_MATERIAL_LIST_GUI  ("litematica.gui.button.material_list");

            private final String translationKey;
            @Nullable private final String hoverText;

            private Type(String translationKey)
            {
                this(translationKey, null);
            }

            private Type(String translationKey, @Nullable String hoverText)
            {
                this.translationKey = translationKey;
                this.hoverText = hoverText;
            }

            public String getTranslationKey()
            {
                return this.translationKey;
            }

            public String getDisplayName(Object... args)
            {
                return StringUtils.translate(this.translationKey, args);
            }

            @Nullable
            public String getHoverText()
            {
                return this.hoverText;
            }
        }
    }

    private static class TextFieldListener implements ITextFieldListener
    {
        private final GuiPlacementConfiguration parent;
        private final SchematicPlacementManager manager;
        private final SchematicPlacement placement;
        private final CoordinateType type;

        public TextFieldListener(CoordinateType type, SchematicPlacement placement, GuiPlacementConfiguration parent)
        {
            this.manager = DataManager.getSchematicPlacementManager();
            this.placement = placement;
            this.type = type;
            this.parent = parent;
        }

        @Override
        public void onTextChange(String newText)
        {
            try
            {
                int value = Integer.parseInt(newText);
                BlockPos posOld = this.placement.getOrigin();
                this.parent.setNextMessageType(MessageType.ERROR);

                switch (this.type)
                {
                    case X: this.manager.setOrigin(this.placement, new BlockPos(value, posOld.getY(), posOld.getZ()), this.parent); break;
                    case Y: this.manager.setOrigin(this.placement, new BlockPos(posOld.getX(), value, posOld.getZ()), this.parent); break;
                    case Z: this.manager.setOrigin(this.placement, new BlockPos(posOld.getX(), posOld.getY(), value), this.parent); break;
                }

                this.parent.updateElements();
            }
            catch (NumberFormatException e) {}
        }
    }

    private static class CoordinateLockListener implements ISelectionListener<WidgetCheckBox>
    {
        private final SchematicPlacement placement;
        private final CoordinateType type;

        private CoordinateLockListener(CoordinateType type, SchematicPlacement placement)
        {
            this.type = type;
            this.placement = placement;
        }

        @Override
        public void onSelectionChange(WidgetCheckBox entry)
        {
            this.placement.setCoordinateLocked(this.type, entry.isChecked());
        }
    }
}
