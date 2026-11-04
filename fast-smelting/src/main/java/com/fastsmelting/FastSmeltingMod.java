package com.fastsmelting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Быстрая переплавка.
 *
 * Мод не меняет рецепты и не трогает приватные поля печи. Он просто даёт каждой
 * горящей печи (печь, плавильня, коптильня) ещё (SPEED - 1) обычных тиков за каждый
 * серверный тик. Топлива на один предмет уходит столько же, сколько в ванилле,
 * просто всё происходит в SPEED раз быстрее.
 *
 * Скорость задаётся в config/fastsmelting.properties (строка speed=20).
 */
public class FastSmeltingMod implements ModInitializer {
	public static final String MOD_ID = "fastsmelting";
	private static final int DEFAULT_SPEED = 20; // 10 секунд -> 0,5 секунды на предмет
	private static final int MAX_SPEED = 200;

	private static final Set<AbstractFurnaceBlockEntity> FURNACES = ConcurrentHashMap.newKeySet();
	private static int speed = DEFAULT_SPEED;

	@Override
	public void onInitialize() {
		speed = loadSpeed();

		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) FURNACES.add(furnace);
		});
		ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof AbstractFurnaceBlockEntity furnace) FURNACES.remove(furnace);
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> tickFurnaces());
	}

	private static void tickFurnaces() {
		if (speed <= 1) return;

		for (AbstractFurnaceBlockEntity furnace : FURNACES) {
			if (furnace.isRemoved() || !(furnace.getLevel() instanceof ServerLevel level)) {
				FURNACES.remove(furnace);
				continue;
			}

			BlockPos pos = furnace.getBlockPos();
			for (int i = 1; i < speed; i++) {
				// Состояние берём заново: после каждого тика печь может погаснуть.
				BlockState state = level.getBlockState(pos);
				if (!state.hasProperty(AbstractFurnaceBlock.LIT) || !state.getValue(AbstractFurnaceBlock.LIT)) break;
				AbstractFurnaceBlockEntity.serverTick(level, pos, state, furnace);
			}
		}
	}

	private static int loadSpeed() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".properties");
		Properties props = new Properties();
		try {
			if (Files.exists(file)) {
				try (var in = Files.newBufferedReader(file)) {
					props.load(in);
				}
			} else {
				props.setProperty("speed", String.valueOf(DEFAULT_SPEED));
				try (var out = Files.newBufferedWriter(file)) {
					props.store(out, "Fast Smelting: во сколько раз быстрее работают печи (1 = как в ванилле, максимум " + MAX_SPEED + ")");
				}
			}
			int value = Integer.parseInt(props.getProperty("speed", String.valueOf(DEFAULT_SPEED)).trim());
			return Math.max(1, Math.min(MAX_SPEED, value));
		} catch (IOException | NumberFormatException e) {
			return DEFAULT_SPEED;
		}
	}
}
