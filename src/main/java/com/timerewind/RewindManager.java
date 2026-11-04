package com.timerewind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Серверная логика отмотки времени.
 *
 * Каждый тик для каждого игрока пишется снимок состояния (позиция, здоровье, еда, скорость,
 * ближайшие мобы). Пока игрок держит клавишу — снимки берутся с конца и применяются
 * (то есть время идёт назад). Отпустил — игра продолжается с этого места.
 *
 * Смерть: если игрок умирает, а история не пуста, смерть отменяется на RESCUE_TICKS.
 * За это время нужно зажать клавишу. Не успел — умираешь по-настоящему.
 */
public final class RewindManager {
	// ===== Настройки (меняй тут) =====
	/** Сколько секунд истории хранить. */
	public static final int HISTORY_SECONDS = 30;
	/** Скорость отмотки: сколько тиков истории проматывается за 1 тик. 1 = реальная скорость, 2 = x2. */
	public static final int REWIND_SPEED = 2;
	/** Радиус, в котором откатываются мобы. */
	public static final double MOB_RADIUS = 32.0;
	/** Максимум мобов в одном снимке (защита от лагов). */
	public static final int MAX_MOBS = 64;
	/** Сколько секунд даётся на то, чтобы зажать клавишу после "смерти". */
	public static final int RESCUE_SECONDS = 6;
	// =================================

	private static final int MAX_HISTORY = 20 * HISTORY_SECONDS;
	private static final int RESCUE_TICKS = 20 * RESCUE_SECONDS;

	private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

	private RewindManager() {
	}

	private static final class State {
		final ArrayDeque<Snapshot> history = new ArrayDeque<>();
		volatile boolean holding;
		boolean rewinding;
		int rescueLeft;
	}

	private record MobSnap(UUID id, Vec3 pos, Vec3 motion, float yRot, float xRot, float headYaw, float health) {
	}

	private record Snapshot(ResourceKey<Level> dimension, Vec3 pos, Vec3 motion, float yRot, float xRot,
							float health, int food, float saturation, float fall, int fire, int air,
							List<MobSnap> mobs) {
	}

	// ---------- вход из сети ----------

	public static void setHolding(UUID id, boolean held) {
		STATES.computeIfAbsent(id, k -> new State()).holding = held;
	}

	// ---------- события ----------

	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
		// Раз в 10 секунд чистим данные вышедших игроков.
		if (server.getTickCount() % 200 == 0) {
			STATES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	/** Вызывается, когда сущность получила смертельный урон. true = умереть, false = отменить смерть. */
	public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) return true;
		// /kill и наша же "настоящая смерть" проходят без спасения.
		if (source.is(DamageTypes.GENERIC_KILL)) return true;

		State state = STATES.get(player.getUUID());
		if (state == null) return true;

		boolean canRescue = state.rescueLeft > 0 || !state.history.isEmpty();
		if (!canRescue) return true; // отматывать нечего — умираем

		if (state.rescueLeft == 0) {
			state.rescueLeft = RESCUE_TICKS;
			player.sendOverlayMessage(rescueMessage(RESCUE_SECONDS));
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.5f);
		}
		player.setHealth(1.0f); // иначе ванилла добьёт на следующем тике
		return false;
	}

	/** Во время отмотки игрок неуязвим. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player) {
			State state = STATES.get(player.getUUID());
			if (state != null && state.rewinding) return false;
		}
		return true;
	}

	// ---------- логика тика ----------

	private static void tickPlayer(ServerPlayer player) {
		State state = STATES.computeIfAbsent(player.getUUID(), k -> new State());

		// Игрок по-настоящему умер — история больше не нужна.
		if (!player.isAlive()) {
			state.history.clear();
			state.rewinding = false;
			state.rescueLeft = 0;
			return;
		}

		if (state.holding && !state.history.isEmpty()) {
			rewind(player, state);
			return;
		}
		state.rewinding = false;

		if (state.rescueLeft > 0) {
			state.rescueLeft--;
			if (state.rescueLeft == 0) {
				killForReal(player);
			} else if (state.rescueLeft % 20 == 0) {
				player.sendOverlayMessage(rescueMessage(state.rescueLeft / 20));
			}
			return; // умирающее состояние в историю не пишем
		}

		record(player, state);
	}

	private static void record(ServerPlayer player, State state) {
		ServerLevel level = (ServerLevel) player.level();

		List<MobSnap> mobs = new ArrayList<>();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(MOB_RADIUS),
				m -> m.isAlive() && !(m instanceof Player));
		for (LivingEntity e : near) {
			if (mobs.size() >= MAX_MOBS) break;
			mobs.add(new MobSnap(e.getUUID(), e.position(), e.getDeltaMovement(),
					e.getYRot(), e.getXRot(), e.getYHeadRot(), e.getHealth()));
		}

		state.history.addLast(new Snapshot(
				level.dimension(),
				player.position(), player.getDeltaMovement(),
				player.getYRot(), player.getXRot(),
				player.getHealth(),
				player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
				(float) player.fallDistance,
				player.getRemainingFireTicks(), player.getAirSupply(),
				mobs));

		while (state.history.size() > MAX_HISTORY) {
			state.history.pollFirst();
		}
	}

	private static void rewind(ServerPlayer player, State state) {
		ServerLevel level = (ServerLevel) player.level();

		Snapshot snap = null;
		for (int i = 0; i < REWIND_SPEED && !state.history.isEmpty(); i++) {
			snap = state.history.pollLast();
		}
		if (snap == null) return;

		// Перешли в другое измерение — дальше отматывать нельзя.
		if (!snap.dimension().equals(level.dimension())) {
			state.history.clear();
			return;
		}

		state.rewinding = true;
		state.rescueLeft = 0; // зажал вовремя — спасён

		// Игрок
		player.teleport(new TeleportTransition(level, snap.pos(), snap.motion(),
				snap.yRot(), snap.xRot(), TeleportTransition.DO_NOTHING));
		player.setHealth(snap.health());
		player.getFoodData().setFoodLevel(snap.food());
		player.getFoodData().setSaturation(snap.saturation());
		player.fallDistance = snap.fall();
		player.setRemainingFireTicks(snap.fire());
		player.setAirSupply(snap.air());

		// Мобы вокруг (те, что ещё живы)
		for (MobSnap m : snap.mobs()) {
			Entity e = level.getEntity(m.id());
			if (e instanceof LivingEntity living && living.isAlive()) {
				living.snapTo(m.pos());
				living.setYRot(m.yRot());
				living.setXRot(m.xRot());
				living.setYHeadRot(m.headYaw());
				living.setDeltaMovement(m.motion());
				living.setHealth(m.health());
				if (living instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}

		// Эффекты: частицы и звук
		level.sendParticles(ParticleTypes.REVERSE_PORTAL,
				player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.4, 0.7, 0.4, 0.1);
		if (player.tickCount % 3 == 0) {
			float pitch = 0.6f + 0.8f * ((float) state.history.size() / MAX_HISTORY);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, pitch);
		}
	}

	private static void killForReal(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		player.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
	}

	private static Component rescueMessage(int seconds) {
		return Component.literal("Ты погиб! Зажми ")
				.append(Component.keybind("key.timerewind.rewind"))
				.append(Component.literal(" — отмотка времени (" + seconds + " с)"));
	}
}
