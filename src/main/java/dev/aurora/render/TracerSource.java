package dev.aurora.render;

import java.util.List;

/** Implemented by modules that can feed targets to Tracers. */
public interface TracerSource {
	void tracerTargets(List<TracerTarget> out);
}
