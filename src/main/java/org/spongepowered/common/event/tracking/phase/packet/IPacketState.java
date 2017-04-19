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
package org.spongepowered.common.event.tracking.phase.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.ServerPlayer;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseData;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.registry.type.event.InternalSpawnTypes;

import java.util.ArrayList;

public interface IPacketState extends IPhaseState {

    boolean matches(int packetState);

    default void populateContext(EntityPlayerMP playerMP, Packet<?> packet, PhaseContext context) {

    }

    default boolean isPacketIgnored(Packet<?> packetIn, EntityPlayerMP packetPlayer) {
        return false;
    }

    default boolean ignoresItemPreMerges() {
        return false;
    }

    default boolean doesCaptureEntityDrops() {
        return false;
    }

    default boolean doBlockCapturing() {
        return true;
    }
    /**
     * A defaulted method to handle entities that are spawned due to packet placement during post processing.
     * Examples can include a player placing a redstone block priming a TNT explosive.
     *  @param phaseContext The phase context
     * @param entities The list of entities to spawn
     */
    default void postSpawnEntities(PhaseContext phaseContext, ArrayList<Entity> entities) {
        final ServerPlayer player =
                phaseContext.getSource(ServerPlayer.class)
                        .orElseThrow(TrackingUtil.throwWithContext("Expected to be capturing a player packet, but didn't get anything",
                                phaseContext));
        final EntitySpawnCause cause = EntitySpawnCause.builder()
                .entity(player)
                .type(InternalSpawnTypes.PLACEMENT)
                .build();
        TrackingUtil.splitAndSpawnEntities(Cause.source(cause).build(), entities, entity -> entity.setCreator(player.getUniqueId()));
    }

    default boolean shouldCaptureEntity() {
        return false;
    }

    /**
     * Defaulted method for packet phase states to spawn an entity directly.
     * This should be overridden by all packet phase states that are handling spawns
     * customarily with contexts and such. Captured entities are handled in
     * their respective {@link PacketFunction}s.
     *
     * @param context
     * @param entity
     * @param chunkX
     * @param chunkZ
     * @return True if the entity was spawned
     */
    default boolean spawnEntity(PhaseContext context, Entity entity, int chunkX, int chunkZ) {
        final net.minecraft.entity.Entity minecraftEntity = (net.minecraft.entity.Entity) entity;
        final WorldServer minecraftWorld = (WorldServer) minecraftEntity.world;
        final ServerPlayer player = context.getSource(ServerPlayer.class)
                        .orElseThrow(TrackingUtil.throwWithContext("Expected to be capturing a player", context));
        final ArrayList<Entity> entities = new ArrayList<>(1);
        entities.add(entity);
        final Cause.Builder builder = Cause.source(EntitySpawnCause.builder()
                .entity(player)
                .type(InternalSpawnTypes.PLACEMENT)
                .build());
        builder.notifier(player);
        builder.owner(player);
        final SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(builder.build(), entities);
        SpongeImpl.postEvent(event);
        if (!event.isCancelled()) {
            for (Entity newEntity : event.getEntities()) {
                EntityUtil.toMixin(newEntity).setCreator(player.getUniqueId());
                ((IMixinWorldServer) minecraftWorld).forceSpawnEntity(newEntity);
            }

            return true;
        }
        return false;
    }

    default void appendContextPreExplosion(PhaseContext phaseContext, PhaseData currentPhaseData) {
        currentPhaseData.context.first(ServerPlayer.class).ifPresent(player -> phaseContext.add(NamedCause.source(player)));
    }

}
