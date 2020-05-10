package fi.dy.masa.litematica.schematic.conversion;

import java.util.ArrayList;
import java.util.HashMap;
import javax.annotation.Nullable;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Dynamic;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.datafix.TypeReferences;

public class SchematicConversionMaps
{
    private static final Object2IntOpenHashMap<String> OLD_BLOCK_NAME_TO_SHIFTED_BLOCK_ID = DataFixUtils.make(new Object2IntOpenHashMap<>(), (map) -> { map.defaultReturnValue(-1); });
    private static final Int2ObjectOpenHashMap<String> ID_META_TO_UPDATED_NAME = new Int2ObjectOpenHashMap<>();
    private static final Object2IntOpenHashMap<IBlockState> BLOCKSTATE_TO_ID_META = DataFixUtils.make(new Object2IntOpenHashMap<>(), (map) -> { map.defaultReturnValue(-1); });
    private static final Int2ObjectOpenHashMap<IBlockState> ID_META_TO_BLOCKSTATE = new Int2ObjectOpenHashMap<>();
    private static final HashMap<String, String> OLD_NAME_TO_NEW_NAME = new HashMap<>();
    private static final HashMap<String, String> NEW_NAME_TO_OLD_NAME = new HashMap<>();
    private static final HashMap<NBTTagCompound, NBTTagCompound> OLD_STATE_TO_NEW_STATE = new HashMap<>();
    private static final HashMap<NBTTagCompound, NBTTagCompound> NEW_STATE_TO_OLD_STATE = new HashMap<>();
    static final ArrayList<ConversionData> CACHED_DATA = new ArrayList<>();

    public static void addEntry(int idMeta, String newStateString, String... oldStateStrings)
    {
        CACHED_DATA.add(new ConversionData(idMeta, newStateString, oldStateStrings));
    }

    public static void computeMaps()
    {
        clearMaps();
        addOverrides();

        for (ConversionData data : CACHED_DATA)
        {
            try
            {
                if (data.oldStateStrings.length > 0)
                {
                    NBTTagCompound oldStateTag = getStateTagFromString(data.oldStateStrings[0]);

                    if (oldStateTag != null)
                    {
                        String name = oldStateTag.getString("Name");
                        OLD_BLOCK_NAME_TO_SHIFTED_BLOCK_ID.putIfAbsent(name, data.idMeta & 0xFFF0);
                    }
                }

                NBTTagCompound newStateTag = getStateTagFromString(data.newStateString);

                if (newStateTag != null)
                {
                    addIdMetaToBlockState(data.idMeta, newStateTag, data.oldStateStrings);
                }
            }
            catch (Exception e)
            {
                Litematica.logger.warn("addEntry(): Exception while adding blockstate conversion map entry for ID '{}' (fixed state: '{}')", data.idMeta, data.newStateString, e);
            }
        }

        StateTagFixers_1_12_to_1_13_2.init();
    }

    public static String get_1_13_2_NameForIdMeta(int idMeta, String oldName)
    {
        String newName = ID_META_TO_UPDATED_NAME.get(idMeta);
        return newName != null ? newName : oldName;
    }

    @Nullable
    public static IBlockState get_1_13_2_StateForIdMeta(int idMeta)
    {
        return ID_META_TO_BLOCKSTATE.get(idMeta);
    }

    public static NBTTagCompound get_1_13_2_StateTagFor_1_12_Tag(NBTTagCompound oldStateTag)
    {
        NBTTagCompound tag = OLD_STATE_TO_NEW_STATE.get(oldStateTag);
        return tag != null ? tag : oldStateTag;
    }

    public static NBTTagCompound get_1_12_StateTagFor_1_13_2_Tag(NBTTagCompound newStateTag)
    {
        NBTTagCompound tag = NEW_STATE_TO_OLD_STATE.get(newStateTag);
        return tag != null ? tag : newStateTag;
    }

    public static int getOldNameToShiftedBlockId(String oldBlockname)
    {
        return OLD_BLOCK_NAME_TO_SHIFTED_BLOCK_ID.getInt(oldBlockname);
    }

