package pl.bongo.flashbangs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.MoveSimulationType;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.UpdateInterval;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;

/** Runs only in the separate disposable dedicated verification server. */
public final class ProjectileVerification implements ModInitializer {
    private final List<FlashGrenade> grenades = new ArrayList<>();
    private final AtomicInteger detonations = new AtomicInteger();
    private Snowball normal;
    private int started = -1;
    private boolean collisionsChecked;
    private PacketProbe probe;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                ServerLevel world = server.overworld();
                // No air drag: retain loaded space for the fast rebound fixture through its fuse.
                for(int x=-32;x<=3;x++) for(int z=-1;z<=1;z++) world.setChunkForced(x,z,true);
                world.getChunk(0,0); world.getChunk(1,0); world.getChunk(2,0); world.getChunk(3,0);
                for (int x=0;x<55;x++) for(int y=99;y<111;y++) for(int z=0;z<5;z++)
                    world.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(), 3);
                for(int y=101;y<109;y++) for(int z=0;z<4;z++) world.setBlock(new BlockPos(4,y,z),Blocks.STONE.defaultBlockState(),3);
                world.setBlock(new BlockPos(10,100,0),Blocks.STONE.defaultBlockState(),3);
                world.setBlock(new BlockPos(20,100,0),Blocks.SMOOTH_STONE_SLAB.defaultBlockState(),3);
                for(int y=101;y<109;y++) world.setBlock(new BlockPos(34,y,0),Blocks.STONE.defaultBlockState(),3);
                spawn(world,new Vec3(2.5,102.5,0.5),new Vec3(3,0,0));
                spawn(world,new Vec3(10.5,102,0.5),new Vec3(0,-2,0));
                spawn(world,new Vec3(20.5,102,0.5),new Vec3(0,-2,0));
                spawn(world,new Vec3(2.5,106.5,0.5),new Vec3(3,0,0.4));
                spawn(world,new Vec3(30.5,104,0.5),new Vec3(15,0,0));
                normal=new Snowball(world,2.5,104.5,0.5,new ItemStack(Items.SNOWBALL));
                normal.setDeltaMovement(3,0,0); world.addFreshEntity(normal);
                check(BongoFlashbangs.isFlashbang(BongoFlashbangs.flashbangItem()),"marked item recognized");
                check(!BongoFlashbangs.isFlashbang(new ItemStack(Items.SNOWBALL)),"normal snowball unaffected");
                for(var grenade:grenades) {
                    check(grenade.getType()==EntityTypes.SULFUR_CUBE,"native sulfur cube entity type");
                    check(grenade.getMoveSimulationType()==MoveSimulationType.AUTHORITATIVE_SIDE,"no projectile-style client simulation");
                    check(grenade.getAttributeValue(Attributes.SCALE)==FlashGrenade.GRENADE_SCALE,"scaled physics body");
                    check(grenade.getBbWidth()<0.5 && grenade.getBbHeight()<0.5,"grenade-sized hitbox: "+grenade.getBbWidth()+" x "+grenade.getBbHeight());
                    check(grenade.model().getVehicle()==grenade,"model rides the physics body");
                    check(grenade.model().getType()==EntityTypes.ITEM_DISPLAY && grenade.isInvisible(),"visible model and hidden body");
                    check(!grenade.shouldBeSaved() && !grenade.model().shouldBeSaved(),"both grenade entities are transient");
                }
                probe = new PacketProbe(world, grenades.get(0));
                FlashServerVerification.run(world);
                CraftingAndThrowVerification.run(world);
                SoundMuteVerification.run(world);
                PhysicsAndSafetyVerification.run(world);
                started=server.getTickCount();
            } catch(Throwable failure) { failure.printStackTrace(); System.out.println("BONGO_PROJECTILE_FAIL setup"); server.halt(false); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(started<0)return;
            int age=server.getTickCount()-started;
            try {
                if(!grenades.get(0).isRemoved()) probe.update(grenades.get(0));
                for(var grenade:grenades) if(!grenade.isRemoved())
                    check(server.overworld().noCollision(grenade,grenade.getBoundingBox().deflate(0.00001)),"full grenade body stays outside blocks");
                if(!collisionsChecked && grenades.get(0).tickCount>=2) {
                    var wall=grenades.get(0); check(!wall.isRemoved() && wall.getDeltaMovement().x<0 && wall.getX()<4,
                        "wall bounce: age="+wall.tickCount+", removed="+wall.isRemoved()+", pos="+wall.position()+", speed="+wall.getDeltaMovement());
                    var floor=grenades.get(1); check(floor.getDeltaMovement().y>0 && floor.getY()>=101,"ground bounce");
                    var slab=grenades.get(2); check(slab.getDeltaMovement().y>0 && slab.getY()>=100.5,"slab collision shape");
                    var glance=grenades.get(3); check(glance.getDeltaMovement().x<0 && glance.getDeltaMovement().z>0,"glancing reflection");
                    var fast=grenades.get(4); check(fast.getDeltaMovement().x<0 && fast.getX()<34,"fast raycast doesn't tunnel");
                    check(normal.isRemoved(),"ordinary snowball still breaks on wall");
                    check(detonations.get()==0,"impact doesn't detonate grenade");
                    System.out.println("BONGO_PROJECTILE_COLLISIONS_PASS");
                    collisionsChecked=true;
                }
                if(age>200) throw new AssertionError("projectiles didn't tick/detonate in loaded forced chunks");
                if(collisionsChecked && detonations.get()==grenades.size()) {
                    for(var grenade:grenades) check(grenade.isRemoved() && grenade.model().isRemoved(),"fuse removes body and model");
                    check(detonations.get()==grenades.size(),"one detonation per grenade after fuse");
                    check(probe.positionPackets>=probe.movingUpdates && probe.movingUpdates>5,
                        "positions emitted each moving server tick: "+probe.positionPackets+" packets / "+probe.movingUpdates+" moves / "+probe.updates+" updates");
                    check(probe.steppedPackets>0,"native mob interpolation paths emitted");
                    probe.replica.discard();
                    System.out.println("BONGO_PROJECTILE_PASS: native sulfur cube, scaled body, wall/ground/slab/fast collisions, model passenger, no penetration, fuse, removal of both entities and "+probe.positionPackets+" native position packets with safe interpolation paths");
                    started=-1; server.halt(false);
                }
            } catch(Throwable failure) {failure.printStackTrace();System.out.println("BONGO_PROJECTILE_FAIL age="+age);started=-1;server.halt(false);}
        });
    }
    private void spawn(ServerLevel world,Vec3 position,Vec3 speed) {
        var grenade=new FlashGrenade(world,position,BongoFlashbangs.flashbangItem().copyWithCount(1), g -> detonations.incrementAndGet());
        grenade.setDeltaMovement(speed);world.addFreshEntity(grenade);check(grenade.attachModel(),"model attached");grenades.add(grenade);
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}

    private static final class PacketProbe implements ServerEntity.Synchronizer {
        final FlashGrenade replica;
        final ServerEntity tracker;
        final ServerLevel world;
        Vec3 previous;
        int positionPackets, steppedPackets, updates, movingUpdates;
        final List<String> sent = new ArrayList<>();
        PacketProbe(ServerLevel world, FlashGrenade source) {
            this.world=world;
            replica=new FlashGrenade(world,source.position(),BongoFlashbangs.flashbangItem(),g -> {});
            previous=source.position();
            tracker=new ServerEntity(world,replica,UpdateInterval.periodic(EntityTypes.SULFUR_CUBE.updateInterval()),true,this);
        }
        void update(FlashGrenade source) {
            updates++;
            boolean moving=source.position().distanceToSqr(replica.position())>0.00001;
            if(moving) movingUpdates++;
            int before=positionPackets;
            sent.clear();
            replica.setPos(source.position()); replica.setDeltaMovement(source.getDeltaMovement());
            replica.syncPosition=true;
            replica.needsSync=true;
            tracker.sendChanges();
            check(!moving || positionPackets==before+1,"missing or duplicated movement update: tick="+updates
                +", precise="+replica.getRequiresPrecisePosition()+", source="+source.position()+", sent="+sent);
        }
        @Override public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
            sent.add(packet.getClass().getSimpleName());
            if(packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle) {
                for(var child:bundle.subPackets()) sendToTrackingPlayers(child);
                return;
            }
            if(packet instanceof ClientboundEntityPositionSyncPacket position) {
                positionPackets++;
                if(position.position() instanceof PositionPath.Stepped steps) {
                    steppedPackets++;
                    for(var step:steps.steps()) sampleSegment(step.position());
                } else sampleSegment(position.position().endPosition());
            }
        }
        private void sampleSegment(Vec3 endpoint) {
            Vec3 start=previous;
            for(int i=1;i<=8;i++) {
                Vec3 point=start.lerp(endpoint,i/8.0);
                var body=replica.getBoundingBox().move(point.subtract(replica.position())).deflate(0.00001);
                check(world.noCollision(replica,body),"interpolated client path intersects a wall: "+point);
            }
            previous=endpoint;
        }
        @Override public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {sendToTrackingPlayers(packet);}
        @Override public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet,
                java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> filter) {sendToTrackingPlayers(packet);}
    }
}
