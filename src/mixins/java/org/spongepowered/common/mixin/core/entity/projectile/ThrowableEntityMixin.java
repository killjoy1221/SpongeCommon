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
package org.spongepowered.common.mixin.core.entity.projectile;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ThrowableEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.api.entity.projectile.Projectile;
import org.spongepowered.api.projectile.source.ProjectileSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.entity.projectile.ProjectileSourceSerializer;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.mixin.core.entity.EntityMixin;

import javax.annotation.Nullable;

@Mixin(ThrowableEntity.class)
public abstract class ThrowableEntityMixin extends EntityMixin {

    @Shadow protected LivingEntity owner;

    @Shadow @Nullable public abstract LivingEntity shadow$getThrower();
    @Shadow protected abstract void onImpact(RayTraceResult movingObjectPosition);

    @Override
    public void impl$readFromSpongeCompound(final CompoundNBT compound) {
        super.impl$readFromSpongeCompound(compound);
        ProjectileSourceSerializer.readSourceFromNbt(compound, ((Projectile) this));
    }

    @Override
    public void impl$writeToSpongeCompound(final CompoundNBT compound) {
        super.impl$writeToSpongeCompound(compound);
        ProjectileSourceSerializer.writeSourceToNbt(compound, ((Projectile) this).shooter().get(), this.shadow$getThrower());
    }

    @Redirect(method = "tick()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/projectile/ThrowableEntity;onImpact(Lnet/minecraft/util/math/RayTraceResult;)V"
        )
    )
    private void impl$handleProjectileImpact(ThrowableEntity projectile, RayTraceResult movingObjectPosition) {
        if (((WorldBridge) this.world).bridge$isFake() || movingObjectPosition.getType() == RayTraceResult.Type.MISS) {
            this.onImpact(movingObjectPosition);
            return;
        }

        //if (!SpongeCommonEventFactory.handleCollideImpactEvent(projectile, (ProjectileSource) this.shadow$getThrower(),  movingObjectPosition)) {
            this.onImpact(movingObjectPosition);
        //}
    }
}
