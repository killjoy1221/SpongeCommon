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
package org.spongepowered.common.mixin.core.tileentity;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.FurnaceTileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.entity.carrier.furnace.FurnaceBlockEntity;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.Surrogate;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.data.CustomNameableBridge;
import org.spongepowered.common.item.util.ItemStackUtil;

import java.util.Collections;

@Mixin(FurnaceTileEntity.class)
public abstract class FurnaceTileEntityMixin extends LockableTileEntityMixin implements CustomNameableBridge {

    @Shadow private NonNullList<ItemStack> furnaceItemStacks;
    @Shadow private int cookTime;
    @Shadow private int currentItemBurnTime;

    @Shadow protected abstract boolean canSmelt();

    @Override
    public void bridge$setCustomDisplayName(final String customName) {
        ((FurnaceTileEntity) (Object) this).setCustomInventoryName(customName);
    }

    // Shrink Fuel
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;shrink(I)V"))
    private void impl$throwFuelEventIfOrShrink(final ItemStack itemStack, final int quantity) {
        final Cause cause = Sponge.getCauseStackManager().getCurrentCause();

        final ItemStackSnapshot fuel = ItemStackUtil.snapshotOf(itemStack);
        final ItemStackSnapshot shrinkedFuel = ItemStackUtil.snapshotOf(ItemStackUtil.cloneDefensive(itemStack, itemStack.getCount() - 1));

        final Transaction<ItemStackSnapshot> transaction = new Transaction<>(fuel, shrinkedFuel);
        final SmeltEvent.ConsumeFuel event = SpongeEventFactory.createSmeltEventConsumeFuel(cause, fuel, (FurnaceBlockEntity) this, Collections.singletonList(transaction));
        SpongeImpl.postEvent(event);
        if (event.isCancelled()) {
            this.currentItemBurnTime = 0;
            return;
        }

        if (!transaction.isValid()) {
            return;
        }

        if (transaction.getCustom().isPresent()) {
            this.furnaceItemStacks.set(1, ItemStackUtil.fromSnapshotToNative(transaction.getFinal()));
        } else { // vanilla
            itemStack.shrink(quantity);
        }
    }

    // Tick up and Start
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntityFurnace;canSmelt()Z", ordinal = 1))
    private boolean impl$checkIfCanSmelt(final FurnaceTileEntity furnace) {
        if (!this.canSmelt()) {
            return false;
        }

        final ItemStackSnapshot fuel = ItemStackUtil.snapshotOf(this.furnaceItemStacks.get(1));

        final Cause cause = Sponge.getCauseStackManager().getCurrentCause();
        if (this.cookTime == 0) { // Start
            final SmeltEvent.Start event = SpongeEventFactory.createSmeltEventStart(cause, fuel, (FurnaceBlockEntity) this, Collections.emptyList());
            SpongeImpl.postEvent(event);
            return !event.isCancelled();

        } else { // Tick up
            final SmeltEvent.Tick event = SpongeEventFactory.createSmeltEventTick(cause, fuel, (FurnaceBlockEntity) this, Collections.emptyList());
            SpongeImpl.postEvent(event);
            return !event.isCancelled();
        }
    }

    // Tick down
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(III)I"))
    private int impl$resetCookTimeIfCancelled(final int newCookTime, final int zero, final int totalCookTime) {
        final int clampedCookTime = MathHelper.clamp(newCookTime, zero, totalCookTime);
        final ItemStackSnapshot fuel = ItemStackUtil.snapshotOf(this.furnaceItemStacks.get(1));
        final Cause cause = Sponge.getCauseStackManager().getCurrentCause();
        final SmeltEvent.Tick event = SpongeEventFactory.createSmeltEventTick(cause, fuel, (FurnaceBlockEntity) this, Collections.emptyList());
        SpongeImpl.postEvent(event);
        if (event.isCancelled()) {
            return this.cookTime; // dont tick down
        }

        return clampedCookTime;
    }

    // Interrupt-Active - e.g. a player removing the currently smelting item
    @Inject(
        method = "setInventorySlotContents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/tileentity/TileEntityFurnace;getCookTime(Lnet/minecraft/item/ItemStack;)I"
        )
    )
    private void impl$interruptSmelt(final CallbackInfo ci) {
        this.impl$callInteruptSmeltEvent();
    }

    // Interrupt-Passive - if the currently smelting item was removed in some other way
    @Inject(method = "update",
        at = @At(
            shift = At.Shift.BEFORE,
            value = "FIELD",
            opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/tileentity/TileEntityFurnace;cookTime:I"
        ),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/tileentity/TileEntityFurnace;smeltItem()V"
            ),
            to = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/util/math/MathHelper;clamp(III)I"
            )
        )
    )
    private void impl$onResetCookTimePassive(final CallbackInfo ci) {
        this.impl$callInteruptSmeltEvent();
    }

    private void impl$callInteruptSmeltEvent() {
        if (this.cookTime > 0) {
            final ItemStackSnapshot fuel = ItemStackUtil.snapshotOf(this.furnaceItemStacks.get(1));
            final Cause cause = Sponge.getCauseStackManager().getCurrentCause();
            final SmeltEvent.Interrupt event = SpongeEventFactory.createSmeltEventInterrupt(cause, fuel, Collections.emptyList(), (FurnaceBlockEntity) this);
            SpongeImpl.postEvent(event);
        }
    }

    // Finish
    @Inject(
        method = "smeltItem",
        locals = LocalCapture.CAPTURE_FAILHARD,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;shrink(I)V"))
    private void impl$afterSmeltItem(final CallbackInfo ci, final ItemStack itemStack, final ItemStack result, final ItemStack outputStack) {
        this.impl$callSmeltFinish(result);
    }

    /**
     * The jvm can optimize the local variable table in production (in raw vanilla, not decompiled/recompiled)
     * to where the local variable table ends up being trimmed. Because of that, development
     * environments and recompiled environments (such as in a forge environment), we end up with
     * the original three itemstacks still on the local variable table by the time shrink is called.
     *
     * Can be verified with <a href="https://i.imgur.com/IfeLzed.png">that image</a>.
     *
     * @param ci The callback injection
     * @param outputStack The output
     */
    @Surrogate
    private void impl$afterSmeltItem(final CallbackInfo ci, final ItemStack outputStack) {
        this.impl$callSmeltFinish(FurnaceRecipes.instance().getSmeltingResult(this.furnaceItemStacks.get(0)));
    }

    private void impl$callSmeltFinish(final ItemStack result) {
        final ItemStackSnapshot fuel = ItemStackUtil.snapshotOf(this.furnaceItemStacks.get(1));
        final Cause cause = Sponge.getCauseStackManager().getCurrentCause();
        final ItemStackSnapshot snapshot = ItemStackUtil.snapshotOf(result);
        final SmeltEvent.Finish event = SpongeEventFactory.createSmeltEventFinish(cause, fuel, Collections.singletonList(snapshot), (FurnaceBlockEntity) this);
        SpongeImpl.postEvent(event);
    }

}