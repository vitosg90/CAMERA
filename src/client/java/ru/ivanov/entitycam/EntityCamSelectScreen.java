package ru.ivanov.entitycam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class EntityCamSelectScreen extends Screen {
	private static final int SEARCH_RADIUS_BLOCKS = 64;

	private TextFieldWidget filter;
	private EntityList list;

	public EntityCamSelectScreen() {
		super(Text.literal("EntityCam"));
	}

	@Override
	protected void init() {
		int top = 20;

		filter = new TextFieldWidget(textRenderer, 10, top, width - 20, 20, Text.literal("Filter"));
		filter.setChangedListener(ignored -> refresh());
		addDrawableChild(filter);

		int listTop = top + 24;
		int listBottom = height - 40;
		list = new EntityList(client, width, listBottom - listTop, listTop, listBottom, 22);
		addDrawableChild(list);

		addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> refresh())
			.dimensions(10, height - 30, 90, 20)
			.build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Back to you"), b -> {
			if (client != null && client.player != null) client.setCameraEntity(client.player);
			close();
		}).dimensions(110, height - 30, 110, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
			.dimensions(width - 100, height - 30, 90, 20)
			.build());

		refresh();
	}

	private void refresh() {
		if (client == null || client.player == null || client.world == null) return;

		String q = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);

		Box box = client.player.getBoundingBox().expand(SEARCH_RADIUS_BLOCKS);
		List<Entity> entities = client.world.getOtherEntities(client.player, box, e -> e != null && e.isAlive());
		entities.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));

		List<Entity> filtered = new ArrayList<>(entities.size());
		for (Entity e : entities) {
			String name = e.getName().getString();
			String type = e.getType().toString();
			if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || type.toLowerCase(Locale.ROOT).contains(q)) {
				filtered.add(e);
			}
		}

		list.setEntities(filtered);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFFFFFF);

		Text hint = Text.literal("Click an entry to switch camera. Radius: " + SEARCH_RADIUS_BLOCKS + " blocks")
			.formatted(Formatting.GRAY);
		context.drawTextWithShadow(textRenderer, hint, 10, 44, 0xFFFFFF);
	}

	private final class EntityList extends ElementListWidget<EntityEntry> {
		private EntityList(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
			super(client, width, height, top, bottom, itemHeight);
		}

		void setEntities(List<Entity> entities) {
			clearEntries();
			for (Entity e : entities) addEntry(new EntityEntry(e));
		}
	}

	private final class EntityEntry extends ElementListWidget.Entry<EntityEntry> {
		private final Entity entity;

		private EntityEntry(Entity entity) {
			this.entity = entity;
		}

		@Override
		public List<? extends Element> children() {
			return List.of();
		}

		@Override
		public List<? extends Selectable> selectableChildren() {
			return List.of();
		}

		@Override
		public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
			if (client == null || client.player == null) return;
			double d = Math.sqrt(entity.squaredDistanceTo(client.player));
			String label = entity.getName().getString() + "  (" + String.format(Locale.ROOT, "%.1f", d) + "m)";
			context.drawTextWithShadow(textRenderer, Text.literal(label), x + 6, y + 6, hovered ? 0xFFFFAA : 0xFFFFFF);
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			if (button != 0) return false;
			if (client == null) return false;

			if (entity.isAlive()) {
				client.setCameraEntity(entity);
				close();
				return true;
			}
			return false;
		}
	}
}
