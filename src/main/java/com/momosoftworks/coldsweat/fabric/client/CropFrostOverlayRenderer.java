package com.momosoftworks.coldsweat.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M10.2d-r3a: proof of model-quad-aligned, alpha-masked frost on vanilla wheat.
 *
 * The original wheat model provides actual 3D planes/UVs. The vanilla wheat
 * sprite's pixel transparency determines WHERE each white frost fleck may go.
 * No guessed plant silhouettes, no soil-wide crossed planes, no shader hacks.
 * Other crops deliberately get no overlay until this proof is accepted.
 *
 * Only client display logic changes. Server growth, frost stress, network
 * tiers, greenhouse physics and SNOWFLAKE particles are untouched.
 */
public final class CropFrostOverlayRenderer
{
    private static final Identifier ATLAS = ColdSweatFabric.id("textures/misc/crop_frost_overlay.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucent(ATLAS);
    private static final int PACKED_LIGHT = 0x00D000D0;
    private static final int MAX_CROPS = 96;
    private static final double MAX_DISTANCE_SQR = 24.0 * 24.0;
    private static final int MAX_PIXELS_PER_FACE = 128;
    private static final float SURFACE_OFFSET = 0.0035F;

    /** 8 growth stages x 3 tiers, rebuilt on atlas reload. */
    private static final Map<Integer, List<IcePixel>> MODEL_CACHE = new HashMap<>();
    private static final TextureAtlasSprite[] KNOWN_SPRITES = new TextureAtlasSprite[8];
    private static List<FrostDraw> frame = List.of();

    private CropFrostOverlayRenderer() { }

    public static void initialize()
    {
        LevelExtractionEvents.END_EXTRACTION.register(CropFrostOverlayRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(CropFrostOverlayRenderer::collectSubmits);
    }

    private static void extract(net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext context)
    {
        ClientLevel level = context.level();
        Vec3 camera = context.camera().position();
        List<FrostDraw> result = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();

        for (Map.Entry<Long, Byte> entry : CropFrostClientState.tiers().entrySet())
        {
            if (result.size() >= MAX_CROPS) break;
            byte tier = entry.getValue();
            if (tier < 1 || tier > 3) continue;

            BlockPos pos = BlockPos.of(entry.getKey());
            if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > MAX_DISTANCE_SQR
                    || !level.hasChunkAt(pos)) continue;

            BlockState state = level.getBlockState(pos);
            // M10.2d-r3a intentionally proves wheat only. No other guessed shapes.
            if (!state.is(Blocks.WHEAT)) continue;
            int age = state.getValue(BlockStateProperties.AGE_7);
            TextureAtlasSprite sprite = minecraft.getAtlasManager().get(
                    Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath(
                            "minecraft", "wheat_stage" + age)));
            if (sprite == null || sprite.contents().width() <= 0 || sprite.contents().height() <= 0)
            {
                continue;
            }

            if (KNOWN_SPRITES[age] != sprite)
            {
                KNOWN_SPRITES[age] = sprite;
                for (int t = 1; t <= 3; t++) MODEL_CACHE.remove(age * 4 + t);
            }

            int key = age * 4 + tier;
            List<IcePixel> pixels = MODEL_CACHE.get(key);
            if (pixels == null)
            {
                pixels = buildForWheat(minecraft, state, sprite, age, tier);
                MODEL_CACHE.put(key, pixels);
            }
            if (!pixels.isEmpty()) result.add(new FrostDraw(pos.getX(), pos.getY(), pos.getZ(), pixels));
        }
        frame = List.copyOf(result);
    }

    private static List<IcePixel> buildForWheat(
            Minecraft minecraft, BlockState state,
            TextureAtlasSprite sprite, int age, int tier)
    {
        BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);

