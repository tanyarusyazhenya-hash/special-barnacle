package dev.aurora.module;

public enum Category {
	COMBAT("Combat"),
	RENDER("Visuals"),
	WORLD("World"),
	UTILITY("Utility"),
	HUD("HUD"),
	CLIENT("Client");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