    private static void addOverrides()
    {
        IBlockState air = Blocks.AIR.getDefaultState();
        BLOCKSTATE_TO_ID_META.put(air, 0);
        ID_META_TO_BLOCKSTATE.put(0, air);

        ID_META_TO_UPDATED_NAME.put(1648, "minecraft:melon");

        // These will get converted to the correct type in the state fixers
        ID_META_TO_UPDATED_NAME.put(2304, "minecraft:skeleton_skull");
        ID_META_TO_UPDATED_NAME.put(2305, "minecraft:skeleton_skull");
        ID_META_TO_UPDATED_NAME.put(2306, "minecraft:skeleton_wall_skull");
        ID_META_TO_UPDATED_NAME.put(2307, "minecraft:skeleton_wall_skull");
        ID_META_TO_UPDATED_NAME.put(2308, "minecraft:skeleton_wall_skull");
        ID_META_TO_UPDATED_NAME.put(2309, "minecraft:skeleton_wall_skull");
        ID_META_TO_UPDATED_NAME.put(2312, "minecraft:skeleton_skull");
        ID_META_TO_UPDATED_NAME.put(2313, "minecraft:skeleton_skull");
        ID_META_TO_UPDATED_NAME.put(2314, "minecraft:skeleton_wall_skull");
        ID_META_TO_UPDATED_NAME.put(2315, "minecraft:skeleton_wall_skull");
        ID_META_TO_UPDATED_NAME.put(2316, "minecraft:skeleton_wall_skull");
        ID_META_TO_UPDATED_NAME.put(2317, "minecraft:skeleton_wall_skull");

        // From the old Purple Shulker Box to the new default/non-colored Shulker Box
        ID_META_TO_UPDATED_NAME.put(3664, "minecraft:shulker_box");
        ID_META_TO_UPDATED_NAME.put(3665, "minecraft:shulker_box");
        ID_META_TO_UPDATED_NAME.put(3666, "minecraft:shulker_box");
        ID_META_TO_UPDATED_NAME.put(3667, "minecraft:shulker_box");
        ID_META_TO_UPDATED_NAME.put(3668, "minecraft:shulker_box");
        ID_META_TO_UPDATED_NAME.put(3669, "minecraft:shulker_box");
    }

    private static void clearMaps()
    {
        OLD_BLOCK_NAME_TO_SHIFTED_BLOCK_ID.clear();
        ID_META_TO_UPDATED_NAME.clear();

        BLOCKSTATE_TO_ID_META.clear();
        ID_META_TO_BLOCKSTATE.clear();

        OLD_NAME_TO_NEW_NAME.clear();
        NEW_NAME_TO_OLD_NAME.clear();

        OLD_STATE_TO_NEW_STATE.clear();
        NEW_STATE_TO_OLD_STATE.clear();
    }

    private static void addIdMetaToBlockState(int idMeta, NBTTagCompound newStateTag, String... oldStateStrings)
    {
        try
        {
            // The flattening map actually has outdated names for some blocks...
            // Ie. some blocks were renamed after the flattening, so we need to handle those here.
            String newName = newStateTag.getString("Name");
            String overriddenName = ID_META_TO_UPDATED_NAME.get(idMeta);

            if (overriddenName != null)
            {
                newName = overriddenName;
                newStateTag.putString("Name", newName);
            }

            // Store the id + meta => state maps before renaming the block for the state <=> state maps
            IBlockState state = NBTUtil.readBlockState(newStateTag);
            //System.out.printf("id: %5d, state: %s, tag: %s\n", idMeta, state, newStateTag);
            ID_META_TO_BLOCKSTATE.putIfAbsent(idMeta, state);

            // Don't override the id and meta for air, which is what unrecognized blocks will turn into
            BLOCKSTATE_TO_ID_META.putIfAbsent(state, idMeta);

            if (oldStateStrings.length > 0)
            {
                NBTTagCompound oldStateTag = getStateTagFromString(oldStateStrings[0]);
                String oldName = oldStateTag.getString("Name");

                // Don't run the vanilla block rename for overidden names
                if (overriddenName == null)
                {
                    newName = updateBlockName(newName);
                    newStateTag.putString("Name", newName);
                }

                if (oldName.equals(newName) == false)
                {
                    OLD_NAME_TO_NEW_NAME.putIfAbsent(oldName, newName);
                    NEW_NAME_TO_OLD_NAME.putIfAbsent(newName, oldName);
                }

                addOldStateToNewState(newStateTag, oldStateStrings);
            }
        }
        catch (Exception e)
        {
            Litematica.logger.warn("addIdMetaToBlockState(): Exception while adding blockstate conversion map entry for ID '{}'", idMeta, e);
        }
    }

