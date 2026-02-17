package com.deathfrog.salvationmod.core.blockentity;

import net.minecraft.core.BlockPos;

public class Beacon
{
    protected BlockPos pos = null;
    protected boolean valid = false;
    protected boolean lit = false;
    protected int fuel = 0;

    public Beacon(BlockPos pos, boolean valid, boolean lit, int fuel)
    {
        this.pos = pos;
        this.valid = valid;
        this.lit = lit;
        this.fuel = fuel;
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

    @Override
    public String toString()
    {
        return "Beacon{" + "pos=" + this.pos + ", valid=" + this.valid + ", lit=" + this.lit + ", fuel=" + this.fuel + '}';
    }
}
