package fi.dy.masa.litematica.gui;

import javax.annotation.Nullable;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier.BlockMismatchEntry;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicVerificationResults;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicVerificationResult;
import fi.dy.masa.litematica.render.infohud.InfoHud;
import fi.dy.masa.litematica.render.infohud.RenderPhase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import fi.dy.masa.litematica.util.BlockInfoListType;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.gui.BaseListScreen;
import fi.dy.masa.malilib.gui.widget.button.BaseButton;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.gui.widget.button.ButtonActionListener;
import fi.dy.masa.malilib.gui.widget.list.entry.SelectionListener;
import fi.dy.masa.malilib.gui.util.GuiUtils;
import fi.dy.masa.malilib.overlay.message.MessageOutput;
import fi.dy.masa.malilib.listener.TaskCompletionListener;
import fi.dy.masa.malilib.overlay.message.MessageUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiSchematicVerifier   extends BaseListScreen<BlockMismatchEntry, WidgetSchematicVerificationResult, WidgetListSchematicVerificationResults>
                                    implements SelectionListener<BlockMismatchEntry>, TaskCompletionListener
{
    private static SchematicVerifier verifierLast;
    // static to remember the mode over GUI close/open cycles
    private static MismatchType resultMode = MismatchType.ALL;

    private final SchematicPlacement placement;
    private final SchematicVerifier verifier;

    public GuiSchematicVerifier(SchematicPlacement placement)
    {
        super(10, 60, 20, 94);

        this.title = StringUtils.translate("litematica.gui.title.schematic_verifier", placement.getName());
        this.placement = placement;
        this.verifier = placement.getSchematicVerifier();

        SchematicVerifier verifier = placement.getSchematicVerifier();

        if (verifier != verifierLast || verifier.isActive())
        {
            WidgetSchematicVerificationResult.setMaxNameLengths(verifier.getMismatchOverviewCombined());
            verifierLast = verifier;
        }
    }

    @Override
    protected void initScreen()
    {
        super.initScreen();

        int x = 12;
        int y = 20;
        int buttonWidth;
        String label;
        GenericButton button;

        x += this.createButton(x, y, -1, ButtonListener.Type.START) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.STOP) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.RESET_VERIFIER) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.SET_LIST_TYPE) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.RESET_IGNORED) + 4;
        this.createButton(x, y, -1, ButtonListener.Type.TOGGLE_INFO_HUD);
        y += 22;

        x = 12;
        x += this.createButton(x, y, -1, ButtonListener.Type.SET_RESULT_MODE_ALL) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.SET_RESULT_MODE_WRONG_BLOCKS) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.SET_RESULT_MODE_WRONG_STATES) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.SET_RESULT_MODE_EXTRA) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.SET_RESULT_MODE_MISSING) + 4;
        x += this.createButton(x, y, -1, ButtonListener.Type.SET_RESULT_MODE_CORRECT) + 4;

        y = this.height - 32;

        if (this.verifier.isActive())
        {
            String str = StringUtils.translate("litematica.gui.label.schematic_verifier.status.verifying", this.verifier.getUnseenChunks(), this.verifier.getTotalChunks());
            this.addLabel(12, y, 0xFFF0F0F0, str);
        }
        else if (this.verifier.isFinished())
        {
            Integer wb = this.verifier.getMismatchedBlocks();
            Integer ws = this.verifier.getMismatchedStates();
            Integer m = this.verifier.getMissingBlocks();
            Integer e = this.verifier.getExtraBlocks();
            Integer c = this.verifier.getCorrectStatesCount();
            Integer t = this.verifier.getSchematicTotalBlocks();
            String str = StringUtils.translate("litematica.gui.label.schematic_verifier.status.done_errors", wb, ws, m, e);
            this.addLabel(12, y, 0xFFF0F0F0, str);
            str = StringUtils.translate("litematica.gui.label.schematic_verifier.status.done_correct_total", c, t);
            this.addLabel(12, y + 12, 0xFFF0F0F0, str);
        }

        ButtonListenerChangeMenu.ButtonType type = ButtonListenerChangeMenu.ButtonType.MAIN_MENU;
        label = StringUtils.translate(type.getLabelKey());
        buttonWidth = this.getStringWidth(label) + 20;
        x = this.width - buttonWidth - 10;
        button = new GenericButton(x, y, buttonWidth, 20, label);
        this.addButton(button, new ButtonListenerChangeMenu(type, this.getParent()));
    }

    private int createButton(int x, int y, int width, ButtonListener.Type type)
    {
        ButtonListener listener = new ButtonListener(type, this.placement, this);
        boolean enabled = true;
        String label = "";

        switch (type)
        {
            case SET_RESULT_MODE_ALL:
                label = MismatchType.ALL.getDisplayname();
                enabled = resultMode != MismatchType.ALL;
                break;

            case SET_RESULT_MODE_WRONG_BLOCKS:
                label = MismatchType.WRONG_BLOCK.getDisplayname();
                enabled = resultMode != MismatchType.WRONG_BLOCK;
                break;

            case SET_RESULT_MODE_WRONG_STATES:
                label = MismatchType.WRONG_STATE.getDisplayname();
                enabled = resultMode != MismatchType.WRONG_STATE;
                break;

            case SET_RESULT_MODE_EXTRA:
                label = MismatchType.EXTRA.getDisplayname();
                enabled = resultMode != MismatchType.EXTRA;
                break;

            case SET_RESULT_MODE_MISSING:
                label = MismatchType.MISSING.getDisplayname();
                enabled = resultMode != MismatchType.MISSING;
                break;

            case SET_RESULT_MODE_CORRECT:
                label = MismatchType.CORRECT_STATE.getDisplayname();
                enabled = resultMode != MismatchType.CORRECT_STATE;
                break;

            case START:
                if (this.verifier.isPaused())
                {
                    label = StringUtils.translate("litematica.gui.button.schematic_verifier.resume");
                }
                else
                {
                    label = StringUtils.translate("litematica.gui.button.schematic_verifier.start");
                    enabled = this.verifier.isActive() == false;
                }
                break;

            case STOP:
                label = StringUtils.translate("litematica.gui.button.schematic_verifier.stop");
                enabled = this.verifier.isActive();
                break;

            case RESET_VERIFIER:
            {
                label = StringUtils.translate("litematica.gui.button.schematic_verifier.reset_verifier");
                enabled = this.verifier.isActive() || this.verifier.isPaused() || this.verifier.isFinished();
                break;
            }

            case SET_LIST_TYPE:
            {
                String str = this.placement.getSchematicVerifierType().getDisplayName();
                label = StringUtils.translate("litematica.gui.button.schematic_verifier.range_type", str);
                break;
            }

            case RESET_IGNORED:
                label = StringUtils.translate("litematica.gui.button.schematic_verifier.reset_ignored");
                enabled = this.verifier.getIgnoredMismatches().size() > 0;
                break;

            case TOGGLE_INFO_HUD:
            {
                boolean val = InfoHud.getInstance().isEnabled() && this.verifier.getShouldRenderText(RenderPhase.POST);
                String str = (val ? TXT_GREEN : TXT_RED) + StringUtils.translate("litematica.message.value." + (val ? "on" : "off")) + TXT_RST;
                label = StringUtils.translate("litematica.gui.button.schematic_verifier.toggle_info_hud", str);
                break;
            }

            default:
        }

        if (width == -1)
        {
            width = this.getStringWidth(label) + 10;
        }

        GenericButton button = new GenericButton(x, y, width, 20, label);
        button.setEnabled(enabled);
        this.addButton(button, listener);

        return width;
    }

    public SchematicPlacement getPlacement()
    {
        return this.placement;
    }

    public MismatchType getResultMode()
    {
        return resultMode;
    }

    private void setResultMode(MismatchType type)
    {
        resultMode = type;
    }

    @Override
    public void onTaskCompleted()
    {
        net.minecraft.client.gui.GuiScreen gui = GuiUtils.getCurrentScreen();

        if (gui == this || gui == null)
        {
            WidgetSchematicVerificationResult.setMaxNameLengths(this.verifier.getMismatchOverviewCombined());

            if (gui == this)
            {
                this.initGui();
            }
        }
    }

    @Override
    public void onSelectionChange(@Nullable BlockMismatchEntry entry)
    {
        if (entry != null)
        {
            // Main header - show the currently missing/unseen chunks
            if (entry.type == BlockMismatchEntry.Type.HEADER)
            {
                this.verifier.updateRequiredChunksStringList();
            }
            // Category title - show all mismatches of that type
            else if (entry.type == BlockMismatchEntry.Type.CATEGORY_TITLE)
            {
                this.verifier.toggleMismatchCategorySelected(entry.mismatchType);
            }
            // A specific mismatch pair - show only those state pairs
            else if (entry.type == BlockMismatchEntry.Type.DATA)
            {
                this.verifier.toggleMismatchEntrySelected(entry.blockMismatch);
            }

            if (Configs.InfoOverlays.VERIFIER_OVERLAY_ENABLED.getBooleanValue() == false)
            {
                String name = Configs.InfoOverlays.VERIFIER_OVERLAY_ENABLED.getName();
                String hotkeyName = Hotkeys.TOGGLE_VERIFIER_OVERLAY_RENDERING.getName();
                String hotkeyVal = Hotkeys.TOGGLE_VERIFIER_OVERLAY_RENDERING.getKeyBind().getKeysDisplayString();
                MessageUtils.showGuiOrInGameMessage(MessageOutput.WARNING, "litematica.message.warn.schematic_verifier.overlay_disabled", name, hotkeyName, hotkeyVal);
            }
        }
    }

    @Override
    protected SelectionListener<BlockMismatchEntry> getSelectionListener()
    {
        return this;
    }

    @Override
    protected WidgetListSchematicVerificationResults createListWidget(int listX, int listY)
    {
        return new WidgetListSchematicVerificationResults(listX, listY, this.getListWidth(), this.getListHeight(), this);
    }

    public static class BlockMismatchEntry
    {
        public final Type type;
        @Nullable
        public final MismatchType mismatchType;
        @Nullable
        public final BlockMismatch blockMismatch;
        @Nullable
        public final String header1;
        @Nullable
        public final String header2;

        public BlockMismatchEntry(MismatchType mismatchType, String title)
        {
            this.type = Type.CATEGORY_TITLE;
            this.mismatchType = mismatchType;
            this.blockMismatch = null;
            this.header1 = title;
            this.header2 = null;
        }

        public BlockMismatchEntry(String header1, String header2)
        {
            this.type = Type.HEADER;
            this.mismatchType = null;
            this.blockMismatch = null;
            this.header1 = header1;
            this.header2 = header2;
        }

        public BlockMismatchEntry(MismatchType mismatchType, BlockMismatch blockMismatch)
        {
            this.type = Type.DATA;
            this.mismatchType = mismatchType;
            this.blockMismatch = blockMismatch;
            this.header1 = null;
            this.header2 = null;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((blockMismatch == null) ? 0 : blockMismatch.hashCode());
            result = prime * result + ((header1 == null) ? 0 : header1.hashCode());
            result = prime * result + ((header2 == null) ? 0 : header2.hashCode());
            result = prime * result + ((mismatchType == null) ? 0 : mismatchType.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            BlockMismatchEntry other = (BlockMismatchEntry) obj;
            if (blockMismatch == null)
            {
                if (other.blockMismatch != null)
                    return false;
            }
            else if (!blockMismatch.equals(other.blockMismatch))
                return false;
            if (header1 == null)
            {
                if (other.header1 != null)
                    return false;
            }
            else if (!header1.equals(other.header1))
                return false;
            if (header2 == null)
            {
                if (other.header2 != null)
                    return false;
            }
            else if (!header2.equals(other.header2))
                return false;
            if (mismatchType != other.mismatchType)
                return false;
            if (type != other.type)
                return false;
            return true;
        }

        public enum Type
        {
            HEADER,
            CATEGORY_TITLE,
            DATA;
        }
    }

    private static class ButtonListener implements ButtonActionListener
    {
        private final GuiSchematicVerifier parent;
        private final Type type;

        public ButtonListener(Type type, SchematicPlacement placement, GuiSchematicVerifier parent)
        {
            this.parent = parent;
            this.type = type;
        }

        @Override
        public void actionPerformedWithButton(BaseButton button, int mouseButton)
        {
            switch (this.type)
            {
                case SET_RESULT_MODE_ALL:
                    this.parent.setResultMode(MismatchType.ALL);
                    break;

                case SET_RESULT_MODE_WRONG_BLOCKS:
                    this.parent.setResultMode(MismatchType.WRONG_BLOCK);
                    break;

                case SET_RESULT_MODE_WRONG_STATES:
                    this.parent.setResultMode(MismatchType.WRONG_STATE);
                    break;

                case SET_RESULT_MODE_EXTRA:
                    this.parent.setResultMode(MismatchType.EXTRA);
                    break;

                case SET_RESULT_MODE_MISSING:
                    this.parent.setResultMode(MismatchType.MISSING);
                    break;

                case SET_RESULT_MODE_CORRECT:
                    this.parent.setResultMode(MismatchType.CORRECT_STATE);
                    break;

                case START:
                    WorldSchematic world = SchematicWorldHandler.getSchematicWorld();

                    if (world != null)
                    {
                        SchematicVerifier verifier = this.parent.verifier;

                        if (this.parent.verifier.isPaused())
                        {
                            verifier.resume();
                        }
                        else
                        {
                            verifier.startVerification(this.parent.mc.world, world, this.parent.placement, this.parent);
                        }
                    }
                    else
                    {
                        this.parent.addMessage(MessageOutput.ERROR, "litematica.error.generic.schematic_world_not_loaded");
                    }
                    verifierLast = null; // force re-calculating the column lengths
                    break;

                case STOP:
                    this.parent.verifier.stopVerification();
                    break;

                case RESET_VERIFIER:
                    this.parent.verifier.reset();
                    break;

                case SET_LIST_TYPE:
                    SchematicPlacement placement = this.parent.placement;
                    this.parent.verifier.reset();
                    BlockInfoListType type = placement.getSchematicVerifierType();
                    placement.setSchematicVerifierType((BlockInfoListType) type.cycle(mouseButton == 0));
                    break;

                case RESET_IGNORED:
                    this.parent.verifier.resetIgnoredStateMismatches();
                    break;

                case TOGGLE_INFO_HUD:
                    SchematicVerifier verifier = this.parent.verifier;
                    verifier.toggleShouldRenderInfoHUD();

                    if (verifier.getShouldRenderText(RenderPhase.POST))
                    {
                        InfoHud.getInstance().addInfoHudRenderer(verifier, true);
                    }
                    else
                    {
                        InfoHud.getInstance().removeInfoHudRenderer(verifier, false);
                    }

                    break;
            }

            this.parent.initGui(); // Re-create buttons/text fields
        }

        public enum Type
        {
            SET_RESULT_MODE_ALL,
            SET_RESULT_MODE_WRONG_BLOCKS,
            SET_RESULT_MODE_WRONG_STATES,
            SET_RESULT_MODE_EXTRA,
            SET_RESULT_MODE_MISSING,
            SET_RESULT_MODE_CORRECT,
            START,
            STOP,
            RESET_VERIFIER,
            SET_LIST_TYPE,
            RESET_IGNORED,
            TOGGLE_INFO_HUD;
        }
    }
}
