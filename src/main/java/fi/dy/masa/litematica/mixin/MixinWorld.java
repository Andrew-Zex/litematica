package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.World;

import fi.dy.masa.litematica.interfaces.IWorldUpdateSuppressor;

@Mixin(World.class)
public class MixinWorld implements IWorldUpdateSuppressor
{
    private boolean preventUpdates;

    @Override
    public boolean getShouldPreventUpdates()
    {
        return this.preventUpdates;
    }

    @Override
    public void setShouldPreventUpdates(boolean preventUpdates)
    {
        this.preventUpdates = preventUpdates;
    }
}
