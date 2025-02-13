package litematica.materials;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.common.collect.ImmutableList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFlowerPot;
import net.minecraft.block.BlockFlowerPot.EnumFlowerType;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import malilib.config.util.ConfigUtils;
import malilib.util.FileUtils;
import malilib.util.data.Constants;
import malilib.util.game.wrap.ItemWrap;
import malilib.util.game.wrap.NbtWrap;
import malilib.util.position.BlockPos;
import malilib.util.position.Vec3i;
import litematica.Litematica;
import litematica.Reference;
import litematica.util.WorldUtils;
import litematica.world.WorldSchematic;

public class MaterialCache
{
    private static final MaterialCache INSTANCE = new MaterialCache();

    protected final IdentityHashMap<IBlockState, ItemStack> buildItemsForStates = new IdentityHashMap<>();
    protected final IdentityHashMap<IBlockState, ItemStack> displayItemsForStates = new IdentityHashMap<>();
    protected final WorldSchematic tempWorld;
    protected final BlockPos checkPos;
    protected boolean hasReadFromFile;
    protected boolean dirty;

    private MaterialCache()
    {
        WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT);

        this.tempWorld = new WorldSchematic(null, settings, -1, EnumDifficulty.PEACEFUL, Minecraft.getMinecraft().profiler);
        this.checkPos = new BlockPos(8, 0, 8);

