package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting {
	public boolean value;
	/** GUI animation state (0..1). */
	public float anim;

	public BoolSetting(String name, boolean def) {
		super(name);
		this.value = def;
		this.anim = def ? 1f : 0f;
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			value = element.getAsBoolean();
			anim = value ? 1f : 0f;
		}
	}
}
