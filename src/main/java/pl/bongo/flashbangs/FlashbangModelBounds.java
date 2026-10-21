package pl.bongo.flashbangs;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;

/** Bounds of the exported model, including Blockbench inflation and element rotations. */
public final class FlashbangModelBounds {
    public static final Vec3 MIN;
    public static final Vec3 MAX;
    static {
        try (var stream = FlashbangModelBounds.class.getResourceAsStream("/bongoflashbangs-model-bounds.json")) {
            if (stream == null) throw new IllegalStateException("Missing flashbang model bounds");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            MIN = vector(json.getAsJsonArray("min"));
            MAX = vector(json.getAsJsonArray("max"));
            if (MAX.x <= MIN.x || MAX.y <= MIN.y || MAX.z <= MIN.z)
                throw new IllegalStateException("Invalid flashbang model bounds");
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static Vec3 vector(JsonArray array) {
        if (array.size() != 3) throw new IllegalArgumentException("Expected three coordinates");
        var result = new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        if (!Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z))
            throw new IllegalArgumentException("Non-finite model bounds");
        return result;
    }
    public static Vec3 centerInBlocks() { return MIN.add(MAX).scale(0.5).subtract(8, 8, 8).scale(1.0 / 16.0); }
    public static Vec3 centerInDisplaySpace() {
        // Vanilla ItemDisplayRenderer rotates item geometry 180 degrees around Y.
        Vec3 center = centerInBlocks();
        return new Vec3(-center.x, center.y, -center.z);
    }
    public static double fittingScale(float width, float height) {
        Vec3 size = MAX.subtract(MIN).scale(1.0 / 16.0);
        return 0.9 * Math.min(width / size.x, Math.min(height / size.y, width / size.z));
    }
    public static double rotatingScale(float width, float height) {
        // The bounding-box diagonal is the diameter of a sphere containing every rotation.
        double diameter = MAX.subtract(MIN).length() / 16.0;
        return 0.9 * Math.min(width, height) / diameter;
    }
    private FlashbangModelBounds() {}
}
