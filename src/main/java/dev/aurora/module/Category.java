package dev.aurora.module;

public enum Category {
	RENDER("Render"),
	WORLD("World"),
	UTILITY("Utility"),
	HUD("HUD"),
	CLIENT("Client");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
