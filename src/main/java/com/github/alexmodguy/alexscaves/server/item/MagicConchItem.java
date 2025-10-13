package com.github.alexmodguy.alexscaves.server.item;

import com.github.alexmodguy.alexscaves.server.enchantment.ACEnchantmentRegistry;
import com.github.alexmodguy.alexscaves.server.entity.ACEntityRegistry;
import com.github.alexmodguy.alexscaves.server.entity.living.DeepOneBaseEntity;
import com.github.alexmodguy.alexscaves.server.entity.util.DeepOneReaction;
import com.github.alexmodguy.alexscaves.server.level.storage.ACWorldData;
import com.github.alexmodguy.alexscaves.server.misc.ACSoundRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class MagicConchItem extends Item {
    private static final int REPUTATION_PENALTY_NORMAL = -5;
    private static final int REPUTATION_PENALTY_TAXING_BELLOW = -40;
    
    public MagicConchItem(Item.Properties properties) {
        super(properties);
    }

    public boolean isFoil(ItemStack stack) {
        return true;
    }

    public void releaseUsing(ItemStack stack, Level level, LivingEntity player, int useTimeLeft) {
        int i = this.getUseDuration(stack) - useTimeLeft;
        if (i > 25) {
            boolean hasTaxingBellow = stack.getEnchantmentLevel(ACEnchantmentRegistry.TAXING_BELLOW.get()) > 0;
            
            if (!level.isClientSide && !canUseWithCurrentReputation(level, player, stack, hasTaxingBellow)) {
                // Fail summon
                if (player instanceof Player realPlayer) {
                    realPlayer.displayClientMessage(Component.translatable("item.alexscaves.magic_conch.insufficient_trust"), true);
                }
                level.playSound(null, player, ACSoundRegistry.DEEP_ONE_HOSTILE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                return;
            }
            
            // Proceed with summoning
            level.playSound(null, player, ACSoundRegistry.MAGIC_CONCH_CAST.get(), SoundSource.RECORDS, 16.0F, 1.0F);
            RandomSource randomSource = player.getRandom();
            int time = 1200 + stack.getEnchantmentLevel(ACEnchantmentRegistry.LASTING_MORALE.get()) * 400;
            if (!level.isClientSide) {
                int chartingLevel = stack.getEnchantmentLevel(ACEnchantmentRegistry.CHARTING_CALL.get());
                DeepOneBaseEntity lastSummonedDeepOne = null;
                int maxNormal = 3 + randomSource.nextInt(1);
                int maxKnights = 2 + randomSource.nextInt(1);
                int maxMage = 1 + randomSource.nextInt(1);
                if(chartingLevel > 0){
                    maxNormal += randomSource.nextInt(Math.max(chartingLevel - 1, 1));
                    if(chartingLevel > 2){
                        maxKnights += randomSource.nextInt(Math.max(chartingLevel - 2, 1));
                    }
                    if(chartingLevel > 3){
                        maxMage += randomSource.nextInt(Math.max(chartingLevel - 3, 1));
                    }
                }
                int normal = 0;
                int knights = 0;
                int mage = 0;
                int tries = 0;
                while (normal < maxNormal && tries < 99) {
                    tries++;
                    DeepOneBaseEntity summoned = summonDeepOne(ACEntityRegistry.DEEP_ONE.get(), player, time);
                    if (summoned != null) {
                        normal++;
                        lastSummonedDeepOne = summoned;
                    }
                }
                tries = 0;
                while (knights < maxKnights && tries < 99) {
                    tries++;
                    DeepOneBaseEntity summoned = summonDeepOne(ACEntityRegistry.DEEP_ONE_KNIGHT.get(), player, time);
                    if (summoned != null) {
                        knights++;
                        lastSummonedDeepOne = summoned;
                    }
                }
                tries = 0;
                while (mage < maxMage && tries < 99) {
                    tries++;
                    DeepOneBaseEntity summoned = summonDeepOne(ACEntityRegistry.DEEP_ONE_MAGE.get(), player, time);
                    if (summoned != null) {
                        mage++;
                        lastSummonedDeepOne = summoned;
                    }
                }
                if(lastSummonedDeepOne != null){
                    // Costs rep to summon
                    int reputationPenalty = hasTaxingBellow ? REPUTATION_PENALTY_TAXING_BELLOW : REPUTATION_PENALTY_NORMAL;
                    lastSummonedDeepOne.addReputation(player.getUUID(), reputationPenalty);
                }
            }
            if (player instanceof Player realPlayer) {
                realPlayer.awardStat(Stats.ITEM_USED.get(this));
                realPlayer.getCooldowns().addCooldown(this, time);
            }

            // Not sure why this only works on server side - something about server sync?
            if (!hasTaxingBellow && !level.isClientSide) {
                stack.shrink(1);
            }
        }

    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        level.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
        return InteractionResultHolder.consume(itemstack);
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        // Block Unbreaking and Mending at enchanting tables since this is a single-use consumable item
        if (enchantment == Enchantments.UNBREAKING || enchantment == Enchantments.MENDING) {
            return false;
        }
        return super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        // Block Unbreaking and Mending at anvils since this is a single-use consumable item
        // Check if the book contains these enchantments
        if (book.getAllEnchantments().containsKey(Enchantments.UNBREAKING) || 
            book.getAllEnchantments().containsKey(Enchantments.MENDING)) {
            return false;
        }
        return super.isBookEnchantable(stack, book);
    }

    public int getUseDuration(ItemStack stack) {
        return 1200;
    }

    public UseAnim getUseAnimation(ItemStack itemStack) {
        return UseAnim.BOW;
    }

    private boolean canUseWithCurrentReputation(Level level, LivingEntity player, ItemStack stack, boolean hasTaxingBellow) {
        ACWorldData worldData = ACWorldData.get(level);
        if (worldData == null) {
            return true;
        }
        
        int currentReputation = worldData.getDeepOneReputation(player.getUUID());
        int reputationPenalty = hasTaxingBellow ? REPUTATION_PENALTY_TAXING_BELLOW : REPUTATION_PENALTY_NORMAL;
        int newReputation = currentReputation + reputationPenalty;
        
        // Without Taxing Bellow: can't use if it would move into aggressive territory
        if (!hasTaxingBellow && newReputation < DeepOneReaction.AGGRESSIVE_THRESHOLD) {
            return false;
        }
        
        // With Taxing Bellow: can't use if it would go below minimum reputation
        if (hasTaxingBellow && newReputation < ACWorldData.MIN_DEEP_ONE_REPUTATION) {
            return false;
        }
        
        return true;
    }

    private DeepOneBaseEntity summonDeepOne(EntityType type, LivingEntity summoner, int time) {
        RandomSource random = summoner.getRandom();
        BlockPos randomPos = summoner.blockPosition().offset(random.nextInt(20) - 10, 7, random.nextInt(20) - 10);
        while ((summoner.level().getFluidState(randomPos).is(FluidTags.WATER) || summoner.level().isEmptyBlock(randomPos)) && randomPos.getY() > summoner.level().getMinBuildHeight()) {
            randomPos = randomPos.below();
        }
        BlockState state = summoner.level().getBlockState(randomPos);
        if (!state.getFluidState().is(FluidTags.WATER) && !state.entityCanStandOn(summoner.level(), randomPos, summoner)) {
            return null;
        }
        Vec3 at = Vec3.atCenterOf(randomPos).add(0, 0.5, 0);
        Entity created = type.create(summoner.level());
        if (created instanceof DeepOneBaseEntity deepOne) {
            float f = random.nextFloat() * 360;
            deepOne.moveTo(at.x, at.y, at.z, f, -60);
            deepOne.yBodyRot = f;
            deepOne.setYHeadRot(f);
            deepOne.setSummonedBy(summoner, time);
            deepOne.finalizeSpawn((ServerLevel) summoner.level(), summoner.level().getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.TRIGGERED, (SpawnGroupData) null, (CompoundTag) null);
            if (deepOne.checkSpawnObstruction(summoner.level())) {
                summoner.level().addFreshEntity(deepOne);
                deepOne.copyTarget(summoner);
                return deepOne;
            }
        }
        return null;
    }
}
