package ru.ivanov.entitycam;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
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

	// Стабильный id для клиентского "дубля" игрока
	private static final int BODY_PROXY_ID = -13371337;

	private static KeyBinding toggleKey;
	private static KeyBinding nextKey;
	private static KeyBinding prevKey;
	private static KeyBinding openMenuKey;

	private static Entity originalCameraEntity;
	private static boolean entityCamActive;
	private static OtherClientPlayerEntity bodyProxy;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.entitycam.toggle",
			GLFW.GLFW_KEY_V,
			KeyBinding.Category.MISC
		));

		nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.entitycam.next",
			GLFW.GLFW_KEY_RIGHT_BRACKET,
			KeyBinding.Category.MISC
		));

		prevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.entitycam.prev",
			GLFW.GLFW_KEY_LEFT_BRACKET,
			KeyBinding.Category.MISC
		));

		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.entitycam.menu",
			GLFW.GLFW_KEY_B,
			KeyBinding.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) toggle(client);
			while (nextKey.wasPressed()) cycle(client, +1);
			while (prevKey.wasPressed()) cycle(client, -1);
			while (openMenuKey.wasPressed()) openMenu(client);

			// Поддерживаем/удаляем "дубля" каждый тик.
			tickBodyProxy(client);
		});
	}

	private static void openMenu(MinecraftClient client) {
		if (client.player == null || client.world == null) return;
		client.setScreen(new EntityCamSelectScreen());
	}

	private static void toggle(MinecraftClient client) {
		if (client.player == null || client.world == null) return;

		Entity currentCamera = client.getCameraEntity();
		if (entityCamActive && currentCamera != null && currentCamera != client.player) {
			switchBackToPlayer(client);
			return;
		}

		Entity target = pickTargetEntity(client);
		if (target == null) {
			show(client, "No entity found");
			return;
		}

		switchCameraToEntity(client, target);
	}

	private static void cycle(MinecraftClient client, int dir) {
		if (client.player == null || client.world == null) return;
		if (!entityCamActive) return;

		Entity current = client.getCameraEntity();
		if (current == null) return;

		List<Entity> candidates = getNearbyEntities(client);
		candidates.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));

		if (candidates.isEmpty()) {
			show(client, "No entities nearby");
			return;
		}

		int idx = candidates.indexOf(current);
		int nextIdx = (idx < 0) ? 0 : Math.floorMod(idx + dir, candidates.size());

		Entity next = candidates.get(nextIdx);
		switchCameraToEntity(client, next);
	}

	public static void switchCameraToEntity(MinecraftClient client, Entity target) {
		if (client.player == null || client.world == null || target == null || !target.isAlive()) return;

		if (originalCameraEntity == null) originalCameraEntity = client.player;

		entityCamActive = true;
		client.setCameraEntity(target);
		ensureBodyProxy(client);
		syncBodyProxy(client);

		show(client, "Camera: " + target.getName().getString());
	}

	public static void switchBackToPlayer(MinecraftClient client) {
		if (client.player == null) return;

		client.setCameraEntity(client.player);
		entityCamActive = false;
		originalCameraEntity = null;
		removeBodyProxy(client);

		show(client, "Camera: you");
	}

	private static Entity pickTargetEntity(MinecraftClient client) {
		HitResult hit = client.crosshairTarget;
		if (hit instanceof EntityHitResult ehr) {
			Entity e = ehr.getEntity();
			if (e != null && e.isAlive() && e != client.player) return e;
		}

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

	private static void tickBodyProxy(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			removeBodyProxy(client);
			entityCamActive = false;
			originalCameraEntity = null;
			return;
		}

		if (!entityCamActive) {
			removeBodyProxy(client);
			return;
		}

		Entity cam = client.getCameraEntity();
		// Если камера по какой-то причине вернулась на игрока — выключаем режим корректно.
		if (cam == null || cam == client.player) {
			switchBackToPlayer(client);
			return;
		}

		ensureBodyProxy(client);
		syncBodyProxy(client);
	}

	private static void ensureBodyProxy(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null) return;

		if (bodyProxy != null && bodyProxy.getEntityWorld() == world) return;

		removeBodyProxy(client);

		bodyProxy = new OtherClientPlayerEntity(world, client.player.getGameProfile());
		bodyProxy.setId(BODY_PROXY_ID);

		syncBodyProxy(client);
		world.addEntity(bodyProxy);
	}

	private static void syncBodyProxy(MinecraftClient client) {
		if (client.player == null || bodyProxy == null) return;

		bodyProxy.copyPositionAndRotation(client.player);
		bodyProxy.setYaw(client.player.getYaw());
		bodyProxy.setPitch(client.player.getPitch());
		bodyProxy.setHeadYaw(client.player.getHeadYaw());
		bodyProxy.bodyYaw = client.player.bodyYaw;
		bodyProxy.setPose(client.player.getPose());
		bodyProxy.setSneaking(client.player.isSneaking());
		bodyProxy.setSprinting(client.player.isSprinting());
		bodyProxy.setOnGround(client.player.isOnGround());
	}

	private static void removeBodyProxy(MinecraftClient client) {
		if (client.world != null) {
			client.world.removeEntity(BODY_PROXY_ID, Entity.RemovalReason.DISCARDED);
		}
		bodyProxy = null;
	}

	private static void show(MinecraftClient client, String msg) {
		if (client.player == null) return;
		client.player.sendMessage(Text.literal("[EntityCam] " + msg), true);
	}
}
