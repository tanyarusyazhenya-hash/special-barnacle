package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/** A button in the GUI that runs something when clicked. */
public class ActionSetting extends Setting {
	private final Runnable action;

	public ActionSetting(String name, Runnable action) {
		super(name);
		this.action = action;
	}

	public void run() {
		action.run();
	}

	@Override
	public boolean persistent() {
		return false;
	}

	@Override
	public JsonElement save() {
		return JsonNull.INSTANCE;
	}

	@Override
	public void load(JsonElement element) {}
}
