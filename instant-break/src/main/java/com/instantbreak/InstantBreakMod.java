package com.instantbreak;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Мгновенная добыча блоков.
 *
 * Мод ничего не ломает сам: он только выдаёт игроку временные (не сохраняемые в мире)
 * модификаторы скорости добычи. Поэтому всё остальное работает как в ванилле:
 * прочность тратится как обычно, а дроп зависит от инструмента
 * (камень рукой ломается мгновенно, но не выпадает).
 */
public class InstantBreakMod implements ModInitializer {
	public static final String MOD_ID = "instantbreak";

	private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath(MOD_ID, "break_speed");
	private static final Identifier EFFICIENCY_ID = Identifier.fromNamespaceAndPath(MOD_ID, "mining_efficiency");

	/** Максимум, который разрешает игра для этих атрибутов, — 1024. */
	private static final double MAX = 1024.0;

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				apply(player);
			}
		});
	}

	private static void apply(ServerPlayer player) {
		// Скорость добычи: базовое 1.0 + 1023 = 1024. Работает и для руки.
		AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
		if (speed != null && !speed.hasModifier(SPEED_ID)) {
			speed.addTransientModifier(new AttributeModifier(SPEED_ID, MAX - 1.0, AttributeModifier.Operation.ADD_VALUE));
		}

		// Эффективность (как у зачарования): прибавляется к скорости подходящего инструмента.
		AttributeInstance efficiency = player.getAttribute(Attributes.MINING_EFFICIENCY);
		if (efficiency != null && !efficiency.hasModifier(EFFICIENCY_ID)) {
			efficiency.addTransientModifier(new AttributeModifier(EFFICIENCY_ID, MAX, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
