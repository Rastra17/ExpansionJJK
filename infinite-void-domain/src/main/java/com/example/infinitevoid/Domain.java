// src/main/java/com/example/infinitevoid/Domain.java
package com.example.infinitevoid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.example.infinitevoid.network.DomainPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class Domain {
    private final ServerPlayerEntity caster;
    private final ServerWorld world;
    private final long castStart = System.currentTimeMillis();
    private final long barrierComplete = castStart + 2_000;
    private final long activationTime = castStart + 3_000;
    private boolean barrierBuilt = false, activated = false, breaking = false, finished = false;
    private long domainActiveTime;
    private final Map<UUID, Vec3d> trapped = new HashMap<>();
    private final Map<UUID, Boolean> originalAI = new HashMap<>();
    private final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
    private final Map<BlockPos, BlockState> originalUpperBlocks = new HashMap<>();
    private final Map<BlockPos, BlockState> originalPlatformBlocks = new HashMap<>();
    private int currentBarrierHeight = 0;
    private final BlockPos domainCenter;
    private final int platformY; // The Y level of the platform
    private final Vec3d originalCasterPos;

    // Platform building animation
    private double currentPlatformRadius = 0;
    private boolean platformComplete = false;
    private long platformBuildStart = 0;

    // Breaking animation
    private boolean breakingStarted = false;
    private long breakingStartTime;
    private int currentBreakingHeight = DOMAIN_RADIUS;
    private double currentBreakingPlatformRadius = DOMAIN_RADIUS - 1;

    // Domain radius
    private static final int DOMAIN_RADIUS = 25;
    // Cooldown time in ticks (120 seconds = 2400 ticks)
    private static final int COOLDOWN_TICKS = 2400;

    public Domain(ServerPlayerEntity caster) {
        this.caster = caster;
        this.world = caster.getServerWorld();
        this.originalCasterPos = caster.getPos();

        // Get the exact block position under the player's feet
        BlockPos playerFeetPos = caster.getBlockPos().down();
        this.platformY = playerFeetPos.getY();

        // Center the domain on the block coordinates, not entity coordinates
        this.domainCenter = new BlockPos(playerFeetPos.getX(), platformY, playerFeetPos.getZ());

        startBarrierConstruction();
        ServerPlayNetworking.send(caster, new DomainPayloads.StartCastPayload());
    }

    public ServerPlayerEntity getCaster() {
        return caster;
    }

    public boolean isActive() {
        return activated;
    }

    public boolean isFinished() {
        return finished;
    }

    public void requestBreak() {
        if (activated && !breaking) {
            breaking = true;
            breakingStarted = true;
            breakingStartTime = System.currentTimeMillis();
            caster.sendMessage(Text.literal("§6Breaking Domain Expansion..."));
        }
    }

    public static boolean canCast(ServerPlayerEntity player) {
        return !player.hasStatusEffect(StatusEffects.MINING_FATIGUE);
    }

    private void startBarrierConstruction() {
        // Store original blocks for the barrier sphere
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

        // Store original blocks where the platform will be built (extend to barrier edge)
        for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
            for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance < DOMAIN_RADIUS) { // Platform extends to just before the barrier
                    BlockPos pos = new BlockPos(domainCenter.getX() + x, platformY,
                            domainCenter.getZ() + z);
                    originalPlatformBlocks.put(pos, world.getBlockState(pos));
                }
            }
        }
    }

    private boolean isInsideDomain(Vec3d pos) {
        double distance = pos.distanceTo(
                new Vec3d(domainCenter.getX(), domainCenter.getY(), domainCenter.getZ()));
        return distance <= DOMAIN_RADIUS - 1;
    }

    public void tick() {
        long now = System.currentTimeMillis();

        if (breaking && breakingStarted) {
            if (now < breakingStartTime + 3_000) {
                breakBarrierAndPlatform(now);
            } else {
                finish();
                return;
            }
        }

        if (!breaking && !barrierBuilt && now < barrierComplete) {
            buildBarrierLayer(now);
        } else if (!breaking && !barrierBuilt) {
            finishBarrier();
            barrierBuilt = true;
            platformBuildStart = now; // Start platform building after barrier is complete
        }

        if (!breaking && barrierBuilt && !platformComplete && now < activationTime) {
            buildPlatformLayer(now);
            playLightSpeedAnimation();
        } else if (!breaking && barrierBuilt && !platformComplete && now >= activationTime) {
            finishPlatform();
            platformComplete = true;
        }

        if (!breaking && !activated && now >= activationTime) {
            activateDomain();
            return;
        }

        if (activated && !breaking) {
            maintainStunEffects();
            playVoidSpaceEffects();
            preventEntitySpawning();
        }
    }

    private void preventEntitySpawning() {
        // Collect entities to remove in a separate list to avoid concurrent modification
        Box domainBox =
                new Box(domainCenter.getX() - DOMAIN_RADIUS, domainCenter.getY() - DOMAIN_RADIUS,
                        domainCenter.getZ() - DOMAIN_RADIUS, domainCenter.getX() + DOMAIN_RADIUS,
                        domainCenter.getY() + DOMAIN_RADIUS, domainCenter.getZ() + DOMAIN_RADIUS);

        // Create a list to store entities that need to be removed
        List<LivingEntity> entitiesToRemove = new ArrayList<>();

        world.getEntitiesByClass(LivingEntity.class, domainBox, entity -> {
            // Check if this entity is not tracked (meaning it spawned after domain activation)
            if (!trapped.containsKey(entity.getUuid()) && entity != caster
                    && isInsideDomain(entity.getPos())) {
                entitiesToRemove.add(entity);
            }
            return false;
        });

        // Now remove the entities after iteration is complete
        for (LivingEntity entity : entitiesToRemove) {
            entity.discard();
        }
    }

    private void buildBarrierLayer(long currentTime) {
        long elapsed = currentTime - castStart;
        double progress = (double) elapsed / 2000.0;

        int targetHeight = (int) (progress * (DOMAIN_RADIUS * 2 + 1)) - DOMAIN_RADIUS;

        if (targetHeight > currentBarrierHeight) {
            for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
                for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                    for (int y = currentBarrierHeight; y <= Math.min(targetHeight,
                            DOMAIN_RADIUS); y++) {
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance >= DOMAIN_RADIUS - 1 && distance <= DOMAIN_RADIUS + 1) {
                            BlockPos pos = domainCenter.add(x, y, z);
                            world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
                        }
                    }
                }
            }
            currentBarrierHeight = Math.min(targetHeight, DOMAIN_RADIUS);
        }
    }

    private void buildPlatformLayer(long currentTime) {
        if (platformBuildStart == 0)
            return;

        long elapsed = currentTime - platformBuildStart;
        double progress = (double) elapsed / 1000.0; // 1 second to build platform

        double targetRadius = progress * (DOMAIN_RADIUS - 1); // Extend to barrier edge

        if (targetRadius > currentPlatformRadius) {
            // Build from center outward
            for (int x = -(int) targetRadius; x <= (int) targetRadius; x++) {
                for (int z = -(int) targetRadius; z <= (int) targetRadius; z++) {
                    double distance = Math.sqrt(x * x + z * z);

                    // Check if this block is within the current build radius but wasn't built
                    // before
                    if (distance <= targetRadius && distance > currentPlatformRadius - 1
                            && distance < DOMAIN_RADIUS) {
                        BlockPos pos = new BlockPos(domainCenter.getX() + x, platformY,
                                domainCenter.getZ() + z);

                        // Skip if it's a barrier block
                        if (originalBlocks.containsKey(pos)) {
                            continue;
                        }

                        world.setBlockState(pos, Blocks.BLACK_CONCRETE.getDefaultState());
                    }
                }
            }
            currentPlatformRadius = Math.min(targetRadius, DOMAIN_RADIUS - 1);
        }
    }

    private void breakBarrierAndPlatform(long currentTime) {
        long elapsed = currentTime - breakingStartTime;
        double progress = (double) elapsed / 3000.0;

        // Break barrier from top to bottom
        int targetHeight = DOMAIN_RADIUS - (int) (progress * (DOMAIN_RADIUS * 2 + 1));
        if (targetHeight < currentBreakingHeight) {
            for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
                for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                    for (int y = currentBreakingHeight; y >= Math.max(targetHeight,
                            -DOMAIN_RADIUS); y--) {
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance >= DOMAIN_RADIUS - 1 && distance <= DOMAIN_RADIUS + 1) {
                            BlockPos pos = domainCenter.add(x, y, z);
                            if (originalBlocks.containsKey(pos)) {
                                world.setBlockState(pos, originalBlocks.get(pos));
                            }
                        }
                    }
                }
            }
            currentBreakingHeight = Math.max(targetHeight, -DOMAIN_RADIUS);
        }

        // Break platform from outside to center
        double targetPlatformRadius = (DOMAIN_RADIUS - 1) * (1 - progress);

        if (targetPlatformRadius < currentBreakingPlatformRadius) {
            for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
                for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                    double distance = Math.sqrt(x * x + z * z);

                    // Check if this block should be removed
                    if (distance > targetPlatformRadius
                            && distance <= currentBreakingPlatformRadius) {
                        BlockPos pos = new BlockPos(domainCenter.getX() + x, platformY,
                                domainCenter.getZ() + z);

                        if (originalPlatformBlocks.containsKey(pos)) {
                            world.setBlockState(pos, originalPlatformBlocks.get(pos));
                        }
                    }
                }
            }
            currentBreakingPlatformRadius = Math.max(targetPlatformRadius, 0);
        }
    }

    private void finishBarrier() {
        originalBlocks.forEach((pos, originalState) -> {
            world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
        });

        caster.sendMessage(Text.literal("§6Domain barrier complete..."));
    }

    private void finishPlatform() {
        // Ensure all platform blocks are placed to the edge
        for (int x = -DOMAIN_RADIUS; x <= DOMAIN_RADIUS; x++) {
            for (int z = -DOMAIN_RADIUS; z <= DOMAIN_RADIUS; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance < DOMAIN_RADIUS) { // Extend to just before barrier
                    BlockPos pos = new BlockPos(domainCenter.getX() + x, platformY,
                            domainCenter.getZ() + z);

                    // Skip barrier blocks
                    if (originalBlocks.containsKey(pos)) {
                        continue;
                    }

                    world.setBlockState(pos, Blocks.BLACK_CONCRETE.getDefaultState());
                }
            }
        }
    }

    private void playLightSpeedAnimation() {
        // Simple end rod effects during activation
        for (int i = 0; i < 20; i++) {
            double angle = (System.currentTimeMillis() / 20.0 + i * 18) % 360;
            double radians = Math.toRadians(angle);
            double radius = 15;

            double x = domainCenter.getX() + Math.cos(radians) * radius;
            double y = platformY + 5;
            double z = domainCenter.getZ() + Math.sin(radians) * radius;

            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0.1);
        }
    }

    private void activateDomain() {
        activated = true;
        domainActiveTime = System.currentTimeMillis();

        setupDomainStructure();
        trapEntitiesInDomain();

        ServerPlayNetworking.send(caster, new DomainPayloads.DomainActivatedPayload());
    }

    private void setupDomainStructure() {
        // Platform is already built during the animation phase

        // Remove ALL blocks in the entire domain interior except barrier and platform
        for (int x = -DOMAIN_RADIUS + 1; x <= DOMAIN_RADIUS - 1; x++) {
            for (int y = -DOMAIN_RADIUS + 1; y <= DOMAIN_RADIUS - 1; y++) {
                for (int z = -DOMAIN_RADIUS + 1; z <= DOMAIN_RADIUS - 1; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);

                    // Inside the sphere and not part of barrier
                    if (distance < DOMAIN_RADIUS - 1) {
                        BlockPos pos = domainCenter.add(x, y, z);

                        // Skip platform level
                        if (pos.getY() == platformY) {
                            continue;
                        }

                        // Skip if it's already stored as original block (barrier)
                        if (originalBlocks.containsKey(pos)) {
                            continue;
                        }

                        BlockState original = world.getBlockState(pos);
                        if (!original.isAir()) {
                            originalUpperBlocks.put(pos, original);
                            world.setBlockState(pos, Blocks.AIR.getDefaultState());
                        }
                    }
                }
            }
        }

        System.out.println(
                "Domain structure complete: platform at Y=" + platformY + ", interior cleared");
    }

    private void trapEntitiesInDomain() {
        Box area = new Box(domainCenter.getX() - DOMAIN_RADIUS, platformY - DOMAIN_RADIUS,
                domainCenter.getZ() - DOMAIN_RADIUS, domainCenter.getX() + DOMAIN_RADIUS,
                platformY + DOMAIN_RADIUS, domainCenter.getZ() + DOMAIN_RADIUS);

        // Teleport entities so their feet are on the platform
        double platformLevel = platformY + 1; // +1 so they stand ON the platform, not in it

        // First, teleport the caster
        Vec3d casterPos = caster.getPos();
        caster.setPosition(casterPos.x, platformLevel, casterPos.z);

        // Then teleport other entities
        world.getEntitiesByClass(LivingEntity.class, area, e -> e != caster).forEach(e -> {
            Vec3d entityPos = e.getPos();

            if (isInsideDomain(entityPos)) {
                trapped.put(e.getUuid(), entityPos);

                // Teleport with feet on platform
                e.setPosition(entityPos.x, platformLevel, entityPos.z);

                if (e instanceof MobEntity mob) {
                    NbtCompound nbt = new NbtCompound();
                    mob.writeNbt(nbt);
                    originalAI.put(e.getUuid(), !nbt.getBoolean("NoAI"));
                    nbt.putBoolean("NoAI", true);
                    mob.readNbt(nbt);
                }

                // Apply complete stun
                e.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 255, false, false));
                e.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 255, false, false));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 255,
                        false, false));
                e.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false));
                e.setVelocity(0, 0, 0);
                e.velocityModified = true;
            }
        });

        System.out.println(
                "Trapped " + trapped.size() + " entities on platform at Y=" + platformLevel);
    }

    private void maintainStunEffects() {
        // Keep entities static in their elevated positions
        trapped.keySet().forEach(id -> {
            Entity ent = world.getEntity(id);
            if (ent instanceof LivingEntity e) {
                e.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 255, false, false));
                e.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.WEAKNESS, 10, 255, false, false));
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 10, 255,
                        false, false));
                e.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.BLINDNESS, 10, 0, false, false));
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
        // Moon orbit calculation
        double orbitTime = System.currentTimeMillis() / 3000.0; // Complete orbit every 3 seconds
        double orbitAngle = orbitTime * 2 * Math.PI;
        double orbitRadius = DOMAIN_RADIUS * 0.6;

        // Moon position with orbit
        double moonX = domainCenter.getX() + Math.cos(orbitAngle) * orbitRadius;
        double moonY = platformY + 10; // Fixed height
        double moonZ = domainCenter.getZ() + Math.sin(orbitAngle) * orbitRadius;

        // Pulsating moon effect
        double pulseTime = System.currentTimeMillis() / 1000.0;
        double pulseIntensity = 0.5 + 0.5 * Math.sin(pulseTime * Math.PI); // Oscillates between 0
                                                                           // and 1
        int particleCount = (int) (30 + 20 * pulseIntensity); // Between 30 and 50 particles

        // Create hollow pulsating moon using end rod particles
        for (int i = 0; i < particleCount; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double r = 3; // Moon radius

            // Create hollow sphere by only placing particles on the surface
            double x = moonX + r * Math.sin(phi) * Math.cos(theta);
            double y = moonY + r * Math.sin(phi) * Math.sin(theta);
            double z = moonZ + r * Math.cos(phi);

            // Check distance from moon center to ensure hollow effect
            double distFromCenter = Math.sqrt((x - moonX) * (x - moonX) + (y - moonY) * (y - moonY)
                    + (z - moonZ) * (z - moonZ));

            // Only place particles on the surface (between radius 2.5 and 3)
            if (distFromCenter >= 2.5 && distFromCenter <= 3) {
                world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }

        // Stars on the ceiling (upper part of the sphere)
        for (int i = 0; i < 200; i++) {
            double angle = Math.random() * 360;
            double radians = Math.toRadians(angle);

            // Generate positions in upper hemisphere only
            double heightFactor = 0.3 + Math.random() * 0.7; // Between 30% and 100% of radius
            double y = domainCenter.getY() + DOMAIN_RADIUS * heightFactor;

            // Calculate horizontal distance based on height
            double maxHorizontalRadius = Math.sqrt(DOMAIN_RADIUS * DOMAIN_RADIUS
                    - (y - domainCenter.getY()) * (y - domainCenter.getY())) - 2;
            double horizontalRadius = Math.random() * maxHorizontalRadius;

            double x = domainCenter.getX() + Math.cos(radians) * horizontalRadius;
            double z = domainCenter.getZ() + Math.sin(radians) * horizontalRadius;

            // Small chance for each star to appear
            if (Math.random() < 0.3) {
                world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }

        // End portal effects on black concrete surface (decorative only)
        for (int i = 0; i < 100; i++) {
            double x = domainCenter.getX() + (Math.random() - 0.5) * (DOMAIN_RADIUS * 2 - 4);
            double z = domainCenter.getZ() + (Math.random() - 0.5) * (DOMAIN_RADIUS * 2 - 4);
            double y = platformY + 0.5; // Just above the platform

            // Check if position is within platform bounds
            double distance = Math.sqrt((x - domainCenter.getX()) * (x - domainCenter.getX())
                    + (z - domainCenter.getZ()) * (z - domainCenter.getZ()));
            if (distance < DOMAIN_RADIUS - 1) {
                world.spawnParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.1, 0, 0.1, 0);
            }
        }

        // End rod particles near the barrier walls
        for (int i = 0; i < 50; i++) {
            double angle = Math.random() * 360;
            double radians = Math.toRadians(angle);
            double height = platformY + Math.random() * (DOMAIN_RADIUS * 2) - DOMAIN_RADIUS;

            // Place near the inner surface of the barrier
            double x = domainCenter.getX() + Math.cos(radians) * (DOMAIN_RADIUS - 2);
            double z = domainCenter.getZ() + Math.sin(radians) * (DOMAIN_RADIUS - 2);

            world.spawnParticles(ParticleTypes.END_ROD, x, height, z, 1, 0, 0, 0, 0);
        }

        // White smoke concentrated near obsidian walls
        for (int i = 0; i < 40; i++) {
            double angle = Math.random() * 360;
            double radians = Math.toRadians(angle);
            double verticalOffset = (Math.random() - 0.5) * DOMAIN_RADIUS;

            // Place very close to inner obsidian surface
            double distanceFromCenter = DOMAIN_RADIUS - 1 - Math.random() * 0.5;
            double x = domainCenter.getX() + Math.cos(radians) * distanceFromCenter;
            double y = platformY + verticalOffset;
            double z = domainCenter.getZ() + Math.sin(radians) * distanceFromCenter;

            world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 1, 0.1, 0.1, 0.1, 0);
        }
    }

    private void finish() {
        // FIRST: Teleport caster back to original position
        caster.setPosition(originalCasterPos.x, originalCasterPos.y, originalCasterPos.z);

        // THEN: Restore everything else
        restoreAllBlocks();
        restoreEntities();

        // Send deactivation packet to client
        ServerPlayNetworking.send(caster, new DomainPayloads.DomainDeactivatedPayload());

        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE,
                COOLDOWN_TICKS, 0, false, true, true));

        caster.sendMessage(Text
                .literal("§cDomain Expansion ended. You feel exhausted... (2 minutes cooldown)"));
        finished = true;
    }

    private void restoreAllBlocks() {
        // Restore upper half blocks first
        originalUpperBlocks.forEach((pos, originalState) -> {
            world.setBlockState(pos, originalState);
        });

        // Restore platform blocks
        originalPlatformBlocks.forEach((pos, originalState) -> {
            world.setBlockState(pos, originalState);
        });

        // Restore remaining barrier blocks (if any left)
        originalBlocks.forEach((pos, originalState) -> {
            if (world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN) {
                world.setBlockState(pos, originalState);
            }
        });

        System.out.println("Restored " + (originalBlocks.size() + originalUpperBlocks.size()
                + originalPlatformBlocks.size()) + " blocks");
    }

    private void restoreEntities() {
        // Note: Caster is already restored in finish() method

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

                e.setPosition(pos.x, pos.y, pos.z);
            }
        });

        System.out.println("Restored " + trapped.size() + " entities to original positions");
    }
}
