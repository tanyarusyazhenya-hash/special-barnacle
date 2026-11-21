package dev.aurora.module;

public enum Category {
	RENDER("Render"),
	CLIENT("Client");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
