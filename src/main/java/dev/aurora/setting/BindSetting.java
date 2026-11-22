package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Keyboard bind for a module. key = GLFW key code, -1 = unbound. */
public class BindSetting extends Setting {
	public int key = -1;
	/** GUI state: waiting for the next key press. */
	public boolean listening;

	public BindSetting() {
		super("Bind");
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(key);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			key = element.getAsInt();
		}
	}
}
