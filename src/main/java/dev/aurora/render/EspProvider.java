package dev.aurora.render;

import java.util.List;

public interface EspProvider {
	/** Called once per frame; add the boxes to draw. */
	void collect(List<EspBox> out);
}
