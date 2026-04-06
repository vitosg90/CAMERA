package ru.ivanov.entitycam;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public final class EntityCamClient implements ClientModInitializer {
	private static final int SEARCH_RADIUS_BLOCKS = 64;
	private static final int SEARCH_RADIUS_SQUARED = SEARCH_RADIUS_BLOCKS * SEARCH_RADIUS_BLOCKS;

	private static KeyBinding toggleKey;
	private static KeyBinding nextKey;
	private static KeyBinding prevKey;

	private static Entity originalCameraEntity;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"KeyBinding.Category.MISC",
			GLFW.GLFW_KEY_V,
			"KeyBinding.Category.MISC"
		));

		nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"KeyBinding.Category.MISC",
			GLFW.GLFW_KEY_RIGHT_BRACKET,
			"KeyBinding.Category.MISC"
		));

		prevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"KeyBinding.Category.MISC",
			GLFW.GLFW_KEY_LEFT_BRACKET,
			"KeyBinding.Category.MISC"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				toggle(client);
			}
			while (nextKey.wasPressed()) {
				cycle(client, +1);
			}
			while (prevKey.wasPressed()) {
				cycle(client, -1);
			}
		});
	}

	private static void toggle(MinecraftClient client) {
		if (client.player == null || client.world == null) return;

		var currentCamera = client.getCameraEntity();
		if (originalCameraEntity != null && currentCamera != null && currentCamera != originalCameraEntity) {
			client.setCameraEntity(originalCameraEntity);
			originalCameraEntity = null;
			show(client, "Camera: you");
			return;
		}

		originalCameraEntity = client.player;

		Entity target = pickTargetEntity(client);
		if (target == null) {
			originalCameraEntity = null;
			show(client, "No entity found");
			return;
		}

		client.setCameraEntity(target);
		show(client, "Camera: " + target.getName().getString());
	}

	private static void cycle(MinecraftClient client, int dir) {
		if (client.player == null || client.world == null) return;
		if (originalCameraEntity == null) return; // not in entity cam mode

		Entity current = client.getCameraEntity();
		if (current == null) return;

		List<Entity> candidates = getNearbyEntities(client);
		candidates.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));

		if (candidates.isEmpty()) {
			show(client, "No entities nearby");
			return;
		}

		int idx = candidates.indexOf(current);
		int nextIdx;
		if (idx < 0) {
			nextIdx = 0;
		} else {
			nextIdx = Math.floorMod(idx + dir, candidates.size());
		}

		Entity next = candidates.get(nextIdx);
		client.setCameraEntity(next);
		show(client, "Camera: " + next.getName().getString());
	}

	private static Entity pickTargetEntity(MinecraftClient client) {
		HitResult hit = client.crosshairTarget;
		if (hit instanceof EntityHitResult ehr) {
			Entity e = ehr.getEntity();
			if (e != null && e.isAlive() && e != client.player) return e;
		}

		// Fallback: nearest alive entity around the player (within radius)
		List<Entity> candidates = getNearbyEntities(client);
		return candidates.stream()
			.min(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)))
			.orElse(null);
	}

	private static List<Entity> getNearbyEntities(MinecraftClient client) {
		Box box = client.player.getBoundingBox().expand(SEARCH_RADIUS_BLOCKS);
		return client.world.getOtherEntities(client.player, box, e ->
			e != null && e.isAlive() && e.squaredDistanceTo(client.player) <= SEARCH_RADIUS_SQUARED
		);
	}

	private static void show(MinecraftClient client, String msg) {
		if (client.player == null) return;
		client.player.sendMessage(Text.literal("[EntityCam] " + msg), true);
	}
}

