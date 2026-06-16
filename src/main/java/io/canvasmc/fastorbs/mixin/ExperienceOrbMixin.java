package io.canvasmc.fastorbs.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin extends Entity {

    @Shadow
    public int age;

    @Inject(method = "awardWithDirection", at = @At("HEAD"), cancellable = true)
    private static void canvas$injectFasterAwardWithDirectionImplementation(final ServerLevel level, final Vec3 pos, final Vec3 roughDirection, final int amount, final CallbackInfo ci) {
        if (amount <= 0) {
            // fully skip if count is 0
            ci.cancel();
            return;
        }

        final int experienceValue = getExperienceValue(amount);
        final AABB aabb = AABB.ofSize(pos, 1.0, 1.0, 1.0);
        final List<ExperienceOrb> entities = level.getEntities(
            // we are running fast orbs so it just checks if it's alive, the 2 int vals don't matter
            EntityTypeTest.forClass(ExperienceOrb.class), aabb, orb -> canMerge(orb, Integer.MIN_VALUE, Integer.MIN_VALUE)
        );

        if (!entities.isEmpty()) {
            final ExperienceOrb firstEntry = entities.getFirst();
            firstEntry.age = 0;
            firstEntry.setValue(firstEntry.getValue() + experienceValue);
        }
        else {
            level.addFreshEntity(new ExperienceOrb(level, pos, roughDirection, experienceValue));
        }

        ci.cancel();
    }

    /**
     * @author dueris
     * @reason completely remove all other checks, just check if alive
     */
    @Overwrite
    private static boolean canMerge(final @NonNull ExperienceOrb orb, final int id, final int value) {
        return orb.isAlive();
    }

    public ExperienceOrbMixin(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Shadow
    public static int getExperienceValue(final int maxValue) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    /**
     * @author dueris
     * @reason replace with Canvas implementation
     */
    @Overwrite
    private boolean canMerge(final ExperienceOrb orb) {
        return orb != canvas$this() && canMerge(orb, canvas$this().getId(), canvas$this().getValue());
    }

    /**
     * Basically just a method to cast {@code this} to {@link net.minecraft.world.entity.ExperienceOrb} to reduce code
     * redundancy
     *
     * @return {@code this} casted to {@link net.minecraft.world.entity.ExperienceOrb}
     */
    @Unique
    private ExperienceOrb canvas$this() {
        return (ExperienceOrb) ((Object) this);
    }

    @Inject(method = "merge", at = @At("HEAD"), cancellable = true)
    public void canvas$injectAtMerge(final @NonNull ExperienceOrb orb, final @NonNull CallbackInfo ci) {
        this.setValue(this.getValue() + orb.getValue());
        this.age = Math.min(this.age, orb.age);
        orb.discard();
        ci.cancel();
    }

    /**
     * We want the XP delay to be 0, not 2, for faster pickup speeds
     *
     * @param constant
     *     the original, should always be {@code 2}
     *
     * @return {@code 0} always
     */
    @ModifyConstant(method = "playerTouch", constant = @Constant(intValue = 2, ordinal = 0))
    public int canvas$changeTo0XpDelay(final int constant) {
        return 0;
    }

    @Shadow
    protected abstract void merge(final ExperienceOrb orb);

    @Shadow
    public abstract int getValue();

    @Shadow
    public abstract void setValue(final int value);

    /**
     * @implNote Base from Vanilla, modified from base marked with {@code // Canvas...} comments
     * @author dueris
     * @reason add check
     */
    @Overwrite
    private void scanForMerges() {
        // Canvas start - fast orbs
        if (this.level() instanceof ServerLevel && (this.tickCount % 2 == 0)) {
            for (
                ExperienceOrb orb :
                this.level().getEntities(EntityTypeTest.forClass(ExperienceOrb.class), this.getBoundingBox().inflate(0.9), this::canMerge)
            ) {
                // Canvas end - fast orbs
                this.merge(orb);
            }
        }

    }
}