    private static void addOldStateToNewState(NBTTagCompound newStateTagIn, String... oldStateStrings)
    {
        try
        {
            // A 1:1 mapping from the old state to the new state
            if (oldStateStrings.length == 1)
            {
                NBTTagCompound oldStateTag = getStateTagFromString(oldStateStrings[0]);

                if (oldStateTag != null)
                {
                    OLD_STATE_TO_NEW_STATE.putIfAbsent(oldStateTag, newStateTagIn);
                    NEW_STATE_TO_OLD_STATE.putIfAbsent(newStateTagIn, oldStateTag);
                }
            }
            // Multiple old states collapsed into one new state.
            // These are basically states where all the properties were not stored in metadata, but
            // some of the property values were calculated in the getActualState() method.
            else if (oldStateStrings.length > 1)
            {
                NBTTagCompound oldStateTag = getStateTagFromString(oldStateStrings[0]);

                if (oldStateTag != null)
                {
                    // Same property names and same number of properties - just remap the block name.
                    // FIXME Is this going to be correct for everything?
                    if (newStateTagIn.keySet().equals(oldStateTag.keySet()))
                    {
                        String oldBlockName = oldStateTag.getString("Name");
                        String newBlockName = OLD_NAME_TO_NEW_NAME.get(oldBlockName);

                        if (newBlockName != null && newBlockName.equals(oldBlockName) == false)
                        {
                            for (String oldStateString : oldStateStrings)
                            {
                                oldStateTag = getStateTagFromString(oldStateString);

                                if (oldStateTag != null)
                                {
                                    NBTTagCompound newStateTag = oldStateTag.copy();
                                    newStateTag.putString("Name", newBlockName);

                                    OLD_STATE_TO_NEW_STATE.putIfAbsent(oldStateTag, newStateTag);
                                    NEW_STATE_TO_OLD_STATE.putIfAbsent(newStateTag, oldStateTag);
                                }
                            }
                        }
                    }
                    // The property names or the set of properties changed, use the state fixers
                    else
                    {
                        for (String oldStateString : oldStateStrings)
                        {
                            oldStateTag = getStateTagFromString(oldStateString);

                            if (oldStateTag != null)
                            {
                                NBTTagCompound newStateTag = StateTagFixers_1_12_to_1_13_2.fixStateTag(oldStateTag);

                                OLD_STATE_TO_NEW_STATE.putIfAbsent(oldStateTag, newStateTag);
                                NEW_STATE_TO_OLD_STATE.putIfAbsent(newStateTag, oldStateTag);
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            Litematica.logger.warn("addOldStateToNewState(): Exception while adding new blockstate to old blockstate conversion map entry for '{}'", newStateTagIn, e);
        }
    }

    public static NBTTagCompound getStateTagFromString(String str)
    {
        try
        {
            return JsonToNBT.getTagFromJson(str.replace('\'', '"'));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static String updateBlockName(String oldName)
    {
        NBTTagString tagStr = new NBTTagString(oldName);

        return Minecraft.getInstance().getDataFixer().update(TypeReferences.BLOCK_NAME, new Dynamic<>(NBTDynamicOps.INSTANCE, tagStr),
                        1139, LitematicaSchematic.MINECRAFT_DATA_VERSION).getValue().getString();
    }

    public static class ConversionData
    {
        public final int idMeta;
        public final String newStateString;
        public final String[] oldStateStrings;

        private ConversionData(int idMeta, String newStateString, String[] oldStateStrings)
        {
            this.idMeta = idMeta;
            this.newStateString = newStateString;
            this.oldStateStrings = oldStateStrings;
        }
    }
}
