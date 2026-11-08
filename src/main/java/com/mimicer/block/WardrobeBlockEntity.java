package com.mimicer.block;

import com.mimicer.hide.ClosetIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Estado de las puertas del armario: abierto/cerrado y velocidad. La animación se interpola en el cliente. */
public class WardrobeBlockEntity extends BlockEntity {
    private boolean open;
    private int speed = 12;

    // Solo cliente: interpolación de la animación.
    private float animFrom;
    private float animTarget;
    private float animStart = Float.NEGATIVE_INFINITY;

    public WardrobeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open, int ticks) {
        this.open = open;
        this.speed = Mth.clamp(ticks, 2, 80);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Apertura 0..1 en el instante {@code time} (ticks de mundo con parcial), suavizada. */
    public float openAmount(float time) {
        if (animStart == Float.NEGATIVE_INFINITY) {
            return open ? 1.0F : 0.0F;
        }
        float p = Mth.clamp((time - animStart) / speed, 0.0F, 1.0F);
        float e = p * p * (3.0F - 2.0F * p);
        return Mth.lerp(e, animFrom, animTarget);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            ClosetIndex.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            ClosetIndex.remove(level, worldPosition);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0.0, 1.0, 0.0).inflate(1.2);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Open", open);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boolean wasOpen = open;
        open = tag.getBoolean("Open");
        if (tag.contains("Speed")) {
            speed = tag.getInt("Speed");
        }
        if (level != null && level.isClientSide && wasOpen != open) {
            float now = level.getGameTime();
            animFrom = animStart == Float.NEGATIVE_INFINITY ? (wasOpen ? 1.0F : 0.0F) : openAmount(now);
            animTarget = open ? 1.0F : 0.0F;
            animStart = now;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Open", open);
        tag.putInt("Speed", speed);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }
}
