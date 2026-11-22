package dev.aurora.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.aurora.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws ESP boxes with the custom render pipeline approach from the Fabric 1.21.11 docs
 * ("Rendering in the World"). Outlines are built from thin filled quads, so thickness is
 * a real world-space value and no line-width uniforms are needed.
 *
 * This is the most version-sensitive file in the project. If a render API name differs in
 * your exact 1.21.11 build, this is the file to adjust.
 */
public final class EspRenderer {
	private static final RenderPipeline PIPE_DEPTH = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/esp_depth"))
			.build());

	private static final RenderPipeline PIPE_WALLS = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/esp_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(1 << 18);
	private static MappableRingBuffer ring;

	private EspRenderer() {}

	public static void render(PoseStack stack, Vec3 cam, List<EspBox> boxes, List<EspLine> lines) {
		// Vertices are written camera-relative (computed in double, then cast) so there is no
		// float jitter far from the origin. The pose already carries the camera rotation.
		Matrix4f m = stack.last().pose();
		drawPass(PIPE_DEPTH, m, cam, boxes, lines, false);
		drawPass(PIPE_WALLS, m, cam, boxes, lines, true);
	}

	public static void close() {
		if (ring != null) {
			ring.close();
			ring = null;
		}
		ALLOCATOR.close();
	}

	private static void drawPass(RenderPipeline pipeline, Matrix4f m, Vec3 cam, List<EspBox> boxes, List<EspLine> lines,
								boolean walls) {
		BufferBuilder b = null;
		for (EspBox e : boxes) {
			if (e.throughWalls() != walls) {
				continue;
			}
			if (b == null) {
				b = new BufferBuilder(ALLOCATOR, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
			}
			emit(b, m, cam, e);
		}
		for (EspLine l : lines) {
			if (l.throughWalls() != walls) {
				continue;
			}
			if (b == null) {
				b = new BufferBuilder(ALLOCATOR, pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
			}
			ribbon(b, m, cam, l);
		}
		if (b == null) {
			return;
		}
		MeshData mesh = b.build();
		if (mesh == null) {
			return;
		}
		draw(pipeline, mesh);
	}

	/** A camera-facing strip between two points, with constant on-screen width. Drawn twice (both windings). */
	private static void ribbon(BufferBuilder b, Matrix4f m, Vec3 cam, EspLine l) {
		Vec3 a = l.from().subtract(cam);
		Vec3 c = l.to().subtract(cam);
		Vec3 dir = c.subtract(a);
		if (dir.lengthSqr() < 1e-6) {
			return;
		}
		Vec3 mid = a.add(c).scale(0.5);
		Vec3 side = dir.cross(mid);
		if (side.lengthSqr() < 1e-8) {
			side = dir.cross(new Vec3(0, 1, 0));
		}
		if (side.lengthSqr() < 1e-8) {
			side = dir.cross(new Vec3(1, 0, 0));
		}
		side = side.normalize();

		// about 0.0013 world units per pixel per unit of distance at a normal FOV
		double k = l.thickness() * 0.5 * 0.0013;
		double wa = Math.max(0.002, a.length() * k);
		double wc = Math.max(0.002, c.length() * k);
		int col = ColorUtil.argb(l.rgb(), l.alpha());

		float ax1 = (float) (a.x + side.x * wa), ay1 = (float) (a.y + side.y * wa), az1 = (float) (a.z + side.z * wa);
		float ax2 = (float) (a.x - side.x * wa), ay2 = (float) (a.y - side.y * wa), az2 = (float) (a.z - side.z * wa);
		float cx1 = (float) (c.x + side.x * wc), cy1 = (float) (c.y + side.y * wc), cz1 = (float) (c.z + side.z * wc);
		float cx2 = (float) (c.x - side.x * wc), cy2 = (float) (c.y - side.y * wc), cz2 = (float) (c.z - side.z * wc);

		v(b, m, ax1, ay1, az1, col); v(b, m, ax2, ay2, az2, col); v(b, m, cx2, cy2, cz2, col); v(b, m, cx1, cy1, cz1, col);
		v(b, m, cx1, cy1, cz1, col); v(b, m, cx2, cy2, cz2, col); v(b, m, ax2, ay2, az2, col); v(b, m, ax1, ay1, az1, col);
	}

	private static void emit(BufferBuilder b, Matrix4f m, Vec3 cam, EspBox e) {
		AABB a = e.aabb();
		float x1 = (float) (a.minX - cam.x), y1 = (float) (a.minY - cam.y), z1 = (float) (a.minZ - cam.z);
		float x2 = (float) (a.maxX - cam.x), y2 = (float) (a.maxY - cam.y), z2 = (float) (a.maxZ - cam.z);
		int mode = e.mode();

		boolean fill = (mode == 1 || mode == 2) && e.fillAlpha() > 0f;
		boolean lines = (mode == 0 || mode == 2 || mode == 3) && e.outlineAlpha() > 0f;

		if (fill) {
			box(b, m, x1, y1, z1, x2, y2, z2, ColorUtil.argb(e.rgb(), e.fillAlpha()));
		}
		if (lines) {
			edges(b, m, x1, y1, z1, x2, y2, z2, e.thickness() / 2f, mode == 3 ? 0.28f : 1f,
				ColorUtil.argb(e.rgb(), e.outlineAlpha()));
		}
	}

	/** The 12 edges as thin boxes. frac < 1 draws only that fraction at each end (Corners mode). */
	private static void edges(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
							  float h, float frac, int c) {
		float[] xs = {x1, x2};
		float[] ys = {y1, y2};
		float[] zs = {z1, z2};

		for (float y : ys) {
			for (float z : zs) { // along X
				if (frac >= 1f) {
					box(b, m, x1 - h, y - h, z - h, x2 + h, y + h, z + h, c);
				} else {
					float l = (x2 - x1) * frac;
					box(b, m, x1 - h, y - h, z - h, x1 + l, y + h, z + h, c);
					box(b, m, x2 - l, y - h, z - h, x2 + h, y + h, z + h, c);
				}
			}
		}
		for (float x : xs) {
			for (float z : zs) { // along Y
				if (frac >= 1f) {
					box(b, m, x - h, y1 - h, z - h, x + h, y2 + h, z + h, c);
				} else {
					float l = (y2 - y1) * frac;
					box(b, m, x - h, y1 - h, z - h, x + h, y1 + l, z + h, c);
					box(b, m, x - h, y2 - l, z - h, x + h, y2 + h, z + h, c);
				}
			}
		}
		for (float x : xs) {
			for (float y : ys) { // along Z
				if (frac >= 1f) {
					box(b, m, x - h, y - h, z1 - h, x + h, y + h, z2 + h, c);
				} else {
					float l = (z2 - z1) * frac;
					box(b, m, x - h, y - h, z1 - h, x + h, y + h, z1 + l, c);
					box(b, m, x - h, y - h, z2 - l, x + h, y + h, z2 + h, c);
				}
			}
		}
	}

	private static void box(BufferBuilder b, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
		// -Y
		v(b, m, x1, y1, z1, c); v(b, m, x2, y1, z1, c); v(b, m, x2, y1, z2, c); v(b, m, x1, y1, z2, c);
		// +Y
		v(b, m, x1, y2, z1, c); v(b, m, x1, y2, z2, c); v(b, m, x2, y2, z2, c); v(b, m, x2, y2, z1, c);
		// -Z
		v(b, m, x1, y1, z1, c); v(b, m, x1, y2, z1, c); v(b, m, x2, y2, z1, c); v(b, m, x2, y1, z1, c);
		// +Z
		v(b, m, x1, y1, z2, c); v(b, m, x2, y1, z2, c); v(b, m, x2, y2, z2, c); v(b, m, x1, y2, z2, c);
		// -X
		v(b, m, x1, y1, z1, c); v(b, m, x1, y1, z2, c); v(b, m, x1, y2, z2, c); v(b, m, x1, y2, z1, c);
		// +X
		v(b, m, x2, y1, z1, c); v(b, m, x2, y2, z1, c); v(b, m, x2, y2, z2, c); v(b, m, x2, y1, z2, c);
	}

	private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z, int argb) {
		b.addVertex(m, x, y, z).setColor(argb);
	}

	private static void draw(RenderPipeline pipeline, MeshData mesh) {
		MeshData.DrawState ds = mesh.drawState();
		VertexFormat format = ds.format();

		int size = ds.vertexCount() * format.getVertexSize();
		if (ring == null || ring.size() < size) {
			if (ring != null) {
				ring.close();
			}
			ring = new MappableRingBuffer(() -> "aurora esp", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(
			ring.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = ring.currentBuffer();

		RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer indices = sequential.getBuffer(ds.indexCount());
		VertexFormat.IndexType indexType = sequential.type();

		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
			RenderSystem.getModelViewMatrix(), new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());

		RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
			() -> "aurora esp", target.getColorTextureView(), OptionalInt.empty(),
			target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", dynamicTransforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, ds.indexCount(), 1);
		}

		ring.rotate();
		mesh.close();
	}
}
