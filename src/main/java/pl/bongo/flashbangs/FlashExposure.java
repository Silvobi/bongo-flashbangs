package pl.bongo.flashbangs;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;

/** Bongo's diagram: 60/120 degree total cones, measured around the player's gaze. */
public final class FlashExposure {
    public static final double MAX_RADIUS = 24.0;
    private static final double COS_30 = Math.cos(Math.toRadians(30));
    private static final double COS_60 = Math.cos(Math.toRadians(60));
    private static final double EPSILON = 1.0e-7;

    private FlashExposure() {}

    public static Optional<FlashStrength> strength(Vec3 eyes, Vec3 look, Vec3 explosion) {
        Vec3 toFlash = explosion.subtract(eyes);
        double distanceSquared = toFlash.lengthSqr();
        if (distanceSquared > MAX_RADIUS * MAX_RADIUS + EPSILON) return Optional.empty();
        if (distanceSquared < EPSILON) return Optional.of(FlashStrength.FULL);
        // Include pitch: looking up/down away from a flash also reduces its severity.
        double facing = look.normalize().dot(toFlash.scale(1.0 / Math.sqrt(distanceSquared)));
        double full, medium, weak;
        if (facing >= COS_30 - EPSILON) {
            full = 15; medium = 20; weak = 24;
        } else if (facing >= COS_60 - EPSILON) {
            full = 2; medium = 6; weak = 17;
        } else {
            full = 1; medium = 2; weak = 0;
        }
        if (distanceSquared <= full * full + EPSILON) return Optional.of(FlashStrength.FULL);
        if (distanceSquared <= medium * medium + EPSILON) return Optional.of(FlashStrength.MEDIUM);
        if (weak > 0 && distanceSquared <= weak * weak + EPSILON) return Optional.of(FlashStrength.WEAK);
        return Optional.empty();
    }
}
