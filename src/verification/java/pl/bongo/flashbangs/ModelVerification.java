package pl.bongo.flashbangs;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Arrays;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Checks the actual Minecraft parser, original artwork data, texture bytes and flight bounds. */
public final class ModelVerification {
    public static void main(String[] args) throws Exception {
        Path pack=Path.of(args[0]), source=Path.of(args[1]);
        var original=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
        Path file=pack.resolve("assets/bongoflashbangs/models/item/flashbang.json");
        var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        check(json.get("display").equals(original.get("display")),"author's display settings preserved exactly");
        CuboidModel model;
        try(var reader=Files.newBufferedReader(file)) { model=CuboidModel.fromStream(reader); }
        var geometry=(UnbakedCuboidGeometry)model.geometry();
        check(geometry.elements().size()==8,"all eight elements parsed by Minecraft");
        check(model.transforms().getTransform(ItemDisplayContext.ON_SHELF).scale().x()==1.85f,"shelf display parsed");
        check(model.transforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).rotation().x()==-31f,"held display parsed");
        check(model.transforms().getTransform(ItemDisplayContext.NONE).equals(net.minecraft.client.resources.model.cuboid.ItemTransform.NO_TRANSFORM),"flight doesn't inherit item-frame transform");
        Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY), hi=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var e:geometry.elements()) {
            for(int i=0;i<8;i++) {
                var point=new Vector3f((i&1)==0?e.from().x():e.to().x(),(i&2)==0?e.from().y():e.to().y(),(i&4)==0?e.from().z():e.to().z()).div(16f);
                if(e.rotation()!=null) point.sub(e.rotation().origin()).mulPosition(e.rotation().transform()).add(e.rotation().origin());
                point.mul(16f); lo.min(point); hi.max(point);
            }
        }
        check(lo.distance(new Vector3f((float)FlashbangModelBounds.MIN.x,(float)FlashbangModelBounds.MIN.y,(float)FlashbangModelBounds.MIN.z))<0.0001,"Minecraft rotation/inflation minimum bounds match export");
        check(hi.distance(new Vector3f((float)FlashbangModelBounds.MAX.x,(float)FlashbangModelBounds.MAX.y,(float)FlashbangModelBounds.MAX.z))<0.0001,"Minecraft rotation/inflation maximum bounds match export: "+hi);
        byte[] png=Files.readAllBytes(pack.resolve("assets/bongoflashbangs/textures/item/flashbang.png"));
        String encoded=original.getAsJsonArray("textures").get(0).getAsJsonObject().get("source").getAsString().split(",",2)[1];
        check(Arrays.equals(png,Base64.getDecoder().decode(encoded)),"embedded texture extracted without changes");
        var image=ImageIO.read(new java.io.ByteArrayInputStream(png));
        check(image.getWidth()==32 && image.getHeight()==32,"32x32 texture decodes");
        var size=FlashbangModelBounds.MAX.subtract(FlashbangModelBounds.MIN).scale(1.0/16.0);
        double scale=FlashbangModelBounds.fittingScale(0.364f,0.364f);
        check(size.x*scale<=0.364*0.90001 && size.y*scale<=0.364*0.90001 && size.z*scale<=0.364*0.90001,"flight model fits collision body");
        var center=FlashbangModelBounds.centerInDisplaySpace();
        for(int i=0;i<8;i++) {
            double x=(((i&1)==0?lo.x:hi.x)/16.0-0.5);
            double y=(((i&2)==0?lo.y:hi.y)/16.0-0.5);
            double z=(((i&4)==0?lo.z:hi.z)/16.0-0.5);
            // The display renderer's built-in Y half-turn precedes the display transform.
            check(Math.abs((-x-center.x)*scale)<0.182001 && Math.abs((y-center.y)*scale)<0.182001
                && Math.abs((-z-center.z)*scale)<0.182001,"flight center includes vanilla item-display half-turn");
        }
        System.out.println("BONGO_MODEL_PASS: real Minecraft model parser, eight elements, original display settings, multi-axis rotations, inflation, exact PNG bytes and flight bounds.");
    }
    private static void check(boolean value,String label) {if(!value)throw new AssertionError(label);}
}
