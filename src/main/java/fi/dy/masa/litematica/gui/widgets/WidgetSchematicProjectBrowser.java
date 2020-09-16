package fi.dy.masa.litematica.gui.widgets;

import java.io.FileFilter;
import javax.annotation.Nullable;
import net.minecraft.util.math.BlockPos;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.projects.SchematicProject;
import fi.dy.masa.litematica.schematic.projects.SchematicVersion;
import fi.dy.masa.malilib.gui.BaseScreen;
import fi.dy.masa.malilib.gui.widget.list.entry.SelectionListener;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class WidgetSchematicProjectBrowser extends BaseFileBrowserWidget implements SelectionListener<DirectoryEntry>
{
    private final SelectionListener<DirectoryEntry> parentSelectionListener;
    protected final int infoWidth;
    @Nullable private SchematicProject selectedProject;

    public WidgetSchematicProjectBrowser(int x, int y, int width, int height, SelectionListener<DirectoryEntry> selectionListener)
    {
        super(x, y, width, height, DataManager.getSchematicsBaseDirectory(), DataManager.getSchematicsBaseDirectory(),
                DataManager.getDirectoryCache(), "version_control", null);

        this.parentSelectionListener = selectionListener;
        this.entryWidgetFixedHeight = 14;
        this.infoWidth = 170;

        this.getEntrySelectionHandler().setSelectionListener(this);
    }

    @Override
    protected FileFilter getFileFilter()
    {
        return WidgetAreaSelectionBrowser.JSON_FILTER;
    }

    @Override
    protected int getListMaxWidthForTotalWidth(int width)
    {
        return super.getListMaxWidthForTotalWidth(width) - this.infoWidth;
    }

    @Override
    public void onSelectionChange(@Nullable DirectoryEntry entry)
    {
        if (entry != null)
        {
            if (entry.getType() == DirectoryEntryType.FILE)
            {
                this.selectedProject = DataManager.getSchematicProjectsManager().loadProjectFromFile(entry.getFullPath(), false);
            }
            else
            {
                this.selectedProject = null;
            }
        }

        this.parentSelectionListener.onSelectionChange(entry);
    }

    @Override
    protected void drawAdditionalContents(int mouseX, int mouseY)
    {
        int x = this.getX() + this.getWidth() - this.infoWidth;
        int y = this.getY() + 4;
        int infoHeight = 100;
        RenderUtils.renderOutlinedBox(x + 1, y - 4, this.infoWidth, infoHeight, 0xA0000000, BaseScreen.COLOR_HORIZONTAL_BAR, this.getZLevel());

        SchematicProject project = this.selectedProject;

        if (project != null)
        {
            String str;
            String w = BaseScreen.TXT_WHITE;
            String r = BaseScreen.TXT_RST;
            int color = 0xFFB0B0B0;

            x += 5;
            str = StringUtils.translate("litematica.gui.label.schematic_projects.project");
            this.drawString(x, y, color, str);
            y += 12;
            this.drawString(x + 8, y, color, w + project.getName() + r);
            y += 12;
            int versionId = project .getCurrentVersionId();
            String strVer = w + (versionId >= 0 ? String.valueOf(versionId + 1) : "N/A") + r;
            str = StringUtils.translate("litematica.gui.label.schematic_projects.version", strVer, w + project.getVersionCount() + r);
            this.drawString(x, y, color, str);
            y += 12;
            SchematicVersion version = project.getCurrentVersion();

            if (version != null)
            {
                str = StringUtils.translate("litematica.gui.label.schematic_projects.origin");
                this.drawString(x, y, color, str);
                y += 12;

                BlockPos o = project.getOrigin();
                str = String.format("x: %s%d%s, y: %s%d%s, z: %s%d%s", w, o.getX(), r, w, o.getY(), r, w, o.getZ(), r);
                this.drawString(x + 8, y, color, str);
            }
        }
    }
}
