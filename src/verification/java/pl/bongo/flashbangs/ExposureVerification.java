package pl.bongo.flashbangs;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Boundary and orientation checks against the distances written on Bongo's diagram. */
public final class ExposureVerification {
    private static final Vec3 EYES = new Vec3(23, 101.62, -17);
    private static final Vec3 FORWARD = new Vec3(0, 0, 1);

    public static void main(String[] args) {
        expect(0, 0, FlashStrength.FULL);
        expect(15, 0, FlashStrength.FULL);
        expect(15.001, 0, FlashStrength.MEDIUM);
        expect(20, 0, FlashStrength.MEDIUM);
        expect(20.001, 0, FlashStrength.WEAK);
        expect(24, 0, FlashStrength.WEAK);
        expect(24.001, 0, null);

        expect(15, 30, FlashStrength.FULL);
        expect(15, -30, FlashStrength.FULL);
        expect(15, 30.01, FlashStrength.WEAK);
        expect(15, -30.01, FlashStrength.WEAK);
        expect(2, 45, FlashStrength.FULL);
        expect(2.001, 45, FlashStrength.MEDIUM);
        expect(6, 45, FlashStrength.MEDIUM);
        expect(6.001, 45, FlashStrength.WEAK);
        expect(17, 45, FlashStrength.WEAK);
        expect(17.001, 45, null);
        expect(6, 60, FlashStrength.MEDIUM);
        expect(6, -60, FlashStrength.MEDIUM);
        expect(6, 60.01, null);
        expect(6, -60.01, null);

        for (double angle : new double[]{61, 90, 135, 180, -90}) {
            expect(1, angle, FlashStrength.FULL);
            expect(1.001, angle, FlashStrength.MEDIUM);
            expect(2, angle, FlashStrength.MEDIUM);
            expect(2.001, angle, null);
        }
        // Looking vertically toward the blast must be equivalent to looking horizontally at it.
        check(FlashExposure.strength(EYES, new Vec3(0, 1, 0), EYES.add(0, 15, 0)).orElse(null)
            == FlashStrength.FULL, "pitch toward explosion");
        check(FlashExposure.strength(EYES, FORWARD, EYES.add(0, 15, 0)).isEmpty(), "pitch away from explosion");
        check(FlashExposure.strength(EYES, FORWARD, EYES.add(0, 0, 16)).orElse(null)
            == FlashStrength.MEDIUM, "direction is from eyes to flash, not reversed");
        check(FlashExposure.strength(EYES, FORWARD, EYES.add(0, 0, -16)).isEmpty(), "flash behind player");
        // Oblique vectors must use spherical distance rather than horizontal distance alone.
        Vec3 diagonal = new Vec3(1, 1, 1).normalize();
        check(FlashExposure.strength(EYES, diagonal, EYES.add(diagonal.scale(25))).isEmpty(), "3D range");
        check(FlashExposure.strength(EYES, FORWARD.scale(5), EYES.add(0, 0, 15)).orElse(null)
            == FlashStrength.FULL, "look direction normalized");

        Set<Identifier> ids = new HashSet<>();
        for (var strength : FlashStrength.values()) {
            check(ids.add(strength.effect(false)) && ids.add(strength.effect(true)), "unique render target lifecycles");
        }
        check(FlashStrength.WEAK.durationTicks() == 10 && FlashStrength.MEDIUM.durationTicks() == 20
            && FlashStrength.FULL.durationTicks() == 60, "cleanup respects 0.5 / 1 / 3 second profiles");
        System.out.println("PASS: diagram ranges, inclusive distance/angle boundaries, both cone edges, rear exposure, pitch, 3D distance and six unique profile IDs.");
    }

    private static void expect(double distance, double degrees, FlashStrength expected) {
        double angle = Math.toRadians(degrees);
        Vec3 explosion = EYES.add(Math.sin(angle) * distance, 0, Math.cos(angle) * distance);
        var actual = FlashExposure.strength(EYES, FORWARD, explosion).orElse(null);
        check(actual == expected, "distance=" + distance + ", angle=" + degrees + ": expected=" + expected + ", actual=" + actual);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
