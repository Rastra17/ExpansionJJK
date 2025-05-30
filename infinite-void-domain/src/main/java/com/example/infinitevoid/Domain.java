// src/main/java/com/example/infinitevoid/Domain.java
package com.example.infinitevoid;

import com.example.infinitevoid.network.DomainPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Domain {
    private final ServerPlayerEntity caster;
    private final ServerWorld world;
    private final long castStart = System.currentTimeMillis();
    private final long barrierComplete = castStart + 2_000; // Barrier completes in 2 seconds
    private final long activationTime = castStart + 3_000; // Domain activates after 3 seconds
    private boolean barrierBuilt = false, activated = false, breaking = false, finished = false;
    private long domainActiveTime;
    private final Map<UUID, Vec3d> trapped = new HashMap<>();
    private final Map<UUID, Boolean> originalAI = new HashMap<>();
    private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>(); // For barrier
    private final Map<BlockPos, BlockState> originalInteriorBlocks = new HashMap<>(); // For interior blocks
    private int currentBarrierHeight = 0;
    private final BlockPos domainCenter;
    private final Vec3d originalCasterPos; // Store caster's original position
    
    // Breaking animation
    private boolean breakingStarted = false;
    private long breakingStartTime;
    private int currentBreakingHeight = DOMAIN_RADIUS; // Start from top

    // Domain radius
    private static final int DOMAIN_RADIUS = 25;
    // Cooldown time in ticks (120 seconds = 2400 ticks)
    private static final int COOLDOWN_TICKS = 2400;

    public Domain(ServerPlayerEntity caster) {
        this.caster = caster;
        this.world = caster.getServerWorld();
        this.domainCenter = caster.getBlockPos();
        this.originalCasterPos = caster.getPos(); // Store original caster position
        // Start building barrier immediately
        startBarrierConstruction();
        // tell the client to show the cast UI
        ServerPlayNetworking.send(caster, new DomainPayloads.StartCastPayload());
    }

    public ServerPlayerEntity getCaster() { return caster; }
    public boolean isActive()      { return activated; }
    public boolean isFinished()    { return finished; }
    public void requestBreak()     { 
        if (activated && !breaking) {
            breaking = true;
            breakingStarted = true;
            breakingStartTime = System.currentTimeMillis();
            caster.sendMessage(Text.literal("§6Breaking Domain Expansion..."));
        }
    }

    // Check if player can cast (not on cooldown)
    public static boolean canCast(ServerPlayerEntity player) {
        return !player.hasStatusEffect(StatusEffects.MINING_FATIGUE);
    }

    private void startBarrierConstruction() {
        // Pre-calculate all barrier positions but don't place yet
        for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
            for (int y = -DOMAIN_RADIUS; y <= DOMAIN_RADIUS; y++) {
                for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance >= DOMAIN_RADIUS - 1 && distance <= DOMAIN_RADIUS + 1) {
                        BlockPos pos = domainCenter.add(x, y, z);
                        originalBlocks.put(pos, world.getBlockState(pos));
                    }
                }
            }
        }
    }

    // Check if a position is inside the domain barrier
    private boolean isInsideDomain(Vec3d pos) {
        double distance = pos.distanceTo(new Vec3d(domainCenter.getX(), domainCenter.getY(), domainCenter.getZ()));
        return distance <= DOMAIN_RADIUS - 1;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        
        // Handle breaking animation
        if (breaking && breakingStarted) {
            if (now < breakingStartTime + 2_000) { // 2 second breaking animation
                breakBarrierLayer(now);
            } else {
                finish();
                return;
            }
        }
        
        // Build barrier gradually from bottom to top
        if (!breaking && !barrierBuilt && now < barrierComplete) {
            buildBarrierLayer(now);
        } else if (!breaking && !barrierBuilt) {
            finishBarrier();
            barrierBuilt = true;
        }
        
        // Light speed animation phase (between barrier completion and activation)
        if (!breaking && barrierBuilt && !activated && now < activationTime) {
            playLightSpeedAnimation();
        }
        
        // Activate domain
        if (!breaking && !activated && now >= activationTime) {
            activateDomain();
            return;
        }
        
        // Domain is active - no time limit, only ends when manually broken
        if (activated && !breaking) {
            // Maintain stun effects
            maintainStunEffects();
            // Play void space effects
            playVoidSpaceEffects();
        }
    }

    private void buildBarrierLayer(long currentTime) {
        long elapsed = currentTime - castStart;
        double progress = (double) elapsed / 2000.0; // 2 second build time
        
        int targetHeight = (int) (progress * (DOMAIN_RADIUS * 2 + 1)) - DOMAIN_RADIUS;
        
        if (targetHeight > currentBarrierHeight) {
            // Build this layer
            for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
                for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                    for (int y = currentBarrierHeight; y <= Math.min(targetHeight, DOMAIN_RADIUS); y++) {
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance >= DOMAIN_RADIUS - 1 && distance <= DOMAIN_RADIUS + 1) {
                            BlockPos pos = domainCenter.add(x, y, z);
                            world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
                            
                            // Particle effect when placing
                            world.spawnParticles(ParticleTypes.PORTAL, 
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 
                                3, 0.3, 0.3, 0.3, 0.1);
                        }
                    }
                }
            }
            currentBarrierHeight = Math.min(targetHeight, DOMAIN_RADIUS);
        }
    }

    private void breakBarrierLayer(long currentTime) {
        long elapsed = currentTime - breakingStartTime;
        double progress = (double) elapsed / 2000.0; // 2 second break time
        
        int targetHeight = DOMAIN_RADIUS - (int) (progress * (DOMAIN_RADIUS * 2 + 1));
        
        if (targetHeight < currentBreakingHeight) {
            // Break this layer (from top to bottom)
            for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
                for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                    for (int y = currentBreakingHeight; y >= Math.max(targetHeight, -DOMAIN_RADIUS); y--) {
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance >= DOMAIN_RADIUS - 1 && distance <= DOMAIN_RADIUS + 1) {
                            BlockPos pos = domainCenter.add(x, y, z);
                            if (originalBlocks.containsKey(pos)) {
                                world.setBlockState(pos, originalBlocks.get(pos));
                                
                                // Particle effect when breaking
                                world.spawnParticles(ParticleTypes.SMOKE, 
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 
                                    5, 0.3, 0.3, 0.3, 0.1);
                            }
                        }
                    }
                }
            }
            currentBreakingHeight = Math.max(targetHeight, -DOMAIN_RADIUS);
        }
    }

    private void finishBarrier() {
        // Ensure all barrier blocks are placed
        originalBlocks.forEach((pos, originalState) -> {
            world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
        });
        
        caster.sendMessage(Text.literal("§6Domain barrier complete..."));
    }

    private void playLightSpeedAnimation() {
        // Create colorful streaking lines
        for (int i = 0; i < 50; i++) {
            double angle = (System.currentTimeMillis() / 10.0 + i * 7.2) % 360;
            double radians = Math.toRadians(angle);
            double radius = 15 + Math.sin(System.currentTimeMillis() / 100.0) * 5;
            
            double x = domainCenter.getX() + Math.cos(radians) * radius;
            double y = domainCenter.getY() + Math.sin(System.currentTimeMillis() / 50.0 + i) * 10;
            double z = domainCenter.getZ() + Math.sin(radians) * radius;
            
            // Colorful particle streams
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0.3);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0, 0, 0.2);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0.1, 0.1, 0.1, 0.1);
        }
        
        // Spiral energy effects
        double spiralTime = (System.currentTimeMillis() - (activationTime - 1000)) / 1000.0;
        for (int i = 0; i < 20; i++) {
            double spiralAngle = spiralTime * 720 + i * 18;
            double spiralRadius = 20 - spiralTime * 15;
            if (spiralRadius > 0) {
                double x = domainCenter.getX() + Math.cos(Math.toRadians(spiralAngle)) * spiralRadius;
                double y = domainCenter.getY() + i - 10;
                double z = domainCenter.getZ() + Math.sin(Math.toRadians(spiralAngle)) * spiralRadius;
                
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0, 0, 0, 0.1);
            }
        }
    }

    private void activateDomain() {
        activated = true;
        domainActiveTime = System.currentTimeMillis();
        
        // Fill lower half and create black concrete surface, remove upper half blocks
        setupDomainStructure();
        
        // Trap entities that are inside the domain (including caster)
        trapEntitiesInDomain();
        
        // Send activation packet to client for big screen display
        ServerPlayNetworking.send(caster, new DomainPayloads.DomainActivatedPayload());
    }

    private void setupDomainStructure() {
        // Fill lower half with black concrete
        for (int x = -DOMAIN_RADIUS + 2; x <= DOMAIN_RADIUS - 2; x++) {
            for (int y = -DOMAIN_RADIUS + 2; y <= 0; y++) { // Lower half
                for (int z = -DOMAIN_RADIUS + 2; z <= DOMAIN_RADIUS - 2; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance < DOMAIN_RADIUS - 2) {
                        BlockPos pos = domainCenter.add(x, y, z);
                        
                        // Skip barrier blocks
                        if (originalBlocks.containsKey(pos)) {
                            continue;
                        }
                        
                        BlockState original = world.getBlockState(pos);
                        originalInteriorBlocks.put(pos, original);
                        
                        // Fill with black concrete
                        world.setBlockState(pos, Blocks.BLACK_CONCRETE.getDefaultState());
                    }
                }
            }
        }
        
        // Create horizontal black concrete surface at caster's Y level
        int surfaceY = domainCenter.getY();
        for (int x = -DOMAIN_RADIUS + 2; x <= DOMAIN_RADIUS - 2; x++) {
            for (int z = -DOMAIN_RADIUS + 2; z <= DOMAIN_RADIUS - 2; z++) {
                double distance = Math.sqrt(x * x + z * z); // Only check horizontal distance for surface
                if (distance < DOMAIN_RADIUS - 2) {
                    BlockPos pos = domainCenter.add(x, surfaceY, z);
                    
                    // Skip barrier blocks
                    if (originalBlocks.containsKey(pos)) {
                        continue;
                    }
                    
                    BlockState original = world.getBlockState(pos);
                    originalInteriorBlocks.put(pos, original);
                    
                    // Create black concrete surface
                    world.setBlockState(pos, Blocks.BLACK_CONCRETE.getDefaultState());
                }
            }
        }
        
        // Remove ALL blocks in upper half (above surface) except barrier
        for (int x = -DOMAIN_RADIUS + 2; x <= DOMAIN_RADIUS - 2; x++) {
            for (int y = 1; y <= DOMAIN_RADIUS - 2; y++) { // Upper half only
                for (int z = -DOMAIN_RADIUS + 2; z <= DOMAIN_RADIUS - 2; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance < DOMAIN_RADIUS - 2) {
                        BlockPos pos = domainCenter.add(x, y, z);
                        
                        // Skip barrier blocks
                        if (originalBlocks.containsKey(pos)) {
                            continue;
                        }
                        
                        BlockState original = world.getBlockState(pos);
                        originalInteriorBlocks.put(pos, original);
                        
                        // Replace with air for void effect
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
        
        System.out.println("Created domain structure: lower half filled, surface created, upper half cleared");
    }

    private void trapEntitiesInDomain() {
        Box area = new Box(
            caster.getX() - DOMAIN_RADIUS, caster.getY() - DOMAIN_RADIUS, caster.getZ() - DOMAIN_RADIUS,
            caster.getX() + DOMAIN_RADIUS, caster.getY() + DOMAIN_RADIUS, caster.getZ() + DOMAIN_RADIUS
        );
        
        // Teleport entities to stand on the black concrete surface (caster's Y level + 1)
        double standingLevel = caster.getY() + 1;
        
        // First, teleport the caster to the surface
        Vec3d casterPos = caster.getPos();
        caster.setPosition(casterPos.x, standingLevel, casterPos.z);
        
        // Then teleport other entities
        world.getEntitiesByClass(LivingEntity.class, area, e -> e != caster)
             .forEach(e -> {
                 Vec3d entityPos = e.getPos();
                 
                 // Only affect entities that are inside the domain barrier
                 if (isInsideDomain(entityPos)) {
                     trapped.put(e.getUuid(), entityPos); // Store original position
                     
                     // Teleport all trapped entities to stand on the black concrete surface
                     // Keep their X and Z coordinates the same, only change Y to surface + 1
                     e.setPosition(entityPos.x, standingLevel, entityPos.z);
                     
                     // Disable AI
                     if (e instanceof MobEntity mob) {
                         NbtCompound nbt = new NbtCompound();
                         mob.writeNbt(nbt);
                         originalAI.put(e.getUuid(), !nbt.getBoolean("NoAI"));
                         nbt.putBoolean("NoAI", true);
                         mob.readNbt(nbt);
                     }
                     
                     // Apply complete stun
                     e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, false, false));
                     e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 255, false, false));
                     e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 255, false, false));
                     e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false));
                     e.setVelocity(0, 0, 0);
                     e.velocityModified = true;
                 }
             });
        
        System.out.println("Trapped " + trapped.size() + " entities on black concrete surface at Y=" + standingLevel);
        System.out.println("Caster also teleported to surface level");
    }

    private void maintainStunEffects() {
        trapped.keySet().forEach(id -> {
            Entity ent = world.getEntity(id);
            if (ent instanceof LivingEntity e) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 255, false, false));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 10, 255, false, false));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 10, 255, false, false));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 10, 0, false, false));
                e.setVelocity(0, 0, 0);
                e.velocityModified = true;
                
                if (e instanceof MobEntity mob) {
                    NbtCompound nbt = new NbtCompound();
                    mob.writeNbt(nbt);
                    nbt.putBoolean("NoAI", true);
                    mob.readNbt(nbt);
                }
            }
        });
    }

    private void playVoidSpaceEffects() {
        // Create black hole effect on one side (in upper void space)
        double blackHoleX = domainCenter.getX() + DOMAIN_RADIUS * 0.7;
        double blackHoleY = domainCenter.getY() + 10; // Above the surface
        double blackHoleZ = domainCenter.getZ();
        
        // Black hole particles
        for (int i = 0; i < 30; i++) {
            double angle = (System.currentTimeMillis() / 20.0 + i * 12) % 360;
            double radius = 8 + Math.sin(System.currentTimeMillis() / 100.0 + i) * 2;
            double x = blackHoleX + Math.cos(Math.toRadians(angle)) * radius;
            double y = blackHoleY + Math.sin(Math.toRadians(angle * 2)) * 3;
            double z = blackHoleZ + Math.sin(Math.toRadians(angle)) * radius;
            
            world.spawnParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0.2);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0, 0, 0, 0.1);
        }
        
        // Ink splash effects (mainly in upper void space)
        for (int i = 0; i < 15; i++) {
            double x = domainCenter.getX() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.5;
            double y = domainCenter.getY() + 1 + Math.random() * DOMAIN_RADIUS * 0.8; // Above surface
            double z = domainCenter.getZ() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.5;
            
            world.spawnParticles(ParticleTypes.SQUID_INK, x, y, z, 3, 1, 1, 1, 0.1);
            world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.5, 0.5, 0.5, 0.05);
        }
        
        // Stars in the upper void space
        for (int i = 0; i < 100; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double r = DOMAIN_RADIUS * 0.8 + Math.random() * DOMAIN_RADIUS * 0.4;
            double x = domainCenter.getX() + r * Math.sin(phi) * Math.cos(theta);
            double y = domainCenter.getY() + r * Math.sin(phi) * Math.sin(theta);
            double z = domainCenter.getZ() + r * Math.cos(phi);
            
            // Only show stars in upper void space (above surface)
            if (y >= domainCenter.getY() + 2 && Math.random() < 0.3) {
                world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }
        
        // Floating void energy (upper void space)
        for (int i = 0; i < 20; i++) {
            double x = domainCenter.getX() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.2;
            double y = domainCenter.getY() + 2 + Math.random() * DOMAIN_RADIUS * 0.6; // Above surface
            double z = domainCenter.getZ() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.2;
            
            world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0.2, 0.2, 0.2, 0.02);
        }
    }

    private void finish() {
        // Restore all blocks
        restoreAllBlocks();
        
        // Restore entities (including caster)
        restoreEntities();
        
        // Apply cooldown
        caster.addStatusEffect(new StatusEffectInstance(
            StatusEffects.MINING_FATIGUE, COOLDOWN_TICKS, 0, false, true, true));
        
        caster.sendMessage(Text.literal("§cDomain Expansion ended. You feel exhausted... (2 minutes cooldown)"));
        finished = true;
    }

    private void restoreAllBlocks() {
        // Restore ALL interior blocks first
        originalInteriorBlocks.forEach((pos, originalState) -> {
            world.setBlockState(pos, originalState);
        });
        
        // Restore remaining barrier blocks (if any left)
        originalBlocks.forEach((pos, originalState) -> {
            if (world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN) {
                world.setBlockState(pos, originalState);
            }
        });
        
        System.out.println("Restored " + (originalBlocks.size() + originalInteriorBlocks.size()) + " blocks");
    }

    private void restoreEntities() {
        // Restore caster to original position first
        caster.setPosition(originalCasterPos.x, originalCasterPos.y, originalCasterPos.z);
        
        // Then restore other entities
        trapped.forEach((id, pos) -> {
            Entity ent = world.getEntity(id);
            if (ent instanceof LivingEntity e) {
                e.removeStatusEffect(StatusEffects.SLOWNESS);
                e.removeStatusEffect(StatusEffects.WEAKNESS);
                e.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                e.removeStatusEffect(StatusEffects.BLINDNESS);
                
                if (e instanceof MobEntity mob && originalAI.containsKey(id)) {
                    NbtCompound nbt = new NbtCompound();
                    mob.writeNbt(nbt);
                    nbt.putBoolean("NoAI", !originalAI.get(id));
                    mob.readNbt(nbt);
                }
                
                // Restore to original position
                e.setPosition(pos.x, pos.y, pos.z);
            }
        });
        
        System.out.println("Restored caster and " + trapped.size() + " entities to original positions");
    }
}