/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.tracker.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.bridge.block.TrackedBlockBridge;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.context.transaction.EffectTransactor;
import org.spongepowered.common.util.ReflectionUtil;

@SuppressWarnings({"unchecked", "rawtypes"})
@Mixin(Block.class)
public abstract class BlockMixin_Tracker implements TrackedBlockBridge {

    // @formatter:off
    @Shadow public static void shadow$spawnDrops(final BlockState state, final World worldIn, final BlockPos pos) {
        throw new IllegalStateException("untransformed shadow");
    }
    // @formatter:on
    private final boolean tracker$hasNeighborLogicOverridden = ReflectionUtil.isNeighborChangedDeclared(this.getClass());

    @Nullable private static EffectTransactor tracker$effectTransactorForDrops = null;


    @Override
    public boolean bridge$overridesNeighborNotificationLogic() {
        return this.tracker$hasNeighborLogicOverridden;
    }

    /**
     * This is a scattering approach to checking that all block spawns being
     * attempted are going to be prevented if the block changes are currently
     * restoring.
     *
     * @author gabizou - August 16th, 2020 - Minecraft 1.14.4
     * @param ci The callback info
     */
    @Inject(
        method = {
            // Effectively, all the spawnDrops injection points we can scatter
            "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",
            "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
            "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V"
        },
        at = @At("HEAD"),
        cancellable = true
    )
    private static void tracker$cancelOnBlockRestoration(final CallbackInfo ci) {
        if (Thread.currentThread() == PhaseTracker.SERVER.getSidedThread()) {
            if (PhaseTracker.SERVER.getPhaseContext().isRestoring()) {
                ci.cancel();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(
        method = "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",
        at = @At("HEAD")
    )
    private static void tracker$captureBlockProposedToBeSpawningDrops(final BlockState state, final World worldIn,
        final BlockPos pos, final CallbackInfo ci) {
        final PhaseTracker server = PhaseTracker.SERVER;
        if (server.getSidedThread() != Thread.currentThread()) {
            return;
        }
        final PhaseContext<@NonNull ?> context = server.getPhaseContext();
        if(!context.recordsEntitySpawns()) {
            return;
        }
        BlockMixin_Tracker.tracker$effectTransactorForDrops = context.getTransactor()
            .logBlockDrops(context, worldIn, pos, state, null);
    }

    @Inject(
        method = "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
        at = @At("HEAD")
    )
    private static void tracker$captureBlockProposedToBeSpawningDrops(
        final BlockState state, final World worldIn,
        final BlockPos pos, final @Nullable TileEntity tileEntity, final CallbackInfo ci
    ) {
        final PhaseTracker server = PhaseTracker.SERVER;
        if (server.getSidedThread() != Thread.currentThread()) {
            return;
        }
        final PhaseContext<@NonNull ?> context = server.getPhaseContext();
        if (!context.recordsEntitySpawns()) {
            return;
        }
        BlockMixin_Tracker.tracker$effectTransactorForDrops = context.getTransactor()
            .logBlockDrops(context, worldIn, pos, state, tileEntity);
    }

    @Inject(
        method = "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD")
    )
    private static void tracker$captureBlockProposedToBeSpawningDrops(final BlockState state, final World worldIn,
        final BlockPos pos, final @Nullable TileEntity tileEntity, final Entity entity, final ItemStack itemStack,
        final CallbackInfo ci) {
        final PhaseTracker server = PhaseTracker.SERVER;
        if (server.getSidedThread() != Thread.currentThread()) {
            return;
        }
        final PhaseContext<@NonNull ?> context = server.getPhaseContext();
        if(!context.recordsEntitySpawns()) {
            return;
        }
        BlockMixin_Tracker.tracker$effectTransactorForDrops = context.getTransactor()
            .logBlockDrops(context, worldIn, pos, state, tileEntity);
    }


    @Inject(
        method = {
            "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",
            "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;)V",
            "spawnDrops(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V"
        },
        at = @At("TAIL")
    )
    private static void tracker$closeEffectIfCapturing(final CallbackInfo ci) {
        final PhaseTracker server = PhaseTracker.SERVER;
        if (server.getSidedThread() != Thread.currentThread()) {
            return;
        }
        final PhaseContext<@NonNull ?> context = server.getPhaseContext();
        if(!context.recordsEntitySpawns()) {
            return;
        }
        context.getTransactor().completeBlockDrops(BlockMixin_Tracker.tracker$effectTransactorForDrops);
    }
}
