package ru.ivanov.entitycam;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class EntityCamSelectScreen extends Screen {
	private static final int SEARCH_RADIUS_BLOCKS = 64;
	private static final int ROWS_PER_PAGE = 12;

	private TextFieldWidget filter;
	private final List<Entity> filteredEntities = new ArrayList<>();
	private final List<ButtonWidget> entityButtons = new ArrayList<>();

	private ButtonWidget statusButton;
	private ButtonWidget prevPageButton;
	private ButtonWidget nextPageButton;

	private int page = 0;

	public EntityCamSelectScreen() {
		super(Text.literal("EntityCam"));
	}

	@Override
	protected void init() {
		int top = 20;

		filter = new TextFieldWidget(textRenderer, 10, top, width - 20, 20, Text.literal("Filter"));
		filter.setChangedListener(ignored -> {
			page = 0;
			refresh();
		});
		addDrawableChild(filter);

		int listTop = top + 28;
		int rowWidth = width - 20;
		int rowHeight = 20;

		statusButton = ButtonWidget.builder(Text.literal("Loading..."), b -> {})
			.dimensions(10, listTop, rowWidth, rowHeight)
			.build();
		statusButton.active = false;
		addDrawableChild(statusButton);

		int y = listTop + rowHeight + 4;
		for (int i = 0; i < ROWS_PER_PAGE; i++) {
			final int slot = i;
			ButtonWidget btn = ButtonWidget.builder(Text.literal(""), b -> onEntityButtonClick(slot))
				.dimensions(10, y, rowWidth, rowHeight)
				.build();
			btn.visible = false;
			btn.active = false;
			addDrawableChild(btn);
			entityButtons.add(btn);
			y += rowHeight + 2;
		}

		prevPageButton = ButtonWidget.builder(Text.literal("< Prev"), b -> {
			if (page > 0) {
				page--;
				applyPageToButtons();
			}
		}).dimensions(10, height - 56, 100, 20).build();
		addDrawableChild(prevPageButton);

		nextPageButton = ButtonWidget.builder(Text.literal("Next >"), b -> {
			if ((page + 1) * ROWS_PER_PAGE < filteredEntities.size()) {
				page++;
				applyPageToButtons();
			}
		}).dimensions(120, height - 56, 100, 20).build();
		addDrawableChild(nextPageButton);

		addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), b -> {
			page = 0;
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
		filteredEntities.clear();

		if (client == null || client.player == null || client.world == null) {
			statusButton.setMessage(Text.literal("World not loaded"));
			applyPageToButtons();
			return;
		}

		String q = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);

		Box box = client.player.getBoundingBox().expand(SEARCH_RADIUS_BLOCKS);
		List<Entity> entities = client.world.getOtherEntities(client.player, box, e -> e != null && e.isAlive());
		entities.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(client.player)));

		for (Entity e : entities) {
			if (matchesFilter(e, q)) filteredEntities.add(e);
		}

		if (page * ROWS_PER_PAGE >= filteredEntities.size()) page = 0;
		applyPageToButtons();
	}

	private boolean matchesFilter(Entity e, String q) {
		if (q.isEmpty()) return true;

		String name = e.getName().getString().toLowerCase(Locale.ROOT);
		if (name.contains(q)) return true;

		String typeStr = e.getType().toString().toLowerCase(Locale.ROOT);
		if (typeStr.contains(q)) return true;

		Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
		if (id != null) {
			String full = id.toString().toLowerCase(Locale.ROOT);
			String path = id.getPath().toLowerCase(Locale.ROOT);
			if (full.contains(q) || path.contains(q)) return true;
		}

		return e.getType().getTranslationKey().toLowerCase(Locale.ROOT).contains(q);
	}

	private void onEntityButtonClick(int slot) {
		int idx = page * ROWS_PER_PAGE + slot;
		if (idx < 0 || idx >= filteredEntities.size()) return;

		Entity e = filteredEntities.get(idx);
		if (client != null && e.isAlive()) {
			client.setCameraEntity(e);
			close();
		}
	}

	private void applyPageToButtons() {
		int total = filteredEntities.size();
		int start = page * ROWS_PER_PAGE;
		int end = Math.min(start + ROWS_PER_PAGE, total);

		if (total == 0) {
			statusButton.setMessage(Text.literal("Entities: 0 (player is not listed)"));
		} else {
			statusButton.setMessage(Text.literal("Entities: " + total + " | Page " + (page + 1)));
		}

		for (int i = 0; i < entityButtons.size(); i++) {
			ButtonWidget btn = entityButtons.get(i);
			int idx = start + i;

			if (idx < end) {
				Entity e = filteredEntities.get(idx);
				double d = (client != null && client.player != null) ? Math.sqrt(e.squaredDistanceTo(client.player)) : 0.0;
				String label = e.getName().getString() + " (" + String.format(Locale.ROOT, "%.1f", d) + "m)";
				btn.setMessage(Text.literal(label));
				btn.visible = true;
				btn.active = true;
			} else {
				btn.visible = false;
				btn.active = false;
			}
		}

		prevPageButton.active = page > 0;
		nextPageButton.active = (page + 1) * ROWS_PER_PAGE < total;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderInGameBackground(context);
		super.render(context, mouseX, mouseY, delta);
	}
}
