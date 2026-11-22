package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ModeSetting extends Setting {
	public final String[] modes;
	private int index;

	public ModeSetting(String name, String def, String... modes) {
		super(name);
		this.modes = modes;
		for (int i = 0; i < modes.length; i++) {
			if (modes[i].equals(def)) {
				index = i;
			}
		}
	}

	public String get() {
		return modes[index];
	}

	public int index() {
		return index;
	}

	public boolean is(String mode) {
		return modes[index].equals(mode);
	}

	public void set(int i) {
		if (i >= 0 && i < modes.length) {
			index = i;
		}
	}

	public void next() {
		index = (index + 1) % modes.length;
	}

	public void prev() {
		index = (index - 1 + modes.length) % modes.length;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(get());
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			String s = element.getAsString();
			for (int i = 0; i < modes.length; i++) {
				if (modes[i].equals(s)) {
					index = i;
				}
			}
		}
	}
}
