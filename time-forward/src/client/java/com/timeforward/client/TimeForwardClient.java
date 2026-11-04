package com.timeforward.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.mojang.blaze3d.platform.InputConstants;
import com.timeforward.ForwardManager;
import com.timeforward.ForwardPayload;
import com.timeforward.TimeForwardMod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

public class TimeForwardClient implements ClientModInitializer {
	/** Ступени скорости для клавиш [ и ]. В конфиге можно указать любое число. */
	private static final float[] STEPS = {1.5f, 2f, 3f, 5f, 8f, 10f, 15f, 20f, 30f, 50f, 100f};
	private static final float DEFAULT_SPEED = 5f;

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TimeForwardMod.MOD_ID, "main"));

	private static final KeyMapping FORWARD_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.timeforward.forward", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_X, CATEGORY));
	private static final KeyMapping FASTER_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.timeforward.faster", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_RIGHTBRACKET, CATEGORY));
	private static final KeyMapping SLOWER_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.timeforward.slower", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_LEFTBRACKET, CATEGORY));

	private float speed = DEFAULT_SPEED;
	private boolean sentDown = false;
	private float sentSpeed = 0f;

	@Override
	public void onInitializeClient() {
		speed = loadSpeed();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				sentDown = false;
				return;
			}

			while (FASTER_KEY.consumeClick()) changeSpeed(client, true);
			while (SLOWER_KEY.consumeClick()) changeSpeed(client, false);

			boolean down = FORWARD_KEY.isDown();
			if (down != sentDown || (down && speed != sentSpeed)) {
				sentDown = down;
				sentSpeed = speed;
				// canSend = false, если на сервере нет мода.
				if (ClientPlayNetworking.canSend(ForwardPayload.TYPE)) {
					ClientPlayNetworking.send(new ForwardPayload(down, speed));
				}
			}
		});
	}

	private void changeSpeed(Minecraft client, boolean up) {
		float next;
		if (up) {
			next = STEPS[STEPS.length - 1];
			for (float s : STEPS) {
				if (s > speed + 0.01f) { next = s; break; }
			}
		} else {
			next = STEPS[0];
			for (int i = STEPS.length - 1; i >= 0; i--) {
				if (STEPS[i] < speed - 0.01f) { next = STEPS[i]; break; }
			}
		}
		speed = Math.max(1.0f, Math.min(ForwardManager.MAX_MULTIPLIER, next));
		saveSpeed(speed);
		client.gui.hud.setOverlayMessage(Component.literal("Скорость времени: x" + ForwardManager.format(speed)), false);
	}

	// ---------- конфиг: config/timeforward.properties (строка speed=5) ----------

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(TimeForwardMod.MOD_ID + ".properties");
	}

	private static float loadSpeed() {
		Path file = configFile();
		try {
			if (!Files.exists(file)) {
				saveSpeed(DEFAULT_SPEED);
				return DEFAULT_SPEED;
			}
			Properties props = new Properties();
			try (var in = Files.newBufferedReader(file)) {
				props.load(in);
			}
			float value = Float.parseFloat(props.getProperty("speed", String.valueOf(DEFAULT_SPEED)).trim());
			return Math.max(1.0f, Math.min(ForwardManager.MAX_MULTIPLIER, value));
		} catch (IOException | NumberFormatException e) {
			return DEFAULT_SPEED;
		}
	}

	private static void saveSpeed(float value) {
		Properties props = new Properties();
		props.setProperty("speed", String.valueOf(value));
		try (var out = Files.newBufferedWriter(configFile())) {
			props.store(out, "Time Forward: во сколько раз ускоряется время при зажатой клавише (1..100)");
		} catch (IOException ignored) {
			// не критично: скорость просто не сохранится
		}
	}
}
