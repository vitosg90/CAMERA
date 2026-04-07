package ru.ivanov.entitycam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class EntityCamSelectScreen extends Screen {
	private static final int SEARCH_RADIUS_BLOCKS = 64;
	private static final int ROW_HEIGHT = 14;
	private static final int MAX_ROWS = 20;

	private TextFieldWidget filter;
	private List<Entity> currentEntities = List.of();

	// для простого скролла
	private int scrollOffset = 0;

	public EntityCamSelectScreen() {
		super(Text.literal("EntityCam"));
	}

	@Override
	protected void init() {
		int top = 20;

		filter = new TextFieldWidget(textRenderer, 10, top, width - 20, 20, Text.literal("Filter"));
		filter.setChangedListener(ignored -> {
			scrollOffset = 0;
			refresh();
		});
		addDrawableChild(filter);

		addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> {
			scrollOffset = 0;
			refresh();
		}).dimensions(10, height - 30, 90, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Back to you"), b -> {
			if (client != null && client.player != null) client.setCameraEntity(client.player);
			close();
		}).dimensions(110, height - 30, 110, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
			.dimensions(width - 100, height - 30, 90, 20).build());

		refresh();
	}

	private void refresh() {
		if (client == null || client.player == null || client.world == null) {
			currentEntities = List.of();
			return;
		}

		String q = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);

		Box box = client.player.getBoundingBox().expand(SEARCH_RADIUS_BLOCKS);
		List<Entity> entities = client.world.getOtherEntities(client.player, box, e -> e != null && e.isAlive());
		entities.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));

		List<Entity> filtered = new ArrayList<>(entities.size());
		for (Entity e : entities) {
			if (entityMatchesFilter(e, q)) filtered.add(e);
		}

		currentEntities = filtered;
	}

	private static boolean entityMatchesFilter(Entity e, String q) {
		if (q.isEmpty()) return true;

		String name = e.getName().getString().toLowerCase(Locale.ROOT);
		if (name.contains(q)) return true;

		String typeStr = e.getType().toString().toLowerCase(Locale.ROOT);
		if (typeStr.contains(q)) return true;

		Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
		if (id != null) {
			String idFull = id.toString().toLowerCase(Locale.ROOT);
			String path = id.getPath().toLowerCase(Locale.ROOT);
			if (idFull.contains(q) || path.contains(q)) return true;
		}

		String transKey = e.getType().getTranslationKey().toLowerCase(Locale.ROOT);
		return transKey.contains(q);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (currentEntities.isEmpty()) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);

		int totalRows = currentEntities.size();
		int visible = Math.min(MAX_ROWS, totalRows);
		int maxOffset = Math.max(0, totalRows - visible);

		if (vertical < 0 && scrollOffset < maxOffset) {
			scrollOffset++;
			return true;
		}
		if (vertical > 0 && scrollOffset > 0) {
			scrollOffset--;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && client != null && client.player != null) {
			int listLeft = 10;
			int listRight = width - 10;
			int listTop = 20 + 24 + 28;

			if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop) {
				int indexInView = (int) ((mouseY - listTop) / ROW_HEIGHT);
				if (indexInView >= 0 && indexInView < MAX_ROWS) {
					int absoluteIndex = scrollOffset + indexInView;
					if (absoluteIndex >= 0 && absoluteIndex < currentEntities.size()) {
						Entity e = currentEntities.get(absoluteIndex);
						if (e.isAlive()) {
							client.setCameraEntity(e);
							close();
							return true;
						}
					}
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderInGameBackground(context);
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFFFFFF);

		if (client != null && client.player != null) {
			Text hint = Text.literal("Click a row to switch camera. Radius: " + SEARCH_RADIUS_BLOCKS + " blocks")
				.formatted(Formatting.GRAY);
			context.drawTextWithShadow(textRenderer, hint, 10, 44, 0xFFFFFF);

			Text countLine = currentEntities.isEmpty()
				? Text.literal("No other entities in range (player is not listed).").formatted(Formatting.YELLOW)
				: Text.literal("Entities: " + currentEntities.size()).formatted(Formatting.GRAY);
			context.drawTextWithShadow(textRenderer, countLine, 10, 56, 0xFFFFFF);
		}

		drawEntityList(context, mouseX, mouseY);
	}

	private void drawEntityList(DrawContext context, int mouseX, int mouseY) {
		int listLeft = 10;
		int listRight = width - 10;
		int listTop = 20 + 24 + 28;
		int listBottom = listTop + ROW_HEIGHT * MAX_ROWS;

		// рамка
		int borderColor = 0xFFFFFFFF;
		context.fill(listLeft, listTop, listRight, listBottom, 0x80000000);
		context.drawBorder(listLeft, listTop, listRight - listLeft, listBottom - listTop, borderColor);

		if (currentEntities.isEmpty()) return;

		int totalRows = currentEntities.size();
		int visible = Math.min(MAX_ROWS, totalRows);

		for (int i = 0; i < visible; i++) {
			int entityIndex = scrollOffset + i;
			if (entityIndex >= totalRows) break;

			Entity e = currentEntities.get(entityIndex);

			int rowY = listTop + i * ROW_HEIGHT;
			boolean hovered = mouseX >= listLeft && mouseX <= listRight && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

			double d = client != null && client.player != null
				? Math.sqrt(e.squaredDistanceTo(client.player))
				: 0.0;

			String label = e.getName().getString() + "  (" + String.format(Locale.ROOT, "%.1f", d) + "m)";
			int color = hovered ? 0xFFFFAA : 0xFFFFFF;

			context.drawTextWithShadow(textRenderer, label, listLeft + 4, rowY + 2, color);
		}
	}
}
