package com.deathfrog.salvationmod.core.blockentity;

import java.util.List;

import net.minecraft.core.BlockPos;

public class Beacon
{
    public static record Upgrade(String descriptionId, int count) {}

    protected BlockPos pos = null;
    protected boolean valid = false;
    protected boolean lit = false;
    protected int fuel = 0;
    protected List<Upgrade> upgrades = List.of();

    public Beacon(BlockPos pos, boolean valid, boolean lit, int fuel)
    {
        this(pos, valid, lit, fuel, List.of());
    }

    public Beacon(BlockPos pos, boolean valid, boolean lit, int fuel, List<Upgrade> upgrades)
    {
        this.pos = pos;
        this.valid = valid;
        this.lit = lit;
        this.fuel = fuel;
        this.upgrades = List.copyOf(upgrades);
    }

    public BlockPos getPosition()
    {
        return this.pos;
    }

    public boolean isValid()
    {
        return this.valid;
    }

    public boolean isLit()
    {
        return this.lit;
    }

    public void setPos(BlockPos pos)
    {
        this.pos = pos;
    }

    public void setValid(boolean valid)
    {
        this.valid = valid;
    }

    public void setLit(boolean lit)
    {
        this.lit = lit;
    }

    public void setFuel(int fuel)
    {
        this.fuel = fuel;
    }

    public int getFuel()
    {
        return this.fuel;
    }

    public List<Upgrade> getUpgrades()
    {
        return this.upgrades;
    }

    @Override
    public String toString()
    {
        return "Beacon{" + "pos=" + this.pos + ", valid=" + this.valid + ", lit=" + this.lit + ", fuel=" + this.fuel + ", upgrades=" + this.upgrades + '}';
    }
}
