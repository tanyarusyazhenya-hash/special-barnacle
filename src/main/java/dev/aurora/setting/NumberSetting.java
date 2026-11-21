package dev.aurora.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public class NumberSetting extends Setting {
	public final double min, max, step;
	public double value;

	public NumberSetting(String name, double def, double min, double max, double step) {
		super(name);
		this.min = min;
		this.max = max;
		this.step = step;
		set(def);
	}

	public void set(double v) {
		v = Math.max(min, Math.min(max, v));
		if (step > 0) {
			v = Math.round((v - min) / step) * step + min;
		}
		value = Math.max(min, Math.min(max, v));
	}

	public float f() {
		return (float) value;
	}

	public int i() {
		return (int) Math.round(value);
	}

	public String display() {
		return step >= 1 ? Integer.toString(i()) : String.format(Locale.ROOT, "%.2f", value);
	}

	@Override
	public JsonElement save() {
		return new JsonPrimitive(value);
	}

	@Override
	public void load(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsDouble());
		}
	}
}
