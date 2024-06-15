package com.bgsoftware.superiorskyblock.nms.v1_19;

import com.bgsoftware.common.collections.Lists;
import com.bgsoftware.common.reflection.ReflectField;
import com.bgsoftware.common.reflection.ReflectMethod;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.objects.Pair;
import com.bgsoftware.superiorskyblock.core.collections.CompletableFutureList;
import com.bgsoftware.superiorskyblock.core.logging.Log;
import com.bgsoftware.superiorskyblock.core.threads.BukkitExecutor;
import com.bgsoftware.superiorskyblock.nms.v1_19.world.PropertiesMapper;
import com.bgsoftware.superiorskyblock.tag.ByteTag;
import com.bgsoftware.superiorskyblock.tag.CompoundTag;
import com.bgsoftware.superiorskyblock.tag.IntArrayTag;
import com.bgsoftware.superiorskyblock.tag.StringTag;
import com.bgsoftware.superiorskyblock.tag.Tag;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.craftbukkit.v1_19_R3.CraftChunk;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class NMSUtils {

    private static final SuperiorSkyblockPlugin plugin = SuperiorSkyblockPlugin.getPlugin();

    private static final ReflectMethod<Void> SEND_PACKETS_TO_RELEVANT_PLAYERS = new ReflectMethod<>(
            ChunkHolder.class, 1, Packet.class, boolean.class);
    private static final ReflectField<Map<Long, ChunkHolder>> VISIBLE_CHUNKS = new ReflectField<>(
            ChunkMap.class, Map.class, Modifier.PUBLIC | Modifier.VOLATILE, 1);
    private static final ReflectMethod<LevelChunk> CHUNK_CACHE_SERVER_GET_CHUNK_IF_CACHED = new ReflectMethod<>(
            ServerChunkCache.class, "getChunkAtIfCachedImmediately", int.class, int.class);
    private static final ReflectMethod<LevelChunk> CRAFT_CHUNK_GET_HANDLE = new ReflectMethod<>(
            CraftChunk.class, LevelChunk.class, "getHandle");
    private static final ReflectField<PersistentEntitySectionManager<Entity>> SERVER_LEVEL_ENTITY_MANAGER = new ReflectField<>(
            ServerLevel.class, PersistentEntitySectionManager.class, Modifier.PUBLIC | Modifier.FINAL, 1);
    private static final ReflectField<IOWorker> ENTITY_STORAGE_WORKER = new ReflectField<>(
            EntityStorage.class, IOWorker.class, Modifier.PRIVATE | Modifier.FINAL, 1);

    private static final List<CompletableFuture<Void>> PENDING_CHUNK_ACTIONS = Lists.newLinkedList();

    private NMSUtils() {

    }

    public static void runActionOnEntityChunks(ServerLevel serverLevel, Collection<ChunkPos> chunksCoords,
                                               ChunkCallback chunkCallback) {
        runActionOnChunksInternal(serverLevel, chunksCoords, chunkCallback, unloadedChunks ->
                runActionOnUnloadedEntityChunks(serverLevel, unloadedChunks, chunkCallback));
    }

    public static void runActionOnChunks(ServerLevel serverLevel, Collection<ChunkPos> chunksCoords,
                                         boolean saveChunks, ChunkCallback chunkCallback) {
        runActionOnChunksInternal(serverLevel, chunksCoords, chunkCallback, unloadedChunks ->
                runActionOnUnloadedChunks(serverLevel, unloadedChunks, saveChunks, chunkCallback));
    }

    private static void runActionOnChunksInternal(ServerLevel serverLevel, Collection<ChunkPos> chunksCoords,
                                                  ChunkCallback chunkCallback, Consumer<List<ChunkPos>> onUnloadChunkAction) {
        List<ChunkPos> unloadedChunks = Lists.newLinkedList();
        List<LevelChunk> loadedChunks = Lists.newLinkedList();

        chunksCoords.forEach(chunkPos -> {
            ChunkAccess chunkAccess;

            try {
                chunkAccess = serverLevel.getChunkIfLoadedImmediately(chunkPos.x, chunkPos.z);
            } catch (Throwable ex) {
                chunkAccess = serverLevel.getChunkIfLoaded(chunkPos.x, chunkPos.z);
            }

            if (chunkAccess instanceof LevelChunk levelChunk) {
                loadedChunks.add(levelChunk);
            } else {
                unloadedChunks.add(chunkPos);
            }
        });

        boolean hasUnloadedChunks = !unloadedChunks.isEmpty();

        if (!loadedChunks.isEmpty())
            runActionOnLoadedChunks(loadedChunks, chunkCallback);

        if (hasUnloadedChunks) {
            onUnloadChunkAction.accept(unloadedChunks);
        } else {
            chunkCallback.onFinish();
        }
    }

    private static void runActionOnLoadedChunks(Collection<LevelChunk> chunks, ChunkCallback chunkCallback) {
        chunks.forEach(chunkCallback::onLoadedChunk);
    }

    private static void runActionOnUnloadedChunks(ServerLevel serverLevel,
                                                  Collection<ChunkPos> chunks,
                                                  boolean saveChunks,
                                                  ChunkCallback chunkCallback) {
        ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;

        if (CHUNK_CACHE_SERVER_GET_CHUNK_IF_CACHED.isValid()) {
            Iterator<ChunkPos> chunksIterator = chunks.iterator();
            while (chunksIterator.hasNext()) {
                ChunkPos chunkPos = chunksIterator.next();
                LevelChunk cachedUnloadedChunk = serverLevel.getChunkSource().getChunkAtIfCachedImmediately(chunkPos.x, chunkPos.z);
                if (cachedUnloadedChunk != null) {
                    chunkCallback.onLoadedChunk(cachedUnloadedChunk);
                    chunksIterator.remove();
                }
            }

            if (chunks.isEmpty()) {
                chunkCallback.onFinish();
                return;
            }
        }

        CompletableFuture<Void> pendingTask = new CompletableFuture<>();
        PENDING_CHUNK_ACTIONS.add(pendingTask);

        BukkitExecutor.createTask().runAsync(v -> {
            List<Pair<ChunkPos, net.minecraft.nbt.CompoundTag>> chunkCompounds = Lists.newLinkedList();

            chunks.forEach(chunkCoords -> {
                try {
                    net.minecraft.nbt.CompoundTag chunkCompound = chunkMap.read(chunkCoords).join().orElse(null);

                    if (chunkCompound == null)
                        return;

                    net.minecraft.nbt.CompoundTag chunkDataCompound = chunkMap.upgradeChunkTag(serverLevel.getTypeKey(),
                            Suppliers.ofInstance(serverLevel.getDataStorage()), chunkCompound,
                            Optional.empty(), chunkCoords, serverLevel);

                    UnloadedChunkCompound unloadedChunkCompound = new UnloadedChunkCompound(chunkDataCompound, chunkCoords);
                    chunkCallback.onUnloadedChunk(unloadedChunkCompound);

                    if (saveChunks)
                        chunkCompounds.add(new Pair<>(chunkCoords, chunkDataCompound));
                } catch (Exception error) {
                    Log.error(error, "An unexpected error occurred while interacting with unloaded chunk ", chunkCoords, ":");
                }
            });

            return chunkCompounds;
        }).runSync(chunkCompounds -> {
            chunkCompounds.forEach(chunkCompoundPair -> {
                try {
                    chunkMap.write(chunkCompoundPair.getKey(), chunkCompoundPair.getValue());
                } catch (IOException error) {
                    Log.error(error, "An unexpected error occurred while saving unloaded chunk ", chunkCompoundPair.getKey(), ":");
                }
            });

            chunkCallback.onFinish();

            pendingTask.complete(null);
            PENDING_CHUNK_ACTIONS.remove(pendingTask);
        });
    }

    private static void runActionOnUnloadedEntityChunks(ServerLevel serverLevel, Collection<ChunkPos> chunks,
                                                        ChunkCallback chunkCallback) {
        if (SERVER_LEVEL_ENTITY_MANAGER.isValid()) {
            PersistentEntitySectionManager<Entity> entityManager = SERVER_LEVEL_ENTITY_MANAGER.get(serverLevel);
            IOWorker worker = ENTITY_STORAGE_WORKER.get(entityManager.permanentStorage);
            CompletableFutureList<Pair<ChunkPos, net.minecraft.nbt.CompoundTag>> workerChunks = new CompletableFutureList<>(-1);
            chunks.forEach(chunkPos -> {
                CompletableFuture<Pair<ChunkPos, net.minecraft.nbt.CompoundTag>> completableFuture = new CompletableFuture<>();
                workerChunks.add(completableFuture);
                worker.loadAsync(chunkPos).whenComplete((entityDataOptional, error) -> {
                    if (error != null) {
                        completableFuture.completeExceptionally(error);
                    } else {
                        net.minecraft.nbt.CompoundTag entityData = entityDataOptional.orElse(null);
                        completableFuture.complete(new Pair<>(chunkPos, entityData));
                    }
                });
            });
            workerChunks.forEachCompleted(pair -> {
                if (pair.getValue() != null) {
                    UnloadedChunkCompound unloadedChunkCompound = new UnloadedChunkCompound(pair.getValue(), pair.getKey());
                    chunkCallback.onUnloadedChunk(unloadedChunkCompound);
                }
            }, error -> {
                Log.error(error, "An unexpected error occurred while interacting with an unloaded chunk:");
            });
            chunkCallback.onFinish();
        } else {
            BukkitExecutor.async(() -> {
                chunks.forEach(chunkPos -> {
                    try {
                        net.minecraft.nbt.CompoundTag entityData = serverLevel.entityDataControllerNew.readData(chunkPos.x, chunkPos.z);
                        if (entityData != null) {
                            UnloadedChunkCompound unloadedChunkCompound = new UnloadedChunkCompound(entityData, chunkPos);
                            chunkCallback.onUnloadedChunk(unloadedChunkCompound);
                        }
                    } catch (IOException error) {
                        Log.error(error, "An unexpected error occurred while interacting with unloaded chunk ", chunkPos, ":");
                    }
                });
                chunkCallback.onFinish();
            });
        }
    }

    public static List<CompletableFuture<Void>> getPendingChunkActions() {
        return Collections.unmodifiableList(PENDING_CHUNK_ACTIONS);
    }

    public static ProtoChunk createProtoChunk(ChunkPos chunkPos, ServerLevel serverLevel) {
        return new ProtoChunk(chunkPos,
                UpgradeData.EMPTY,
                serverLevel,
                serverLevel.registryAccess().registryOrThrow(Registries.BIOME),
                null);
    }

    public static void sendPacketToRelevantPlayers(ServerLevel serverLevel, int chunkX, int chunkZ, Packet<?> packet) {
        ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        ChunkHolder chunkHolder;

        try {
            chunkHolder = chunkMap.getVisibleChunkIfPresent(chunkPos.toLong());
        } catch (Throwable ex) {
            chunkHolder = VISIBLE_CHUNKS.get(chunkMap).get(chunkPos.toLong());
        }

        if (chunkHolder != null) {
            SEND_PACKETS_TO_RELEVANT_PLAYERS.invoke(chunkHolder, packet, false);
        }
    }

    public static void setBlock(LevelChunk levelChunk, BlockPos blockPos, int combinedId,
                                CompoundTag statesTag, CompoundTag tileEntity) {
        ServerLevel serverLevel = levelChunk.level;

        if (!isValidPosition(serverLevel, blockPos))
            return;

        BlockState blockState = Block.stateById(combinedId);

        if (statesTag != null) {
            for (Map.Entry<String, Tag<?>> entry : statesTag.entrySet()) {
                try {
                    // noinspection rawtypes
                    Property property = PropertiesMapper.getProperty(entry.getKey());
                    if (property != null) {
                        if (entry.getValue() instanceof ByteTag) {
                            // noinspection unchecked
                            blockState = blockState.setValue(property, ((ByteTag) entry.getValue()).getValue() == 1);
                        } else if (entry.getValue() instanceof IntArrayTag) {
                            int[] data = ((IntArrayTag) entry.getValue()).getValue();
                            // noinspection unchecked
                            blockState = blockState.setValue(property, data[0]);
                        } else if (entry.getValue() instanceof StringTag) {
                            String data = ((StringTag) entry.getValue()).getValue();
                            // noinspection unchecked
                            blockState = blockState.setValue(property, Enum.valueOf(property.getValueClass(), data));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if ((blockState.getMaterial().isLiquid() && plugin.getSettings().isLiquidUpdate()) ||
                blockState.getBlock() instanceof BedBlock) {
            serverLevel.setBlock(blockPos, blockState, 3);
            return;
        }

        int indexY = serverLevel.getSectionIndex(blockPos.getY());

        LevelChunkSection levelChunkSection = levelChunk.getSections()[indexY];

        if (levelChunkSection == null) {
            int yOffset = SectionPos.blockToSectionCoord(blockPos.getY());
            //noinspection deprecation
            levelChunkSection = levelChunk.getSections()[indexY] = new LevelChunkSection(yOffset, levelChunk.biomeRegistry);
        }

        int blockX = blockPos.getX() & 15;
        int blockY = blockPos.getY();
        int blockZ = blockPos.getZ() & 15;

        boolean isOriginallyChunkSectionEmpty = levelChunkSection.hasOnlyAir();

        levelChunkSection.setBlockState(blockX, blockY & 15, blockZ, blockState, false);

        levelChunk.heightmaps.get(Heightmap.Types.MOTION_BLOCKING).update(blockX, blockY, blockZ, blockState);
        levelChunk.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(blockX, blockY, blockZ, blockState);
        levelChunk.heightmaps.get(Heightmap.Types.OCEAN_FLOOR).update(blockX, blockY, blockZ, blockState);
        levelChunk.heightmaps.get(Heightmap.Types.WORLD_SURFACE).update(blockX, blockY, blockZ, blockState);

        levelChunk.setUnsaved(true);

        boolean isChunkSectionEmpty = levelChunkSection.hasOnlyAir();

        if (isOriginallyChunkSectionEmpty != isChunkSectionEmpty)
            serverLevel.getLightEngine().updateSectionStatus(blockPos, isChunkSectionEmpty);

        serverLevel.getLightEngine().checkBlock(blockPos);

        if (tileEntity != null) {
            net.minecraft.nbt.CompoundTag tileEntityCompound = (net.minecraft.nbt.CompoundTag) tileEntity.toNBT();
            if (tileEntityCompound != null) {
                tileEntityCompound.putInt("x", blockPos.getX());
                tileEntityCompound.putInt("y", blockPos.getY());
                tileEntityCompound.putInt("z", blockPos.getZ());
                BlockEntity worldBlockEntity = serverLevel.getBlockEntity(blockPos);
                if (worldBlockEntity != null)
                    worldBlockEntity.load(tileEntityCompound);
            }
        }
    }

    public static boolean isDoubleBlock(Block block, BlockState blockState) {
        return (block.defaultBlockState().is(BlockTags.SLABS) || block.defaultBlockState().is(BlockTags.WOODEN_SLABS)) &&
                blockState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    public static LevelChunk getCraftChunkHandle(CraftChunk craftChunk) {
        if (CRAFT_CHUNK_GET_HANDLE.isValid())
            return CRAFT_CHUNK_GET_HANDLE.invoke(craftChunk);

        ServerLevel serverLevel = craftChunk.getCraftWorld().getHandle();
        return serverLevel.getChunk(craftChunk.getX(), craftChunk.getZ());
    }

    public record UnloadedChunkCompound(net.minecraft.nbt.CompoundTag chunkCompound, ChunkPos chunkPos) {

        public ListTag getSections() {
            return chunkCompound.getList("sections", 10);
        }

        public ListTag getEntities() {
            return chunkCompound.getList("Entities", 10);
        }

        public void setSections(ListTag sectionsList) {
            chunkCompound.put("sections", sectionsList);
        }

        public void setEntities(ListTag entitiesList) {
            chunkCompound.put("entities", entitiesList);
        }

        public void setBlockEntities(ListTag blockEntitiesList) {
            chunkCompound.put("block_entities", blockEntitiesList);
        }

        public ChunkPos getChunkPos() {
            return chunkPos;
        }

    }

    private static boolean isValidPosition(ServerLevel serverLevel, BlockPos blockPos) {
        return blockPos.getX() >= -30000000 && blockPos.getZ() >= -30000000 &&
                blockPos.getX() < 30000000 && blockPos.getZ() < 30000000 &&
                blockPos.getY() >= serverLevel.getMinBuildHeight() && blockPos.getY() < serverLevel.getMaxBuildHeight();
    }

    public interface ChunkCallback {

        void onLoadedChunk(LevelChunk levelChunk);

        void onUnloadedChunk(UnloadedChunkCompound unloadedChunkCompound);

        void onFinish();

    }

}
