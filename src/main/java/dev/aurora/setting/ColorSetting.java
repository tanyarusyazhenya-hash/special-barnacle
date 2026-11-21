package dev.aurora.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.aurora.util.ColorUtil;

public class ColorSetting extends Setting {
	public float hue, sat, bri;
	/** GUI state: picker open + animation. */
	public boolean open;
	public float anim;

	public ColorSetting(String name, int rgb) {
		super(name);
		float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
		hue = hsb[0];
		sat = hsb[1];
		bri = hsb[2];
	}

	public int rgb() {
		return ColorUtil.hsb(hue, sat, bri);
	}

	@Override
	public JsonElement save() {
		JsonArray a = new JsonArray();
		a.add(new JsonPrimitive(hue));
		a.add(new JsonPrimitive(sat));
		a.add(new JsonPrimitive(bri));
		return a;
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonArray() && element.getAsJsonArray().size() == 3) {
			JsonArray a = element.getAsJsonArray();
			hue = a.get(0).getAsFloat();
			sat = a.get(1).getAsFloat();
			bri = a.get(2).getAsFloat();
		}
	}
}
