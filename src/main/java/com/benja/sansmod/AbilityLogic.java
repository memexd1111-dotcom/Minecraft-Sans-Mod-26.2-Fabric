package com.benja.sansmod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Toda la lógica de las habilidades (solo servidor).
 * Para no necesitar renderers propios, los objetos visibles (huesos, calaveras, muro) son
 * armor stands invisibles con un objeto en la cabeza, y los rayos son partículas.
 */
public final class AbilityLogic {
    /** Altura de la cabeza de un armor stand respecto a sus pies (para centrar el objeto). */
    private static final double HEAD_OFFSET = 1.75;
    /** Evita recursión cuando "Mal momento" re-aplica el daño aumentado. */
    private static boolean boosting = false;

    private AbilityLogic() {}

    private record Hit(Vec3 end, LivingEntity entity) {}

    // =====================================================================
    //  Utilidades
    // =====================================================================

    private static void msg(ServerPlayer player, Component text) {
        player.sendSystemMessage(text, true);
    }

    private static void sound(ServerLevel level, Vec3 pos, SoundEvent event, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, event, SoundSource.PLAYERS, volume, pitch);
    }

    private static void particles(ServerLevel level, net.minecraft.core.particles.ParticleOptions type,
                                  Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(type, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    private static double damageMul(PlayerState st, long now) {
        return st.seriousUntil > now ? SansConfig.d("eye.damage_multiplier") : 1.0;
    }

    private static boolean valid(Entity self, LivingEntity e) {
        return e != self && e.isAlive() && !e.isSpectator() && e.getType() != EntityType.ARMOR_STAND;
    }

    private static Hit trace(ServerLevel level, Entity owner, Vec3 from, Vec3 dir, double range) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();

        AABB box = new AABB(from, end).inflate(1.0);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 bestPos = end;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> valid(owner, x))) {
            Optional<Vec3> r = e.getBoundingBox().inflate(0.3).clip(from, end);
            if (r.isPresent()) {
                double d = from.distanceToSqr(r.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = e;
                    bestPos = r.get();
                }
            }
        }
        return new Hit(best != null ? bestPos : end, best);
    }

    private static void hurt(ServerLevel level, ServerPlayer owner, LivingEntity target, float base, PlayerState st) {
        long now = level.getServer().getTickCount();
        target.invulnerableTime = 0; // que varios golpes seguidos cuenten
        target.hurtServer(level, level.damageSources().playerAttack(owner), (float) (base * damageMul(st, now)));
        if (st.karmaOn && target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER,
                    (int) (SansConfig.d("karma.wither_seconds") * 20), 0));
        }
    }

    private static LivingEntity makeStand(ServerLevel level, Vec3 headPos, float yaw, ItemStack head) {
        LivingEntity stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
        if (stand == null) return null;
        stand.setPos(headPos.x, headPos.y - HEAD_OFFSET, headPos.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setYRot(yaw);
        stand.setYBodyRot(yaw);
        stand.setYHeadRot(yaw);
        stand.setItemSlot(EquipmentSlot.HEAD, head);
        level.addFreshEntity(stand);
        return stand;
    }

    private static void moveStand(LivingEntity stand, Vec3 headPos) {
        if (stand != null && stand.isAlive()) {
            stand.setPos(headPos.x, headPos.y - HEAD_OFFSET, headPos.z);
        }
    }

    private static float yawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    // =====================================================================
    //  Lanzar habilidad (llega desde el cliente)
    // =====================================================================

    public static void cast(ServerPlayer player, int index) {
        if (index < 0 || index >= Ability.ALL.length) return;
        if (!player.isAlive() || player.isSpectator()) return;

        Ability ability = Ability.ALL[index];
        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();

        if (SansConfig.b("require_item") && !SansMod.hasCharm(player)) {
            msg(player, Component.translatable("msg.sansmod.need_charm"));
            return;
        }
        PlayerState st = PlayerState.of(player);
        if (now < st.cooldownEnd[index]) {
            double secs = (st.cooldownEnd[index] - now) / 20.0;
            msg(player, Component.translatable("msg.sansmod.cooldown", String.format("%.1f", secs)));
            return;
        }

        boolean ok = switch (ability) {
            case DODGE -> toggleDodge(player, st);
            case KARMA -> toggleKarma(player, st);
            case BONE -> bone(player, level, st);
            case BONE_WALL -> boneWall(player, level, st);
            case BLASTER -> blaster(player, level, st);
            case BLASTER_RAIN -> blasterRain(player, level, st);
            case TELEKINESIS -> telekinesis(player, level, st);
            case TELEPORT -> teleport(player, level);
            case EYE -> eye(player, level, st, now);
            case BAD_TIME -> badTime(player, level, st, now);
        };

        if (ok) {
            int cd = ability.cooldownTicks();
            st.cooldownEnd[index] = now + cd;
            st.cooldownTotal[index] = cd;
            if (!ability.toggle) {
                player.causeFoodExhaustion((float) SansConfig.d("exhaustion_per_cast"));
            }
            sendSync(player, st, now);
        }
    }

    // ---------- interruptores ----------

    private static boolean toggleDodge(ServerPlayer p, PlayerState st) {
        st.dodgeOn = !st.dodgeOn;
        msg(p, Component.translatable(st.dodgeOn ? "msg.sansmod.dodge_on" : "msg.sansmod.dodge_off"));
        return true;
    }

    private static boolean toggleKarma(ServerPlayer p, PlayerState st) {
        st.karmaOn = !st.karmaOn;
        msg(p, Component.translatable(st.karmaOn ? "msg.sansmod.karma_on" : "msg.sansmod.karma_off"));
        return true;
    }

    // ---------- huesos ----------

    private static boolean bone(ServerPlayer p, ServerLevel level, PlayerState st) {
        MinecraftServer server = level.getServer();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        int count = Math.max(1, SansConfig.i("bone.count"));
        double spread = Math.toRadians(SansConfig.d("bone.spread_degrees"));
        double speed = Math.max(0.5, SansConfig.d("bone.speed"));
        double range = SansConfig.d("bone.range");

        for (int k = 0; k < count; k++) {
            double angle = (k - (count - 1) / 2.0) * spread;
            Vec3 dir = look.yRot((float) angle).normalize();
            Hit hit = trace(level, p, eye, dir, range);
            Vec3 start = eye.add(dir.scale(0.9));
            double dist = Math.max(1.0, eye.distanceTo(hit.end()));
            int ticks = Math.max(1, (int) Math.ceil(dist / speed));

            LivingEntity stand = makeStand(level, start, yawOf(dir), new ItemStack(ModItems.BONE_PROJECTILE));
            for (int t = 1; t <= ticks; t++) {
                final int tt = t;
                Scheduler.later(server, t, () -> moveStand(stand, start.add(dir.scale(speed * tt))));
            }
            Scheduler.later(server, ticks + 1, () -> {
                if (stand != null) stand.discard();
            });

            LivingEntity target = hit.entity();
            if (target != null) {
                Scheduler.later(server, ticks, () -> {
                    if (target.isAlive()) {
                        hurt(level, p, target, (float) SansConfig.d("bone.damage"), st);
                    }
                });
            }
        }
        sound(level, eye, SoundEvents.SKELETON_SHOOT, 1.0f, 1.3f);
        return true;
    }

    private static boolean boneWall(ServerPlayer p, ServerLevel level, PlayerState st) {
        MinecraftServer server = level.getServer();
        Vec3 look = p.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0, look.z);
        fwd = fwd.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : fwd.normalize();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);

        double distance = SansConfig.d("bone_wall.distance");
        int width = Math.max(1, SansConfig.i("bone_wall.width"));
        int rise = Math.max(2, SansConfig.i("bone_wall.rise_ticks"));
        Vec3 base = p.position().add(fwd.scale(distance));
        double floorY = p.getY();
        float yaw = yawOf(fwd);

        List<LivingEntity> stands = new ArrayList<>();
        for (int i = 0; i < width; i++) {
            double off = (i - (width - 1) / 2.0);
            Vec3 headPos = new Vec3(base.x + right.x * off, floorY - 0.2, base.z + right.z * off);
            LivingEntity s = makeStand(level, headPos, yaw, new ItemStack(ModItems.BONE_WALL));
            if (s != null) stands.add(s);
        }
        // sube desde el suelo
        double step = 2.0 / rise;
        for (int t = 1; t <= rise; t++) {
            Scheduler.later(server, t, () -> {
                for (LivingEntity s : stands) {
                    if (s.isAlive()) s.setPos(s.getX(), s.getY() + step, s.getZ());
                }
            });
        }
        sound(level, base, SoundEvents.BONE_BLOCK_BREAK, 1.5f, 0.7f);

        // golpe cuando termina de subir
        final Vec3 fFwd = fwd;
        Scheduler.later(server, rise, () -> {
            double half = width / 2.0 + 0.5;
            double[] xs = {base.x + right.x * half + fFwd.x * 0.9, base.x - right.x * half + fFwd.x * 0.9,
                    base.x + right.x * half - fFwd.x * 0.9, base.x - right.x * half - fFwd.x * 0.9};
            double[] zs = {base.z + right.z * half + fFwd.z * 0.9, base.z - right.z * half + fFwd.z * 0.9,
                    base.z + right.z * half - fFwd.z * 0.9, base.z - right.z * half - fFwd.z * 0.9};
            double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
            double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
            double minZ = Math.min(Math.min(zs[0], zs[1]), Math.min(zs[2], zs[3]));
            double maxZ = Math.max(Math.max(zs[0], zs[1]), Math.max(zs[2], zs[3]));
            AABB box = new AABB(minX, floorY - 0.5, minZ, maxX, floorY + 3.0, maxZ);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> valid(p, x))) {
                hurt(level, p, e, (float) SansConfig.d("bone_wall.damage"), st);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                e.hurtMarked = true;
            }
        });
        Scheduler.later(server, rise + 25, () -> {
            for (LivingEntity s : stands) s.discard();
        });
        return true;
    }

    // ---------- blasters ----------

    private static boolean blaster(ServerPlayer p, ServerLevel level, PlayerState st) {
        MinecraftServer server = level.getServer();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 skullPos = eye.add(look.scale(2.2));
        LivingEntity skull = makeStand(level, skullPos, yawOf(look), new ItemStack(ModItems.BLASTER_SKULL));

        int charge = Math.max(2, SansConfig.i("blaster.charge_ticks"));
        sound(level, skullPos, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.5f);
        for (int t = 0; t < charge; t += 2) {
            Scheduler.later(server, t, () -> particles(level, ParticleTypes.ELECTRIC_SPARK, skullPos, 4, 0.35, 0.05));
        }
        Scheduler.later(server, charge, () -> {
            Vec3 dir = p.isRemoved() ? look : p.getLookAngle();
            fireBeam(level, p, skullPos, dir, SansConfig.d("blaster.range"),
                    (float) SansConfig.d("blaster.damage"), SansConfig.d("blaster.beam_radius"), st);
        });
        Scheduler.later(server, charge + 12, () -> {
            if (skull != null) skull.discard();
        });
        return true;
    }

    private static boolean blasterRain(ServerPlayer p, ServerLevel level, PlayerState st) {
        MinecraftServer server = level.getServer();
        Hit hit = trace(level, p, p.getEyePosition(), p.getLookAngle(), SansConfig.d("blaster_rain.range"));
        Vec3 target = hit.entity() != null
                ? hit.entity().position().add(0, hit.entity().getBbHeight() / 2.0, 0)
                : hit.end();

        int count = Math.max(1, SansConfig.i("blaster_rain.count"));
        double radius = SansConfig.d("blaster_rain.radius");
        for (int k = 0; k < count; k++) {
            double angle = k * 2.0 * Math.PI / count;
            Vec3 skullPos = new Vec3(target.x + Math.cos(angle) * radius,
                    target.y + 3.0 + (k % 2) * 0.8, target.z + Math.sin(angle) * radius);
            Vec3 dir = target.subtract(skullPos).normalize();
            LivingEntity skull = makeStand(level, skullPos, yawOf(dir), new ItemStack(ModItems.BLASTER_SKULL));
            int delay = 10 + k * 3;
            Scheduler.later(server, 0, () -> sound(level, skullPos, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f));
            Scheduler.later(server, delay, () -> fireBeam(level, p, skullPos, dir,
                    skullPos.distanceTo(target) + 3.0, (float) SansConfig.d("blaster_rain.damage"), 1.3, st));
            Scheduler.later(server, delay + 12, () -> {
                if (skull != null) skull.discard();
            });
        }
        return true;
    }

    private static void fireBeam(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 dir,
                                 double range, float damage, double radius, PlayerState st) {
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        double len = from.distanceTo(end);

        AABB box = new AABB(from, end).inflate(radius + 0.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> valid(owner, x))) {
            if (e.getBoundingBox().inflate(radius).clip(from, end).isPresent()) {
                hurt(level, owner, e, damage, st);
            }
        }

        sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.3f);
        MinecraftServer server = level.getServer();
        for (int rep = 0; rep < 4; rep++) { // el rayo se queda unos ticks
            Scheduler.later(server, rep * 2, () -> {
                for (double d = 0; d <= len; d += 0.6) {
                    particles(level, ParticleTypes.END_ROD, from.add(dir.scale(d)), 2, 0.12, 0.0);
                }
            });
        }
        particles(level, ParticleTypes.POOF, end, 12, 0.4, 0.05);
    }

    // ---------- telequinesis ----------

    private static boolean telekinesis(ServerPlayer p, ServerLevel level, PlayerState st) {
        MinecraftServer server = level.getServer();
        Hit hit = trace(level, p, p.getEyePosition(), p.getLookAngle(), SansConfig.d("telekinesis.range"));
        LivingEntity target = hit.entity();
        if (target == null) {
            msg(p, Component.translatable("msg.sansmod.no_target"));
            return false;
        }

        int lift = Math.max(4, SansConfig.i("telekinesis.lift_ticks"));
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, lift, 3, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, lift + 10, 0, false, false));
        sound(level, target.position(), SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.2f);

        for (int t = 0; t < lift; t += 2) {
            Scheduler.later(server, t, () -> particles(level, ParticleTypes.SOUL_FIRE_FLAME,
                    target.position().add(0, target.getBbHeight() / 2.0, 0), 6, 0.45, 0.02));
        }
        Scheduler.later(server, lift, () -> {
            if (!target.isAlive()) return;
            target.removeEffect(MobEffects.LEVITATION);
            target.setDeltaMovement(0, -2.5, 0);
            target.hurtMarked = true;
        });
        Scheduler.later(server, lift + 6, () -> {
            if (!target.isAlive()) return;
            hurt(level, p, target, (float) SansConfig.d("telekinesis.slam_damage"), st);
            particles(level, ParticleTypes.POOF, target.position(), 15, 0.5, 0.08);
            sound(level, target.position(), SoundEvents.BONE_BLOCK_BREAK, 1.2f, 0.6f);
        });
        return true;
    }

    // ---------- teletransporte ----------

    private static boolean teleport(ServerPlayer p, ServerLevel level) {
        Vec3 look = p.getLookAngle();
        double max = SansConfig.d("teleport.distance");
        Vec3 origin = p.position();
        Vec3 dest = null;
        for (double d = max; d >= 1.0; d -= 0.5) {
            Vec3 offset = look.scale(d);
            if (level.noCollision(p, p.getBoundingBox().move(offset))) {
                dest = origin.add(offset);
                break;
            }
        }
        if (dest == null) {
            msg(p, Component.translatable("msg.sansmod.no_space"));
            return false;
        }
        particles(level, ParticleTypes.PORTAL, origin.add(0, 1, 0), 30, 0.4, 0.3);
        p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        particles(level, ParticleTypes.PORTAL, dest.add(0, 1, 0), 30, 0.4, 0.3);
        sound(level, dest, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.2f);
        return true;
    }

    // ---------- ojo brillante ----------

    private static boolean eye(ServerPlayer p, ServerLevel level, PlayerState st, long now) {
        int ticks = (int) (SansConfig.d("eye.duration_seconds") * 20);
        st.seriousUntil = now + ticks;
        p.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0, false, false));
        sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.5f);
        msg(p, Component.translatable("msg.sansmod.eye"));
        return true;
    }

    // ---------- mal momento ----------

    private static boolean badTime(ServerPlayer p, ServerLevel level, PlayerState st, long now) {
        st.badTimeUntil = now + (long) (SansConfig.d("bad_time.duration_seconds") * 20);
        sound(level, p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1.0f, 0.8f);
        msg(p, Component.translatable("msg.sansmod.bad_time"));
        return true;
    }

    // =====================================================================
    //  Tick del servidor: tareas, recuperación, efectos continuos, sincronización
    // =====================================================================

    public static void serverTick(MinecraftServer server) {
        Scheduler.tick(server);
        long now = server.getTickCount();
        long recovery = Math.max(1, (long) (SansConfig.d("dodge.recovery_seconds") * 20));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerState st = PlayerState.of(p);
            ServerLevel level = (ServerLevel) p.level();

            if (st.fatigue > 0 && now - st.lastRecoverTick >= recovery) {
                st.fatigue--;
                st.lastRecoverTick = now;
            }
            if (st.seriousUntil > now && now % 2 == 0) {
                eyeParticles(p, level);
            }
            if (st.badTimeUntil > now && now % 5 == 0) {
                badTimeAura(p, level);
            }
            if (now % 4 == 0 && SansMod.hasCharm(p)) {
                sendSync(p, st, now);
            }
        }
    }

    private static void eyeParticles(ServerPlayer p, ServerLevel level) {
        Vec3 look = p.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 pos = p.getEyePosition().add(look.scale(0.45)).subtract(right.scale(0.14)).add(0, 0.03, 0);
        particles(level, ParticleTypes.SOUL_FIRE_FLAME, pos, 1, 0.01, 0.0);
        particles(level, ParticleTypes.ELECTRIC_SPARK, pos, 1, 0.03, 0.0);
    }

    private static void badTimeAura(ServerPlayer p, ServerLevel level) {
        double radius = SansConfig.d("bad_time.radius");
        AABB box = p.getBoundingBox().inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> valid(p, x))) {
            if (e.getType().getCategory() == MobCategory.MONSTER) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));
            }
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8.0;
            Vec3 pos = p.position().add(Math.cos(a) * radius, 0.2, Math.sin(a) * radius);
            particles(level, ParticleTypes.SOUL_FIRE_FLAME, pos, 1, 0.05, 0.0);
        }
    }

    private static void sendSync(ServerPlayer p, PlayerState st, long now) {
        int n = Ability.ALL.length;
        int[] remaining = new int[n];
        int[] total = new int[n];
        for (int i = 0; i < n; i++) {
            remaining[i] = (int) Math.max(0, st.cooldownEnd[i] - now);
            total[i] = st.cooldownTotal[i];
        }
        ServerPlayNetworking.send(p, new SyncPayload(remaining, total, st.fatigue,
                SansConfig.i("dodge.max_fatigue"), st.dodgeOn, st.karmaOn,
                (int) Math.max(0, st.seriousUntil - now), (int) Math.max(0, st.badTimeUntil - now)));
    }

    // =====================================================================
    //  Daño recibido: esquive automático y "Mal momento"
    // =====================================================================

    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (boosting || !(entity instanceof ServerPlayer player)) return true;
        if (!SansMod.hasCharm(player)) return true;

        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();
        PlayerState st = PlayerState.of(player);

        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true; // vacío, /kill...

        // Mal momento: recibes más daño y no puedes esquivar
        if (st.badTimeUntil > now) {
            double mul = SansConfig.d("bad_time.damage_taken_multiplier");
            if (mul > 1.0001) {
                boosting = true;
                try {
                    player.hurtServer(level, source, (float) (amount * mul));
                } finally {
                    boosting = false;
                }
                return false;
            }
            return true;
        }

        if (!st.dodgeOn) return true;
        Entity attacker = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        if (attacker == null) return true; // caídas, fuego, hambre... no se esquivan

        if (now - st.lastDodgeTick <= SansConfig.i("dodge.window_ticks")) return false; // sigues esquivando
        if (st.fatigue >= SansConfig.i("dodge.max_fatigue")) return true;              // cansado

        st.fatigue++;
        st.lastDodgeTick = now;
        st.lastRecoverTick = now;

        Vec3 away = player.position().subtract(attacker.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
        double sign = player.getRandom().nextBoolean() ? 1.0 : -1.0;
        Vec3 step = new Vec3(-flat.z, 0, flat.x).scale(2.0 * sign);
        Scheduler.later(server, 1, () -> sidestep(player, level, step));
        return false;
    }

    private static void sidestep(ServerPlayer player, ServerLevel level, Vec3 step) {
        if (!player.isAlive()) return;
        for (Vec3 s : new Vec3[] {step, step.scale(-1.0)}) {
            if (level.noCollision(player, player.getBoundingBox().move(s))) {
                Vec3 from = player.position();
                Vec3 dest = from.add(s);
                particles(level, ParticleTypes.POOF, from.add(0, 1, 0), 8, 0.3, 0.02);
                player.connection.teleport(dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
                particles(level, ParticleTypes.POOF, dest.add(0, 1, 0), 8, 0.3, 0.02);
                sound(level, dest, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.6f);
                return;
            }
        }
        sound(level, player.position(), SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.6f);
    }
}
