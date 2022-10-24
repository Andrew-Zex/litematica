package fi.dy.masa.litematica.schematic.verifier;

import malilib.util.game.BlockUtils;

public class BlockStatePairCount
{
    protected final BlockStatePair pair;
    protected final String expectedBlockDisplayName;
    protected final String foundBlockDisplayName;
    public final int count;

    public BlockStatePairCount(BlockStatePair pair, int count)
    {
        this.pair = pair;
        this.expectedBlockDisplayName = BlockUtils.getBlockRegistryName(pair.expectedState);
        this.foundBlockDisplayName = BlockUtils.getBlockRegistryName(pair.foundState);
        this.count = count;
    }

    public BlockStatePair getPair()
    {
        return this.pair;
    }

    public String getExpectedBlockDisplayName()
    {
        return this.expectedBlockDisplayName;
    }

    public String getFoundBlockDisplayName()
    {
        return this.foundBlockDisplayName;
    }

    public int getCount()
    {
        return this.count;
    }

    public static BlockStatePairCount of(BlockStatePair pair, int count)
    {
        return new BlockStatePairCount(pair, count);
    }
}
