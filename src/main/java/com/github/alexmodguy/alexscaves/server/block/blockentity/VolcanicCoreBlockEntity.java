package com.github.alexmodguy.alexscaves.server.block.blockentity;

import com.github.alexmodguy.alexscaves.AlexsCaves;
import com.github.alexmodguy.alexscaves.server.block.ACBlockRegistry;
import com.github.alexmodguy.alexscaves.server.block.PrimalMagmaBlock;
import com.github.alexmodguy.alexscaves.server.entity.ACEntityRegistry;
import com.github.alexmodguy.alexscaves.server.entity.item.TephraEntity;
import com.github.alexmodguy.alexscaves.server.entity.living.LuxtructosaurusEntity;
import com.github.alexmodguy.alexscaves.server.entity.living.SauropodBaseEntity;
import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import com.github.alexmodguy.alexscaves.server.misc.ACAdvancementTriggerRegistry;
import com.github.alexmodguy.alexscaves.server.misc.ACSoundRegistry;
import com.github.alexmodguy.alexscaves.server.misc.ACTagRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public class VolcanicCoreBlockEntity extends BlockEntity {

    private int battleTime = 0;
    private int bossSpawnCooldown = 0;
    private int tephraSpawnCooldown = 0;

    private final Predicate<ItemEntity> itemAttracted = item -> item.getItem().is(ACItemRegistry.OMINOUS_CATALYST.get());

    public VolcanicCoreBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ACBlockEntityRegistry.VOLCANIC_CORE.get(), blockPos, blockState);
    }

    public static void tick(Level level, BlockPos blockPos, BlockState state, VolcanicCoreBlockEntity entity) {
        if(entity.bossSpawnCooldown > 0){
            entity.bossSpawnCooldown--;
        }else{
            Vec3 vec3 = Vec3.atCenterOf(blockPos);
            AABB aabb = new AABB(vec3.subtract(20, 100, 20), vec3.add(20, 100, 20));
            double maxDist = 100;
            for(ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, aabb, entity.itemAttracted)){
                double dist = Mth.sqrt((float) item.distanceToSqr(vec3));
                if(dist < maxDist){
                    Vec3 sub = vec3.subtract(item.position()).normalize().scale(0.2F);
                    Vec3 delta = item.getDeltaMovement().scale(0.8F);
                    item.setDeltaMovement(sub.add(delta));
                }
                if(dist < 0.66F && entity.spawnBoss()){
                    item.getItem().shrink(1);
                    if(!level.isClientSide){
                        for(Player player : level.getEntitiesOfClass(Player.class, aabb, EntitySelector.NO_SPECTATORS)){
                            ACAdvancementTriggerRegistry.SUMMON_LUXTRUCTOSAURUS.triggerForEntity(player);
                        }
                    }
                }
            }
        }
        if (AlexsCaves.PROXY.isPrimordialBossActive(level)) {
            if (entity.tephraSpawnCooldown-- <= 0) {
                entity.spawnTephra(true);
                entity.tephraSpawnCooldown = 120 + level.random.nextInt(120);
            }
        } else {
            entity.battleTime = 0;
        }
        
        int heatingRate = AlexsCaves.COMMON_CONFIG.volcanicCoreHeatingRate.get();
        if (level.getGameTime() % heatingRate == 0) {
            int radius = AlexsCaves.COMMON_CONFIG.volcanicCoreHeatingRadius.get();
            int heatingCount = AlexsCaves.COMMON_CONFIG.volcanicCoreHeatingCount.get();
            int diameter = radius * 2 + 1;
            int totalBlocks = diameter * diameter * diameter - 1; // exclude center
            int centerIndex = (diameter * diameter * diameter) / 2;
            
            for (int i = 0; i < heatingCount; i++) {
                int randomIndex = level.random.nextInt(totalBlocks);
                
                int idx = randomIndex;
                if (idx >= centerIndex) {
                     idx++; // skip center
                }
                
                int dx = (idx % diameter) - radius;
                int dy = ((idx / diameter) % diameter) - radius;    
                int dz = (idx / (diameter * diameter)) - radius;
                
                BlockPos targetPos = blockPos.offset(dx, dy, dz);
                BlockState targetState = level.getBlockState(targetPos);

                BlockState heatedState = getHeatedBlockState(targetState);
                if (heatedState != null) {
                    level.setBlockAndUpdate(targetPos, heatedState);
                    // Play sound and spawn particle effect
                    boolean isLava = heatedState.getFluidState().is(FluidTags.LAVA);
                    if (isLava) {
                        level.playSound(null, targetPos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.5F, 0.8F + level.random.nextFloat() * 0.4F);
                    } else {
                        level.playSound(null, targetPos, ACSoundRegistry.PRIMAL_MAGMA_FISSURE_CLOSE.get(), SoundSource.BLOCKS, 0.5F, 0.8F + level.random.nextFloat() * 0.4F);
                    }
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.LAVA, targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5, 2, 0.25, 0.1, 0.25, 0.0);
                    }
                }
            }
        }
    }
    
    /**
     * Returns the next heated state for a block, or null if the block cannot be heated.
     * volcanic_core_melts_to_lava tag -> Lava
     * Basalt -> Smooth Basalt -> Flood Basalt -> Inactive Primal Magma -> Active Primal Magma -> Lava
     */
    private static BlockState getHeatedBlockState(BlockState state) {
        if (state.is(ACTagRegistry.VOLCANIC_CORE_MELTS_TO_LAVA)) {
            return net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState();
        } else if (state.is(net.minecraft.world.level.block.Blocks.BASALT)) {
            return net.minecraft.world.level.block.Blocks.SMOOTH_BASALT.defaultBlockState();
        } else if (state.is(net.minecraft.world.level.block.Blocks.SMOOTH_BASALT)) {
            return ACBlockRegistry.FLOOD_BASALT.get().defaultBlockState();
        } else if (state.is(ACBlockRegistry.FLOOD_BASALT.get())) {
            return ACBlockRegistry.PRIMAL_MAGMA.get().defaultBlockState().setValue(PrimalMagmaBlock.ACTIVE, false).setValue(PrimalMagmaBlock.PERMANENT, true);
        } else if (state.is(ACBlockRegistry.PRIMAL_MAGMA.get()) && !state.getValue(PrimalMagmaBlock.ACTIVE)) {
            return state.setValue(PrimalMagmaBlock.ACTIVE, true);
        } else if (state.is(ACBlockRegistry.PRIMAL_MAGMA.get()) && state.getValue(PrimalMagmaBlock.ACTIVE)) {
            return net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState();
        }
        return null;
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.tephraSpawnCooldown = tag.getInt("TephraSpawnCooldown");
        this.bossSpawnCooldown = tag.getInt("BossSpawnCooldown");
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("TephraSpawnCooldown", this.tephraSpawnCooldown);
        tag.putInt("BossSpawnCooldown", this.bossSpawnCooldown);
    }

    public boolean spawnBoss(){
        if(bossSpawnCooldown > 0){
            return false;
        }else{
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos volcanoTop = this.getBlockPos();
            for(int i = -5; i <= -5; i++){
                for(int j = -5; j <= -5; j++){
                    BlockPos.MutableBlockPos localTop = getTopOfVolcano(mutableBlockPos.set(this.getBlockPos().getX() + i, this.getBlockPos().getY(), this.getBlockPos().getZ() + j));
                    if(localTop.getY() > volcanoTop.getY()){
                        volcanoTop = volcanoTop.atY(localTop.getY());
                    }
                }
            }
            LuxtructosaurusEntity luxtructosaurus = ACEntityRegistry.LUXTRUCTOSAURUS.get().create(level);
            luxtructosaurus.setPos(Vec3.upFromBottomCenterOf(volcanoTop, 2.0F));
            luxtructosaurus.setInvisible(true);
            luxtructosaurus.setAnimation(SauropodBaseEntity.ANIMATION_SUMMON);
            luxtructosaurus.enragedFor = 100;
            luxtructosaurus.setEnraged(true);
            level.addFreshEntity(luxtructosaurus);
            bossSpawnCooldown = 24000;
            return true;
        }
    }

    private BlockPos.MutableBlockPos getTopOfVolcano(BlockPos posIn){
        BlockPos.MutableBlockPos volcanoTop = new BlockPos.MutableBlockPos();
        volcanoTop.set(posIn);
        while (level.getBlockState(volcanoTop).is(ACTagRegistry.VOLCANO_BLOCKS) && volcanoTop.getY() < level.getMaxBuildHeight()) {
            volcanoTop.move(0, 1, 0);
        }
        volcanoTop.move(0, -1, 0);
        return volcanoTop;
    }
    private void spawnTephra(boolean big) {
        BlockPos volcanoTop = getTopOfVolcano(this.getBlockPos()).immutable();
        if (level.getBlockState(volcanoTop).is(ACBlockRegistry.VOLCANIC_CORE.get()) || level.getBlockState(volcanoTop).is(ACBlockRegistry.PRIMAL_MAGMA.get()) || level.getBlockState(volcanoTop).is(ACBlockRegistry.FISSURE_PRIMAL_MAGMA.get()) || level.getFluidState(volcanoTop).is(FluidTags.LAVA)) {
            Vec3 volcanoVec = Vec3.upFromBottomCenterOf(volcanoTop, 3F);
            Player nearestPlayer = level.getNearestPlayer(volcanoVec.x, volcanoVec.y, volcanoVec.z, 400D, true);
            if (big) {
                TephraEntity bigTephra = ACEntityRegistry.TEPHRA.get().create(level);
                bigTephra.setPos(volcanoVec);
                bigTephra.setMaxScale(2F + level.random.nextFloat());
                Vec3 targetVec;
                if (nearestPlayer == null) {
                    targetVec = new Vec3(level.random.nextFloat() - 0.5F, 0, level.random.nextFloat() - 0.5F).normalize().scale(level.random.nextInt(50) + 20);
                } else {
                    targetVec = nearestPlayer.position().subtract(volcanoVec);
                    bigTephra.setArcingTowards(nearestPlayer.getUUID());
                }
                double d4 = Math.sqrt(targetVec.x * targetVec.x + targetVec.z * targetVec.z);
                double d5 = nearestPlayer == null ? level.random.nextFloat() : 0;
                bigTephra.shoot(targetVec.x, targetVec.y + 0.5F + d4 * 0.75F + d5, targetVec.z,  (float) (d4 * 0.1F + d5), 1 + level.random.nextFloat() * 0.5F);
                level.addFreshEntity(bigTephra);
            }
            for(int smalls = 0; smalls < 3 + level.random.nextInt(3); smalls++){
                TephraEntity smallTephra = ACEntityRegistry.TEPHRA.get().create(level);
                smallTephra.setPos(volcanoVec);
                smallTephra.setMaxScale(0.6F + 0.6F * level.random.nextFloat());
                Vec3 targetVec = new Vec3(level.random.nextFloat() - 0.5F, 0, level.random.nextFloat() - 0.5F).normalize().scale(level.random.nextInt(30) + 30);
                double d4 = Math.sqrt(targetVec.x * targetVec.x + targetVec.z * targetVec.z);
                smallTephra.shoot(targetVec.x, targetVec.y + 0.5F + d4 * 0.75F + level.random.nextFloat(), targetVec.z, (float) (d4 * 0.1F + level.random.nextFloat()), 1);
                level.addFreshEntity(smallTephra);
            }
        }
    }
}
