package dev.aurora.module.impl;

/**
 * Ore X-ray as an overlay: outlines ores through walls. It cannot make stone transparent (that needs
 * changes to the game's block renderer, which this client avoids), but it shows the same information.
 * Built on BlockFinder with more ores on and a bigger range. On servers with anti-xray, buried ores
 * are hidden or faked by the server; set "Anti-Xray Filter" to "Exposed Only" to see only ores that
 * touch air (those are real).
 */
public class Xray extends BlockFinder {
	public Xray() {
		super("Xray", "Ores through walls (overlay)");
		diamond.value = true;
		debris.value = true;
		emerald.value = true;
		gold.value = true;
		iron.value = true;
		lapis.value = true;
		redstone.value = true;
		coal.value = true;
		copper.value = true;
		chunkRadius.set(7);
		style.range.set(128);
		maxBlocks.set(800);
	}
}
