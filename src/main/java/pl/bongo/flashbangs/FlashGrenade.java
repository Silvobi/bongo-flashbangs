package pl.bongo.flashbangs;

import java.util.function.Consumer;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Native sulfur-cube physics; a riding item display supplies the grenade's appearance. */
public final class FlashGrenade extends SulfurCube {
    public static final int FUSE_TICKS = 32;
    public static final double GRENADE_SCALE = 0.35;
    private final Consumer<FlashGrenade> detonate;
    private final long detonateAt;
    private final ItemStack modelItem;
    private GrenadeModel model;
    private int bounceCount;
    private float spin;
    private final Quaternionf modelRotation = new Quaternionf();

    public FlashGrenade(ServerLevel level, ServerPlayer owner, ItemStack item, Consumer<FlashGrenade> detonate) {
        this(level, owner.getEyePosition().add(0, -0.18, 0), item, detonate);
    }

    public FlashGrenade(ServerLevel level, Vec3 position, ItemStack item, Consumer<FlashGrenade> detonate) {
        super(EntityTypes.SULFUR_CUBE, level);
        this.detonate = detonate;
        this.detonateAt = level.getGameTime() + FUSE_TICKS;
        this.modelItem = item.copyWithCount(1);
        setSize(2, true);
        setSilent(true);
        setPersistenceRequired();
        // A non-block item enables ball behavior without an archetype's explosion/contact damage.
        setItemSlot(EquipmentSlot.BODY, modelItem.copy());
        getAttribute(Attributes.SCALE).setBaseValue(GRENADE_SCALE);
        getAttribute(Attributes.BOUNCINESS).setBaseValue(0.58);
        getAttribute(Attributes.AIR_DRAG_MODIFIER).setBaseValue(0.0);
        getAttribute(Attributes.FRICTION_MODIFIER).setBaseValue(1.0);
        getAttribute(Attributes.GRAVITY).setBaseValue(0.03);
        refreshDimensions();
        setPos(position);
    }

    // NoAI also stops this mob's movement; remove its goals while retaining vanilla travel physics.
    @Override protected void addBehaviourGoals() {}
    @Override protected void addTargetingGoals() {}
    @Override public boolean canPickUpLoot() { return false; }

    public boolean attachModel() {
        var world = (ServerLevel) level();
        var display = new GrenadeModel(world, modelItem);
        display.setPos(getPassengerRidingPosition(display));
        if (!world.addFreshEntity(display)) return false;
        if (!display.startRiding(this, true, true)) { display.discard(); return false; }
        model = display;
        model.setTransformationInterpolationDuration(1);
        updateModel(false);
        setInvisible(true);
        return true;
    }

    public void shootFromRotation(Entity owner, float pitch, float yaw, float pitchOffset, float speed, float spread) {
        // ServerPlayer's ordinary delta can differ from its client-reported movement.
        // Vanilla projectiles use this accessor too, including airborne vertical momentum.
        Vec3 inherited = owner.getKnownMovement();
        setDeltaMovement(Entity.calculateViewVector(pitch + pitchOffset, yaw).scale(speed)
            .add(inherited.x, owner.onGround() ? 0.0 : inherited.y, inherited.z));
    }

    @Override public void tick() {
        if (!((ServerLevel) level()).hasChunkAt(BlockPos.containing(position().add(getDeltaMovement())))) {
            discard(); return;
        }
        Vec3 incoming = getDeltaMovement();
        super.tick(); // Vanilla bounciness restitutes movement against full collision shapes.
        Vec3 outgoing = getDeltaMovement();
        updateModel(true);
        boolean bounced = incoming.x * outgoing.x < -0.0001 || incoming.y * outgoing.y < -0.0001
            || incoming.z * outgoing.z < -0.0001;
        if (bounced) {
            bounceCount++;
            if (incoming.lengthSqr() > 0.01) ((ServerLevel) level()).playSound(null, getX(), getY(), getZ(),
                SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.35f, 1.4f);
        }
        // Stepped tracking consumes syncPosition to mark a waypoint. needsSync forces transmission.
        // Use one native update, rather than injecting a competing teleport/motion stream.
        syncPosition = true;
        needsSync = true;
        checkFuse();
    }

    private void updateModel(boolean advance) {
        if (model == null || model.isRemoved()) return;
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        if (speed > 0.01) {
            if (advance) spin = (spin + (float) Math.min(speed * 0.18, 0.55)) % ((float) Math.PI * 2);
            Vec3 direction = velocity.scale(1.0 / speed);
            var target = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                new Vector3f((float) direction.x, (float) direction.y, (float) direction.z)).rotateX(spin);
            if (advance) modelRotation.slerp(target, 0.45f).normalize();
            else modelRotation.set(target);
        }
        float scale = (float) FlashbangModelBounds.rotatingScale(getBbWidth(), getBbHeight());
        Vec3 center = FlashbangModelBounds.centerInDisplaySpace().scale(scale);
        var rotatedCenter = modelRotation.transform(new Vector3f((float) center.x, (float) center.y, (float) center.z));
        Vec3 seat = getPassengerRidingPosition(model).subtract(position());
        model.setTransformation(new Transformation(new Vector3f(-rotatedCenter.x,
            (float) (getBbHeight() * 0.5 - seat.y) - rotatedCenter.y, -rotatedCenter.z),
            new Quaternionf(modelRotation), new Vector3f(scale), new Quaternionf()));
        model.setTransformationInterpolationDelay(0);
    }

    public void checkFuse() {
        if (!isRemoved() && level().getGameTime() >= detonateAt) {
            try { detonate.accept(this); } finally { discard(); }
        }
    }

    @Override public void remove(RemovalReason reason) {
        if (model != null && !model.isRemoved()) model.discard();
        super.remove(reason);
    }

    @Override protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) { return net.minecraft.world.InteractionResult.FAIL; }
    @Override public boolean hurtServer(ServerLevel world, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    @Override public boolean getRequiresPrecisePosition() { return true; }
    public int bounceCount() { return bounceCount; }
    public Display.ItemDisplay model() { return model; }
    public Vec3 flashOrigin() { return getBoundingBox().getCenter(); }
    public float spinAngle() { return spin; }
    public Quaternionf modelRotation() { return new Quaternionf(modelRotation); }

    private static final class GrenadeModel extends Display.ItemDisplay {
        private GrenadeModel(ServerLevel world, ItemStack item) {
            super(EntityTypes.ITEM_DISPLAY, world);
            setItemStack(item.copyWithCount(1));
            // The author-defined FIXED transform belongs to item frames, not the flying grenade.
            setItemTransform(ItemDisplayContext.NONE);
            setNoGravity(true);
        }
        @Override public boolean shouldBeSaved() { return false; }
        @Override public boolean canUsePortal(boolean allowPassengers) { return false; }
    }
}
