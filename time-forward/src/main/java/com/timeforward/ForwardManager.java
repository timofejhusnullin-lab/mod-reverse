package com.timeforward;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ускорение времени = тот же механизм, что у команды /tick rate:
 * пока хоть один игрок держит клавишу, сервер работает в N раз быстрее
 * (мир, мобы, печи, рост растений и сам игрок), а при отпускании скорость возвращается.
 */
public final class ForwardManager {
	/** Верхняя граница множителя (защита от случайных 1000x). */
	public static final float MAX_MULTIPLIER = 100.0f;

	/** Кто сейчас держит клавишу -> какой множитель выбрал. */
	private static final Map<UUID, Float> HOLDERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_MESSAGE_NANOS = new ConcurrentHashMap<>();

	/** Обычная скорость сервера, запомненная перед ускорением. -1 = ускорения нет. */
	private static float baseRate = -1.0f;

	private ForwardManager() {
	}

	public static void set(UUID id, boolean held, float multiplier) {
		if (held) {
			float m = Float.isNaN(multiplier) ? 1.0f : Math.max(1.0f, Math.min(MAX_MULTIPLIER, multiplier));
			HOLDERS.put(id, m);
		} else {
			HOLDERS.remove(id);
			LAST_MESSAGE_NANOS.remove(id);
		}
	}

	public static void tick(MinecraftServer server) {
		// Вышел из игры, не отпустив клавишу — не держим ускорение.
		HOLDERS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);

		var rateManager = server.tickRateManager();

		if (HOLDERS.isEmpty()) {
			if (baseRate > 0.0f) {
				rateManager.setTickRate(baseRate);
				baseRate = -1.0f;
			}
			return;
		}

		float multiplier = 1.0f;
		for (float m : HOLDERS.values()) multiplier = Math.max(multiplier, m);

		if (baseRate < 0.0f) baseRate = rateManager.tickrate();
		float target = baseRate * multiplier;
		if (rateManager.tickrate() != target) {
			rateManager.setTickRate(target);
		}

		// Подпись над хотбаром раз в секунду реального времени.
		long now = System.nanoTime();
		for (UUID id : HOLDERS.keySet()) {
			Long last = LAST_MESSAGE_NANOS.get(id);
			if (last != null && now - last < 1_000_000_000L) continue;
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) continue;
			LAST_MESSAGE_NANOS.put(id, now);
			player.sendOverlayMessage(Component.literal("⏩ Время x" + format(HOLDERS.get(id))));
		}
	}

	public static String format(float value) {
		return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
	}
}
