package fr.leonard.cleaninventory.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Opened with the config key or from Mod Menu. */
public class ConfigScreen extends Screen {
	private static final int WIDGET_WIDTH = 200;
	private static final int WIDGET_HEIGHT = 20;
	private static final int SPACING = 24;

	private final @Nullable Screen parent;

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.translatable("config.cleaninventory.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (width - WIDGET_WIDTH) / 2;
		int y = height / 4;

		StringWidget title = new StringWidget(this.title, font);
		title.setPosition((width - title.getWidth()) / 2, y);
		addRenderableWidget(title);

		addRenderableWidget(CycleButton.builder(SortMode::displayName, SortConfig.get().defaultMode())
			.withValues(SortMode.values())
			.create(x, y + SPACING, WIDGET_WIDTH, WIDGET_HEIGHT, Component.translatable("config.cleaninventory.default_mode"),
				(button, mode) -> SortConfig.get().setDefaultMode(mode)));

		Button reset = Button.builder(Component.translatable("config.cleaninventory.reset"), b -> SortConfig.get().resetChosenModes())
			.bounds(x, y + 2 * SPACING, WIDGET_WIDTH, WIDGET_HEIGHT)
			.build();
		reset.setTooltip(Tooltip.create(Component.translatable("config.cleaninventory.reset.tooltip")));
		addRenderableWidget(reset);

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
			.bounds(x, y + 4 * SPACING, WIDGET_WIDTH, WIDGET_HEIGHT)
			.build());
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