        MutableMesh mesh = Renderer.get().mutableMesh();
        QuadEmitter emitter = mesh.emitter();
        for (BlockStateModelPart part : parts)
        {
            for (var quad : part.getQuads(null))
            {
                emitter.fromBakedQuad(quad).emit();
            }
        }
        List<IcePixel> result = new ArrayList<>();
        mesh.forEach(q ->
        {
            Vector3f p0 = q.copyPos(0, null);
            Vector3f p1 = q.copyPos(1, null);
            Vector3f p3 = q.copyPos(3, null);
            Vector2f uv0 = q.copyUv(0, null);
            Vector2f uv1 = q.copyUv(1, null);
            Vector2f uv3 = q.copyUv(3, null);

            float eU1 = uv1.x - uv0.x, eV1 = uv1.y - uv0.y;
            float eU3 = uv3.x - uv0.x, eV3 = uv3.y - uv0.y;
            float det = eU1 * eV3 - eV1 * eU3;
            if (Math.abs(det) < 1.0e-10F) return;

            Vector3f alongA = new Vector3f(p1).sub(p0);
            Vector3f alongB = new Vector3f(p3).sub(p0);
            Vector3f normal = new Vector3f(alongA).cross(alongB);
            if (normal.lengthSquared() < 1.0e-8F) return;
            normal.normalize();

            int facePixels = 0;
            int w = Math.min(32, sprite.contents().width());
            int h = Math.min(32, sprite.contents().height());
            for (int py = 0; py < h && facePixels < MAX_PIXELS_PER_FACE; py++)
            {
                for (int px = 0; px < w && facePixels < MAX_PIXELS_PER_FACE; px++)
                {
                    // Actual original sprite alpha: never frost an empty pixel.
                    if (sprite.contents().isTransparent(0, px, py)) continue;
                    int hash = Math.floorMod(px * 71 + py * 43 + age * 17 + 29, 101);
                    int density = switch (tier) {
                        case 1 -> 34;
                        case 2 -> 58;
                        default -> 78;
                    };
                    // Exposed upper leaf pixels get the earliest accumulation.
                    if (hash >= density + (py < h / 2 ? 10 : -6)) continue;

                    float su0 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (px + 0.08F) / w;
                    float sv0 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (py + 0.08F) / h;
                    float su1 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (px + 0.92F) / w;
                    float sv1 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (py + 0.92F) / h;

                    Vector3f a = project(p0, alongA, alongB, normal, uv0, eU1, eV1, eU3, eV3, det, su0, sv0);
                    Vector3f b = project(p0, alongA, alongB, normal, uv0, eU1, eV1, eU3, eV3, det, su1, sv0);
                    Vector3f c = project(p0, alongA, alongB, normal, uv0, eU1, eV1, eU3, eV3, det, su1, sv1);
                    Vector3f d = project(p0, alongA, alongB, normal, uv0, eU1, eV1, eU3, eV3, det, su0, sv1);
                    if (a == null || b == null || c == null || d == null) continue;
                    result.add(new IcePixel(a, b, c, d, new Vector3f(normal)));
                    facePixels++;
                }
            }
        });
        return List.copyOf(result);
    }

    private static Vector3f project(
            Vector3f p0, Vector3f alongA, Vector3f alongB, Vector3f normal,
            Vector2f uv0, float eU1, float eV1, float eU3, float eV3,
            float det, float u, float v)
    {
        float du = u - uv0.x, dv = v - uv0.y;
        float a = (du * eV3 - dv * eU3) / det;
        float b = (dv * eU1 - du * eV1) / det;
        if (a < -0.01F || a > 1.01F || b < -0.01F || b > 1.01F) return null;
        return new Vector3f(p0).fma(a, alongA).fma(b, alongB)
                .fma(SURFACE_OFFSET, normal);
    }

    private static void collectSubmits(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context)
    {
        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poses = context.poseStack();
        for (FrostDraw frost : frame)
        {
            poses.pushPose();
            poses.translate(frost.x() - camera.x, frost.y() - camera.y, frost.z() - camera.z);
            context.submitNodeCollector().submitCustomGeometry(poses, RENDER_TYPE,
                    (pose, vertices) -> drawPixels(pose.pose(), vertices, frost.pixels()));
            poses.popPose();
        }
    }

    private static void drawPixels(Matrix4fc pose, VertexConsumer vertices, List<IcePixel> pixels)
    {
        for (IcePixel p : pixels)
        {
            // Four tiny triangles over the EXISTING crop quad's visible pixels.
            quad(pose, vertices, p.a(), p.b(), p.c(), p.d(), p.normal());
        }
    }

    private static void quad(Matrix4fc m, VertexConsumer v,
                             Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f n)
    {
        vertex(m, v, a, 0.2F, 0.2F, n);
        vertex(m, v, b, 0.8F, 0.2F, n);
        vertex(m, v, c, 0.8F, 0.8F, n);
        vertex(m, v, d, 0.2F, 0.8F, n);
        Vector3f back = new Vector3f(n).negate();
        vertex(m, v, d, 0.2F, 0.8F, back);
        vertex(m, v, c, 0.8F, 0.8F, back);
        vertex(m, v, b, 0.8F, 0.2F, back);
        vertex(m, v, a, 0.2F, 0.2F, back);
    }

    private static void vertex(Matrix4fc m, VertexConsumer out, Vector3f xyz,
                               float u, float v, Vector3f normal)
    {
        out.addVertex(m, xyz.x, xyz.y, xyz.z)
                .setColor(1.0F, 1.0F, 1.0F, 0.98F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(PACKED_LIGHT)
                .setNormal(normal.x, normal.y, normal.z);
    }

    private record IcePixel(Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f normal) { }
    private record FrostDraw(int x, int y, int z, List<IcePixel> pixels) { }
}