        WorldUtils.loadChunksClientWorld(this.tempWorld, this.checkPos, new Vec3i(1, 1, 1));
    }

    public static MaterialCache getInstance()
    {
        /*
        if (INSTANCE.hasReadFromFile == false)
        {
            INSTANCE.readFromFile();
        }
        */

        return INSTANCE;
    }

    public void clearCache()
    {
        this.buildItemsForStates.clear();
    }

    public ItemStack getRequiredBuildItemForState(IBlockState state)
    {
        return this.getRequiredBuildItemForState(state, this.tempWorld, this.checkPos);
    }

    public ItemStack getRequiredBuildItemForState(IBlockState state, World world, BlockPos pos)
    {
        ItemStack stack = this.buildItemsForStates.get(state);

        if (stack == null)
        {
            stack = this.getItemForStateFromWorld(state, world, pos, true);
        }

        return stack;
    }

    public ItemStack getItemForDisplayNameForState(IBlockState state)
    {
        ItemStack stack = this.displayItemsForStates.get(state);

        if (stack == null)
        {
            stack = this.getItemForStateFromWorld(state, this.tempWorld, this.checkPos, false);
        }

        return stack;
    }

    protected ItemStack getItemForStateFromWorld(IBlockState state, World world, BlockPos pos, boolean isBuildItem)
    {
        ItemStack stack = isBuildItem ? this.getStateToItemOverride(state) : null;

        if (stack == null)
        {
            world.setBlockState(pos, state, 0x14);
            stack = state.getBlock().getItem(world, pos, state);
        }

        if (stack == null || ItemWrap.isEmpty(stack))
        {
            stack = ItemStack.EMPTY;
        }
        else
        {
            this.overrideStackSize(state, stack);
        }

        if (isBuildItem)
        {
            this.buildItemsForStates.put(state, stack);
        }
        else
        {
            this.displayItemsForStates.put(state, stack);
        }

        this.dirty = true;

        return stack;
    }

    public boolean requiresMultipleItems(IBlockState state)
    {
        Block block = state.getBlock();

        if (block == Blocks.FLOWER_POT && state.getValue(BlockFlowerPot.CONTENTS) != BlockFlowerPot.EnumFlowerType.EMPTY)
        {
            return true;
        }

        return false;
    }

    public ImmutableList<ItemStack> getItems(IBlockState state)
    {
        return this.getItems(state, this.tempWorld, this.checkPos);
    }

    public ImmutableList<ItemStack> getItems(IBlockState state, World world, BlockPos pos)
    {
        Block block = state.getBlock();

        if (block == Blocks.FLOWER_POT && state.getValue(BlockFlowerPot.CONTENTS) != BlockFlowerPot.EnumFlowerType.EMPTY)
        {
            // Nice & clean >_>
            EnumFlowerType type = state.getValue(BlockFlowerPot.CONTENTS);
            ItemStack plant = null;

            switch (type)
            {
                case ACACIA_SAPLING:    plant = new ItemStack(Blocks.SAPLING, 1, 4); break;
                case ALLIUM:            plant = new ItemStack(Blocks.RED_FLOWER, 1, 2); break;
                case BIRCH_SAPLING:     plant = new ItemStack(Blocks.SAPLING, 1, 2); break;
                case BLUE_ORCHID:       plant = new ItemStack(Blocks.RED_FLOWER, 1, 1); break;
                case CACTUS:            plant = new ItemStack(Blocks.CACTUS, 1, 0); break;
                case DANDELION:         plant = new ItemStack(Blocks.YELLOW_FLOWER, 1, 0); break;
                case DARK_OAK_SAPLING:  plant = new ItemStack(Blocks.SAPLING, 1, 5); break;
                case DEAD_BUSH:         plant = new ItemStack(Blocks.DEADBUSH, 1, 0); break;
                case FERN:              plant = new ItemStack(Blocks.TALLGRASS, 1, 2); break;
                case HOUSTONIA:         plant = new ItemStack(Blocks.RED_FLOWER, 1, 3); break;
                case JUNGLE_SAPLING:    plant = new ItemStack(Blocks.SAPLING, 1, 3); break;
                case MUSHROOM_BROWN:    plant = new ItemStack(Blocks.BROWN_MUSHROOM, 1, 0); break;
                case MUSHROOM_RED:      plant = new ItemStack(Blocks.RED_MUSHROOM, 1, 0); break;
                case OAK_SAPLING:       plant = new ItemStack(Blocks.SAPLING, 1, 0); break;
                case ORANGE_TULIP:      plant = new ItemStack(Blocks.RED_FLOWER, 1, 5); break;
                case OXEYE_DAISY:       plant = new ItemStack(Blocks.RED_FLOWER, 1, 8); break;
                case PINK_TULIP:        plant = new ItemStack(Blocks.RED_FLOWER, 1, 7); break;
                case POPPY:             plant = new ItemStack(Blocks.RED_FLOWER, 1, 0); break;
                case RED_TULIP:         plant = new ItemStack(Blocks.RED_FLOWER, 1, 4); break;
                case SPRUCE_SAPLING:    plant = new ItemStack(Blocks.SAPLING, 1, 1); break;
                case WHITE_TULIP:       plant = new ItemStack(Blocks.RED_FLOWER, 1, 6); break;
                default:
            }

            if (plant != null)
            {
                return ImmutableList.of(new ItemStack(Items.FLOWER_POT), plant);
            }
        }

        return ImmutableList.of(this.getRequiredBuildItemForState(state, world, pos));
    }

    @Nullable
    protected ItemStack getStateToItemOverride(IBlockState state)
    {
        Block block = state.getBlock();

        if (block == Blocks.PISTON_HEAD ||
            block == Blocks.PISTON_EXTENSION ||
            block == Blocks.PORTAL ||
            block == Blocks.END_PORTAL ||
            block == Blocks.END_GATEWAY)
        {
            return ItemStack.EMPTY;
        }
        else if (block == Blocks.FARMLAND)
        {
            return new ItemStack(Blocks.DIRT);
        }
        else if (block == Blocks.GRASS_PATH)
        {
            return new ItemStack(Blocks.GRASS);
        }
        else if (block == Blocks.BROWN_MUSHROOM_BLOCK)
        {
            return new ItemStack(Blocks.BROWN_MUSHROOM_BLOCK);
        }
        else if (block == Blocks.RED_MUSHROOM_BLOCK)
        {
            return new ItemStack(Blocks.RED_MUSHROOM_BLOCK);
        }
        else if (block == Blocks.LAVA)
        {
            if (state.getValue(BlockLiquid.LEVEL) == 0)
            {
                return new ItemStack(Items.LAVA_BUCKET);
            }
            else
            {
                return ItemStack.EMPTY;
            }
        }
        else if (block == Blocks.WATER)
        {
            if (state.getValue(BlockLiquid.LEVEL) == 0)
            {
                return new ItemStack(Items.WATER_BUCKET);
            }
            else
            {
                return ItemStack.EMPTY;
            }
        }
        else if (block instanceof BlockDoor && state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER)
        {
            return ItemStack.EMPTY;
        }
        else if (block instanceof BlockBed && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD)
        {
            return ItemStack.EMPTY;
        }
        else if (block instanceof BlockDoublePlant && state.getValue(BlockDoublePlant.HALF) == BlockDoublePlant.EnumBlockHalf.UPPER)
        {
            return ItemStack.EMPTY;
        }

        return null;
    }

    protected void overrideStackSize(IBlockState state, ItemStack stack)
    {
        if (state.getBlock() instanceof BlockSlab && ((BlockSlab) state.getBlock()).isDouble())
        {
            stack.setCount(2);
        }
        else if (state.getBlock() == Blocks.SNOW_LAYER)
        {
            stack.setCount(state.getValue(BlockSnow.LAYERS));
        }
    }

    protected NBTTagCompound writeToNBT()
    {
        NBTTagCompound nbt = new NBTTagCompound();

        NbtWrap.putTag(nbt, "MaterialCache", this.writeMapToNBT(this.buildItemsForStates));
        NbtWrap.putTag(nbt, "DisplayMaterialCache", this.writeMapToNBT(this.displayItemsForStates));

        return nbt;
    }

    protected NBTTagList writeMapToNBT(IdentityHashMap<IBlockState, ItemStack> map)
    {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<IBlockState, ItemStack> entry : map.entrySet())
        {
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound stateTag = new NBTTagCompound();
            NBTUtil.writeBlockState(stateTag, entry.getKey());

            NbtWrap.putTag(tag, "Block", stateTag);
            NbtWrap.putTag(tag, "Item", entry.getValue().writeToNBT(new NBTTagCompound()));

            NbtWrap.addTag(list, tag);
        }

        return list;
    }

    protected void readFromNBT(NBTTagCompound nbt)
    {
        this.buildItemsForStates.clear();
        this.displayItemsForStates.clear();

        this.readMapFromNBT(nbt, "MaterialCache", this.buildItemsForStates);
        this.readMapFromNBT(nbt, "DisplayMaterialCache", this.displayItemsForStates);
    }

    protected void readMapFromNBT(NBTTagCompound nbt, String tagName, IdentityHashMap<IBlockState, ItemStack> map)
    {
        if (NbtWrap.containsList(nbt, tagName))
        {
            NBTTagList list = NbtWrap.getList(nbt, tagName, Constants.NBT.TAG_COMPOUND);
            final int count = NbtWrap.getListSize(list);

            for (int i = 0; i < count; ++i)
            {
                NBTTagCompound tag = NbtWrap.getCompoundAt(list, i);

                if (NbtWrap.containsCompound(tag, "Block") &&
                    NbtWrap.containsCompound(tag, "Item"))
                {
                    IBlockState state = NBTUtil.readBlockState(NbtWrap.getCompound(tag, "Block"));

                    if (state != null)
                    {
                        ItemStack stack = ItemWrap.fromTag(NbtWrap.getCompound(tag, "Item"));
                        map.put(state, stack);
                    }
                }
            }
        }
    }

    protected Path getCacheDir()
    {
        return ConfigUtils.getConfigDirectory().resolve(Reference.MOD_ID);
    }

    protected Path getCacheFile()
    {
        return this.getCacheDir().resolve("material_cache.nbt");
    }

    public boolean writeToFile()
    {
        if (this.dirty == false)
        {
            return false;
        }

        Path dir = this.getCacheDir();
        Path file = this.getCacheFile();

        try
        {
            if (FileUtils.createDirectoriesIfMissing(dir) == false)
            {
                Litematica.LOGGER.warn("Failed to write the material list cache to file '{}'",
                                       file.toAbsolutePath().toString());
                return false;
            }

            OutputStream os = Files.newOutputStream(file);
            CompressedStreamTools.writeCompressed(this.writeToNBT(), os);
            os.close();
            this.dirty = false;

            return true;
        }
        catch (Exception e)
        {
            Litematica.LOGGER.warn("Failed to write the material list cache to file '{}'",
                                   file.toAbsolutePath().toString(), e);
        }

        return false;
    }

    public void readFromFile()
    {
        Path file = this.getCacheFile();

        if (Files.isRegularFile(file) == false || Files.isReadable(file) == false)
        {
            return;
        }

        try
        {
            InputStream is = Files.newInputStream(file);
            NBTTagCompound nbt = CompressedStreamTools.readCompressed(is);
            is.close();

            if (nbt != null)
            {
                this.readFromNBT(nbt);
                this.hasReadFromFile = true;
                this.dirty = false;
            }
        }
        catch (Exception e)
        {
            Litematica.LOGGER.warn("Failed to read the material list cache from file '{}'",
                                   file.toAbsolutePath().toString(), e);
        }
    }
}
