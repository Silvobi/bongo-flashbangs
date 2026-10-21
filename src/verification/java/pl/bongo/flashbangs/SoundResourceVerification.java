package pl.bongo.flashbangs;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.resources.sounds.SoundEventRegistrationSerializer;

/** Parse the actual resource-pack registration using Minecraft's own sound JSON parser. */
public final class SoundResourceVerification {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of(args[0]).resolve("assets/bongoflashbangs");
        var json = JsonParser.parseString(Files.readString(assets.resolve("sounds.json"))).getAsJsonObject();
        var gson = new GsonBuilder().registerTypeAdapter(SoundEventRegistration.class,
            new SoundEventRegistrationSerializer()).create();
        var event = gson.fromJson(json.get("earring"), SoundEventRegistration.class);
        check(event.getSounds().size() == 1, "one ring variant");
        var sound = event.getSounds().getFirst();
        check(sound.getLocation().equals(FlashRinging.ID), "sound ID maps to the ring resource");
        check(sound.shouldPreload() && !sound.shouldStream(), "short clip preloaded for consistent start");
        Path ogg = Path.of(args[0]).resolve("assets/" + sound.getPath().getNamespace() + "/" + sound.getPath().getPath());
        byte[] bytes = Files.readAllBytes(ogg);
        check(bytes.length > 4 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S', "Ogg resource resolved");
        System.out.println("BONGO_SOUND_RESOURCE_PASS: actual Minecraft sound registration parser, preload, exact server sound ID and Ogg resource path.");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
