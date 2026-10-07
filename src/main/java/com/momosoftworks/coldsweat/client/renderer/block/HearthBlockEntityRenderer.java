package com.momosoftworks.coldsweat.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.momosoftworks.coldsweat.common.block.HearthBottomBlock;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import com.momosoftworks.coldsweat.fabric.ColdSweatFabric;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fabric 26.2 renderer for the Hearth.
 *
 * The upstream Hearth artwork is a 64x64 entity-model skin, not a normal
 * cube-face texture. Rendering it through cube_all stretched unrelated UV
 * regions across each face. This renderer restores the intended body + grate
 * model and its hot/cold/smart overlay layers using Minecraft 26.2's
 * render-state/submission pipeline.
 */
public final class HearthBlockEntityRenderer
        implements BlockEntityRenderer<
                HearthBlockEntity,
                HearthBlockEntityRenderer.HearthRenderState
        >
{
    private static final Identifier TEXTURE =
            ColdSweatFabric.id("textures/block/hearth.png");
    private static final Identifier TEXTURE_SMART =
            ColdSweatFabric.id("textures/block/hearth_smart.png");
    private static final Identifier TEXTURE_HEAT_ON =
            ColdSweatFabric.id("textures/block/hearth_heat_on.png");
    private static final Identifier TEXTURE_COLD_ON =
            ColdSweatFabric.id("textures/block/hearth_cold_on.png");
    private static final Identifier TEXTURE_FROST =
            ColdSweatFabric.id("textures/block/hearth_frost.png");
    private static final Identifier TEXTURE_LIT =
            ColdSweatFabric.id("textures/block/hearth_lit.png");

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(
                    ColdSweatFabric.id("hearth"),
                    "main"
            );

    private final ModelPart body;
    private final ModelPart grate;

    public HearthBlockEntityRenderer(
            BlockEntityRendererProvider.Context context
    )
    {
        ModelPart root =
                context.bakeLayer(LAYER_LOCATION);

        body = root.getChild("body");
        grate = root.getChild("grate");
    }

    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -16.0F,
                                -16.0F,
                                0.0F,
                                16.0F,
                                16.0F,
                                16.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offset(
                        8.0F,
                        24.0F,
                        -8.0F
                )
        );

        root.addOrReplaceChild(
                "grate",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(
                                -5.0F,
                                -9.0F,
                                -9.0F,
                                10.0F,
                                7.0F,
                                1.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offset(
                        0.0F,
                        24.0F,
                        0.0F
                )
        );

        return LayerDefinition.create(
                mesh,
                64,
                64
        );
    }

    @Override
    public HearthRenderState createRenderState()
    {
        return new HearthRenderState();
    }

    @Override
    public void extractRenderState(
            HearthBlockEntity blockEntity,
            HearthRenderState state,
            float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.CrumblingOverlay breakProgress
    )
    {
        BlockEntityRenderer.super.extractRenderState(
                blockEntity,
                state,
                partialTicks,
                cameraPosition,
                breakProgress
        );

        BlockState blockState =
                blockEntity.getBlockState();

        state.facing =
                blockState.hasProperty(HearthBottomBlock.FACING)
                        ? blockState.getValue(HearthBottomBlock.FACING)
                        : Direction.NORTH;

        state.smart =
                blockState.hasProperty(HearthBottomBlock.SMART)
                        && blockState.getValue(HearthBottomBlock.SMART);

        state.heating =
                blockState.hasProperty(HearthBottomBlock.HEATING)
                        && blockState.getValue(HearthBottomBlock.HEATING);

        state.cooling =
                blockState.hasProperty(HearthBottomBlock.COOLING)
                        && blockState.getValue(HearthBottomBlock.COOLING);

        state.hot =
                blockEntity.isUsingHotFuel();

        state.cold =
                blockEntity.isUsingColdFuel();
    }

    @Override
    public void submit(
            HearthRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState camera
    )
    {
        poseStack.pushPose();

        float rotation =
                state.facing.toYRot();

        poseStack.translate(
                0.5F,
                0.5F,
                0.5F
        );

        poseStack.mulPose(
                Axis.YP.rotationDegrees(-rotation)
        );

        poseStack.mulPose(
                Axis.XP.rotationDegrees(180.0F)
        );

        poseStack.translate(
                0.0F,
                -1.0F,
                0.0F
        );

        Identifier baseTexture =
                state.smart
                        ? TEXTURE_SMART
                        : TEXTURE;

        submitPart(
                collector,
                body,
                poseStack,
                baseTexture,
                false,
                state
        );

        submitPart(
                collector,
                grate,
                poseStack,
                baseTexture,
                false,
                state
        );

        if (state.hot)
        {
            submitPart(
                    collector,
                    grate,
                    poseStack,
                    TEXTURE_LIT,
                    false,
                    state
            );
        }

        if (state.cold)
        {
            submitPart(
                    collector,
                    body,
                    poseStack,
                    TEXTURE_FROST,
                    true,
                    state
            );
        }

        /*
         * Manual mode retains the original visible redstone-side indicators.
         * SMART mode is now our thermostat mode and uses hearth_smart.png
         * instead, matching the upstream visual distinction.
         */
        if (!state.smart)
        {
            if (state.heating)
            {
                submitPart(
                        collector,
                        body,
                        poseStack,
                        TEXTURE_HEAT_ON,
                        false,
                        state
                );
            }

            if (state.cooling)
            {
                submitPart(
                        collector,
                        body,
                        poseStack,
                        TEXTURE_COLD_ON,
                        false,
                        state
                );
            }
        }

        poseStack.popPose();
    }

    private static void submitPart(
            SubmitNodeCollector collector,
            ModelPart part,
            PoseStack poseStack,
            Identifier texture,
            boolean translucent,
            HearthRenderState state
    )
    {
        collector.submitModelPart(
                part,
                poseStack,
                translucent
                        ? RenderTypes.entityTranslucent(texture)
                        : RenderTypes.entityCutout(texture),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                null,
                -1,
                state.breakProgress
        );
    }

    public static final class HearthRenderState
            extends BlockEntityRenderState
    {
        private Direction facing = Direction.NORTH;
        private boolean smart;
        private boolean heating;
        private boolean cooling;
        private boolean hot;
        private boolean cold;
    }
}