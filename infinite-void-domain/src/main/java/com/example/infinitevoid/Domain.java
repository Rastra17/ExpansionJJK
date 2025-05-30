// src/main/java/com/example/infinitevoid/Domain.java
package com.example.infinitevoid;

import java.util.HashMap;
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
    private double currentBreakingPlatformRadius = DOMAIN_RADIUS - 2;

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

        // Store original blocks where the platform will be built
        for (int x = -DOMAIN_RADIUS + 2; x <= DOMAIN_RADIUS - 2; x++) {
            for (int z = -DOMAIN_RADIUS + 2; z <= DOMAIN_RADIUS - 2; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance < DOMAIN_RADIUS - 2) {
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

                            world.spawnParticles(ParticleTypes.PORTAL, pos.getX() + 0.5,
                                    pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.1);
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

        double targetRadius = progress * (DOMAIN_RADIUS - 2);

        if (targetRadius > currentPlatformRadius) {
            // Build from center outward
            for (int x = -(int) targetRadius; x <= (int) targetRadius; x++) {
                for (int z = -(int) targetRadius; z <= (int) targetRadius; z++) {
                    double distance = Math.sqrt(x * x + z * z);

                    // Check if this block is within the current build radius but wasn't built
                    // before
                    // Check if this block is within the current build radius but wasn't built
                    // before
                    if (distance <= targetRadius && distance > currentPlatformRadius - 1
                            && distance < DOMAIN_RADIUS - 2) {
                        BlockPos pos = new BlockPos(domainCenter.getX() + x, platformY,
                                domainCenter.getZ() + z);

                        // Skip if it's a barrier block
                        if (originalBlocks.containsKey(pos)) {
                            continue;
                        }

                        world.setBlockState(pos, Blocks.BLACK_CONCRETE.getDefaultState());

                        // Particle effect for building
                        world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1,
                                pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.05);
                    }
                }
            }
            currentPlatformRadius = Math.min(targetRadius, DOMAIN_RADIUS - 2);
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

                                world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5,
                                        pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.1);
                            }
                        }
                    }
                }
            }
            currentBreakingHeight = Math.max(targetHeight, -DOMAIN_RADIUS);
        }

        // Break platform from outside to center
        double targetPlatformRadius = (DOMAIN_RADIUS - 2) * (1 - progress);

        if (targetPlatformRadius < currentBreakingPlatformRadius) {
            for (int x = -DOMAIN_RADIUS + 2; x <= DOMAIN_RADIUS - 2; x++) {
                for (int z = -DOMAIN_RADIUS + 2; z <= DOMAIN_RADIUS - 2; z++) {
                    double distance = Math.sqrt(x * x + z * z);

                    // Check if this block should be removed
                    if (distance > targetPlatformRadius
                            && distance <= currentBreakingPlatformRadius) {
                        BlockPos pos = new BlockPos(domainCenter.getX() + x, platformY,
                                domainCenter.getZ() + z);

                        if (originalPlatformBlocks.containsKey(pos)) {
                            world.setBlockState(pos, originalPlatformBlocks.get(pos));

                            // Particle effect for breaking
                            world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5,
                                    pos.getY() + 1, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.05);
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
        // Ensure all platform blocks are placed
        for (int x = -DOMAIN_RADIUS + 2; x <= DOMAIN_RADIUS - 2; x++) {
            for (int z = -DOMAIN_RADIUS + 2; z <= DOMAIN_RADIUS - 2; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance < DOMAIN_RADIUS - 2) {
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
        for (int i = 0; i < 50; i++) {
            double angle = (System.currentTimeMillis() / 10.0 + i * 7.2) % 360;
            double radians = Math.toRadians(angle);
            double radius = 15 + Math.sin(System.currentTimeMillis() / 100.0) * 5;

            double x = domainCenter.getX() + Math.cos(radians) * radius;
            double y = domainCenter.getY() + Math.sin(System.currentTimeMillis() / 50.0 + i) * 10;
            double z = domainCenter.getZ() + Math.sin(radians) * radius;

            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0.3);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0, 0, 0.2);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0.1, 0.1, 0.1, 0.1);
        }

        double spiralTime = (System.currentTimeMillis() - (activationTime - 1000)) / 1000.0;
        for (int i = 0; i < 20; i++) {
            double spiralAngle = spiralTime * 720 + i * 18;
            double spiralRadius = 20 - spiralTime * 15;
            if (spiralRadius > 0) {
                double x =
                        domainCenter.getX() + Math.cos(Math.toRadians(spiralAngle)) * spiralRadius;
                double y = domainCenter.getY() + i - 10;
                double z =
                        domainCenter.getZ() + Math.sin(Math.toRadians(spiralAngle)) * spiralRadius;

                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0, 0, 0, 0.1);
            }
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

        // Remove ALL blocks in upper half (above platform) except barrier
        for (int x = -DOMAIN_RADIUS + 2; x <= DOMAIN_RADIUS - 2; x++) {
            for (int y = 1; y <= DOMAIN_RADIUS - 2; y++) { // Above platform
                for (int z = -DOMAIN_RADIUS + 2; z <= DOMAIN_RADIUS - 2; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    if (distance < DOMAIN_RADIUS - 2) {
                        BlockPos pos = domainCenter.add(x, y, z);

                        // Skip barrier blocks
                        if (originalBlocks.containsKey(pos)) {
                            continue;
                        }

                        BlockState original = world.getBlockState(pos);
                        originalUpperBlocks.put(pos, original);

                        // Replace with air for void effect
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        System.out.println(
                "Domain structure complete: platform at Y=" + platformY + ", upper space cleared");
    }

    private void trapEntitiesInDomain() {
        Box area = new Box(domainCenter.getX() - DOMAIN_RADIUS, platformY - DOMAIN_RADIUS,
                domainCenter.getZ() - DOMAIN_RADIUS, domainCenter.getX() + DOMAIN_RADIUS,
                platformY + DOMAIN_RADIUS, domainCenter.getZ() + DOMAIN_RADIUS);

        // Teleport entities 2 blocks above the platform
        double elevatedLevel = platformY + 3;

        // First, teleport the caster
        Vec3d casterPos = caster.getPos();
        caster.setPosition(casterPos.x, elevatedLevel, casterPos.z);

        // Then teleport other entities
        world.getEntitiesByClass(LivingEntity.class, area, e -> e != caster).forEach(e -> {
            Vec3d entityPos = e.getPos();

            if (isInsideDomain(entityPos)) {
                trapped.put(e.getUuid(), entityPos);

                // Teleport 2 blocks above platform
                e.setPosition(entityPos.x, elevatedLevel, entityPos.z);

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

        System.out.println("Trapped " + trapped.size() + " entities 2 blocks above platform at Y="
                + elevatedLevel);
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
        // Create black hole effect on one side (in upper void space only)
        double blackHoleX = domainCenter.getX() + DOMAIN_RADIUS * 0.7;
        double blackHoleY = platformY + 15; // Well above the platform
        double blackHoleZ = domainCenter.getZ();

        // Black hole particles (only in upper half)
        for (int i = 0; i < 30; i++) {
            double angle = (System.currentTimeMillis() / 20.0 + i * 12) % 360;
            double radius = 8 + Math.sin(System.currentTimeMillis() / 100.0 + i) * 2;
            double x = blackHoleX + Math.cos(Math.toRadians(angle)) * radius;
            double y = blackHoleY + Math.sin(Math.toRadians(angle * 2)) * 3;
            double z = blackHoleZ + Math.sin(Math.toRadians(angle)) * radius;

            // Only spawn if above platform level
            if (y > platformY + 1) {
                world.spawnParticles(ParticleTypes.PORTAL, x, y, z, 1, 0, 0, 0, 0.2);
                world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 1, 0, 0, 0, 0.1);
            }
        }

        // Ink splash effects (only in upper void space)
        // Ink splash effects (only in upper void space)
        for (int i = 0; i < 15; i++) {
            double x = domainCenter.getX() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.5;
            double y = platformY + 2 + Math.random() * DOMAIN_RADIUS * 0.8; // Above platform
            double z = domainCenter.getZ() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.5;

            // Only spawn if above platform level
            if (y > platformY + 1) {
                world.spawnParticles(ParticleTypes.SQUID_INK, x, y, z, 3, 1, 1, 1, 0.1);
                world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.5, 0.5, 0.5, 0.05);
            }
        }

        // Stars in the upper void space only
        for (int i = 0; i < 100; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double r = DOMAIN_RADIUS * 0.8 + Math.random() * DOMAIN_RADIUS * 0.4;
            double x = domainCenter.getX() + r * Math.sin(phi) * Math.cos(theta);
            double y = platformY + r * Math.sin(phi) * Math.sin(theta);
            double z = domainCenter.getZ() + r * Math.cos(phi);

            // Only show stars in upper void space (above platform)
            if (y >= platformY + 3 && Math.random() < 0.3) {
                world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }

        // Floating void energy (upper void space only)
        for (int i = 0; i < 20; i++) {
            double x = domainCenter.getX() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.2;
            double y = platformY + 3 + Math.random() * DOMAIN_RADIUS * 0.6; // Above platform
            double z = domainCenter.getZ() + (Math.random() - 0.5) * DOMAIN_RADIUS * 1.2;

            // Only spawn if above platform level
            if (y > platformY + 2) {
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 1, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    private void finish() {
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
        caster.setPosition(originalCasterPos.x, originalCasterPos.y, originalCasterPos.z);

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

        System.out.println(
                "Restored caster and " + trapped.size() + " entities to original positions");
    }
}
