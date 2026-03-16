package com.deathfrog.salvationmod.client.render.model;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

public class VoraxianObserverModel <T extends Mob> extends EntityModel<T>
{
    @Nonnull public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "voraxian_observer");
    
    @Nonnull public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(LAYER, "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart eye;
    private final ModelPart lowerMaw;
    private final ModelPart stalk0;
    private final ModelPart stalk0Seg1;
    private final ModelPart stalk0Seg2;
    private final ModelPart stalk0Seg3;
    private final ModelPart stalk1;
    private final ModelPart stalk1Seg1;
    private final ModelPart stalk1Seg2;
    private final ModelPart stalk1Seg3;
    private final ModelPart stalk2;
    private final ModelPart stalk2Seg1;
    private final ModelPart stalk2Seg2;
    private final ModelPart stalk2Seg3;
    private final ModelPart stalk3;
    private final ModelPart stalk3Seg1;
    private final ModelPart stalk3Seg2;
    private final ModelPart stalk3Seg3;
    private final ModelPart stalk4;
    private final ModelPart stalk5;
    private final ModelPart stalk6;
    private final ModelPart stalk7;

    public VoraxianObserverModel(final ModelPart bakedRoot)
    {
        this.root = bakedRoot.getChild("root");
        this.body = this.root.getChild("body");
        this.eye = this.body.getChild("eye");
        this.lowerMaw = this.body.getChild("lower_maw");

        this.stalk0 = this.body.getChild("stalk0");
        this.stalk0Seg1 = this.stalk0.getChild("stalk0_seg1");
        this.stalk0Seg2 = this.stalk0Seg1.getChild("stalk0_seg2");
        this.stalk0Seg3 = this.stalk0Seg2.getChild("stalk0_seg3");

        this.stalk1 = this.body.getChild("stalk1");
        this.stalk1Seg1 = this.stalk1.getChild("stalk1_seg1");
        this.stalk1Seg2 = this.stalk1Seg1.getChild("stalk1_seg2");
        this.stalk1Seg3 = this.stalk1Seg2.getChild("stalk1_seg3");

        this.stalk2 = this.body.getChild("stalk2");
        this.stalk2Seg1 = this.stalk2.getChild("stalk2_seg1");
        this.stalk2Seg2 = this.stalk2Seg1.getChild("stalk2_seg2");
        this.stalk2Seg3 = this.stalk2Seg2.getChild("stalk2_seg3");

        this.stalk3 = this.body.getChild("stalk3");
        this.stalk3Seg1 = this.stalk3.getChild("stalk3_seg1");
        this.stalk3Seg2 = this.stalk3Seg1.getChild("stalk3_seg2");
        this.stalk3Seg3 = this.stalk3Seg2.getChild("stalk3_seg3");

        this.stalk4 = this.body.getChild("stalk4");
        this.stalk5 = this.body.getChild("stalk5");
        this.stalk6 = this.body.getChild("stalk6");
        this.stalk7 = this.body.getChild("stalk7");
    }

    @SuppressWarnings({"unused"})
    public static LayerDefinition createBodyLayer()
    {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 3.6667F, -0.4667F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 40).addBox(-6.0F, -3.6667F, -5.5333F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(-7.0F, -4.6667F, -6.5333F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(1.0F, -4.6667F, -6.5333F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(-7.0F, -4.6667F, 1.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(1.0F, -4.6667F, 1.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eye = body.addOrReplaceChild("eye", CubeListBuilder.create().texOffs(24, 0).addBox(-3.0F, -0.6667F, -7.5333F, 6.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition lower_maw = body.addOrReplaceChild("lower_maw", CubeListBuilder.create().texOffs(64, 54).addBox(-3.0F, 5.3333F, -2.5333F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition stalk0 = body.addOrReplaceChild("stalk0", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, -3.6667F, 0.4667F));

		PartDefinition stalk0_seg1 = stalk0.addOrReplaceChild("stalk0_seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk0_seg2 = stalk0_seg1.addOrReplaceChild("stalk0_seg2", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk0_seg3 = stalk0_seg2.addOrReplaceChild("stalk0_seg3", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk0_orb = stalk0_seg3.addOrReplaceChild("stalk0_orb", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk1 = body.addOrReplaceChild("stalk1", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, -3.6667F, 0.4667F));

		PartDefinition stalk1_seg1 = stalk1.addOrReplaceChild("stalk1_seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk1_seg2 = stalk1_seg1.addOrReplaceChild("stalk1_seg2", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk1_seg3 = stalk1_seg2.addOrReplaceChild("stalk1_seg3", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk1_orb = stalk1_seg3.addOrReplaceChild("stalk1_orb", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk2 = body.addOrReplaceChild("stalk2", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.6667F, 5.4667F));

		PartDefinition stalk2_seg1 = stalk2.addOrReplaceChild("stalk2_seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk2_seg2 = stalk2_seg1.addOrReplaceChild("stalk2_seg2", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk2_seg3 = stalk2_seg2.addOrReplaceChild("stalk2_seg3", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk2_orb = stalk2_seg3.addOrReplaceChild("stalk2_orb", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk3 = body.addOrReplaceChild("stalk3", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.6667F, -4.5333F));

		PartDefinition stalk3_seg1 = stalk3.addOrReplaceChild("stalk3_seg1", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk3_seg2 = stalk3_seg1.addOrReplaceChild("stalk3_seg2", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk3_seg3 = stalk3_seg2.addOrReplaceChild("stalk3_seg3", CubeListBuilder.create().texOffs(16, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk3_orb = stalk3_seg3.addOrReplaceChild("stalk3_orb", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk4 = body.addOrReplaceChild("stalk4", CubeListBuilder.create().texOffs(56, 52).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(112, 0).addBox(-2.0F, 15.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, 2.3333F, 4.4667F));

		PartDefinition stalk5 = body.addOrReplaceChild("stalk5", CubeListBuilder.create().texOffs(56, 52).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(112, 0).addBox(-2.0F, 15.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 2.3333F, 4.4667F));

		PartDefinition stalk6 = body.addOrReplaceChild("stalk6", CubeListBuilder.create().texOffs(56, 52).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(112, 0).addBox(-2.0F, 15.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, 2.3333F, -3.5333F));

		PartDefinition stalk7 = body.addOrReplaceChild("stalk7", CubeListBuilder.create().texOffs(56, 52).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 10.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(112, 0).addBox(-2.0F, 15.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 2.3333F, -3.5333F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

    @Override
    public void setupAnim(final @Nonnull T entity, final float limbSwing, final float limbSwingAmount,
                        final float ageInTicks, final float netHeadYaw, final float headPitch)
    {
        final boolean aggressive = entity.isAggressive();

        // Body drift / float
        this.body.xRot = Mth.cos(ageInTicks * 0.06F) * 0.03F;
        this.body.yRot = aggressive ? 0.0F : Mth.sin(ageInTicks * 0.04F) * 0.025F;
        this.body.zRot = Mth.sin(ageInTicks * 0.05F) * 0.06F;
        this.body.y = Mth.sin(ageInTicks * 0.10F) * 0.8F;

        // The renderer already rotates the full mob, so only the eye should track the target.
        this.eye.xRot = headPitch * ((float) Math.PI / 180.0F) * (aggressive ? 0.14F : 0.10F);
        this.eye.yRot = Mth.clamp(netHeadYaw, -35.0F, 35.0F) * ((float) Math.PI / 180.0F) * (aggressive ? 1.0F : 0.45F);
        this.lowerMaw.xRot = Mth.sin(ageInTicks * 0.08F) * 0.04F;

        // Top 4 articulated stalks
        animateTopChain(this.stalk0, this.stalk0Seg1, this.stalk0Seg2, this.stalk0Seg3, ageInTicks, 0.0F, limbSwingAmount);
        animateTopChain(this.stalk1, this.stalk1Seg1, this.stalk1Seg2, this.stalk1Seg3, ageInTicks, 1.15F, limbSwingAmount);
        animateTopChain(this.stalk2, this.stalk2Seg1, this.stalk2Seg2, this.stalk2Seg3, ageInTicks, 2.30F, limbSwingAmount);
        animateTopChain(this.stalk3, this.stalk3Seg1, this.stalk3Seg2, this.stalk3Seg3, ageInTicks, 3.45F, limbSwingAmount);

        // Bottom 4 rigid stalks
        animateBottomStalk(this.stalk4, ageInTicks, 0.50F, limbSwingAmount);
        animateBottomStalk(this.stalk5, ageInTicks, 1.80F, limbSwingAmount);
        animateBottomStalk(this.stalk6, ageInTicks, 3.10F, limbSwingAmount);
        animateBottomStalk(this.stalk7, ageInTicks, 4.40F, limbSwingAmount);

        if (aggressive)
        {
            final float flare = 0.16F + Mth.sin(ageInTicks * 0.45F) * 0.10F;

            this.stalk0.yRot += flare;
            this.stalk1.yRot -= flare;
            this.stalk2.zRot += flare * 0.70F;
            this.stalk3.zRot -= flare * 0.70F;

            this.stalk4.yRot += flare * 0.35F;
            this.stalk5.yRot -= flare * 0.35F;
            this.stalk6.zRot += flare * 0.30F;
            this.stalk7.zRot -= flare * 0.30F;

            // this.lowerMaw.xRot += 0.12F;
        }
    }

    private static void animateTopChain(final ModelPart base,
                                        final ModelPart seg1,
                                        final ModelPart seg2,
                                        final ModelPart seg3,
                                        final float ageInTicks,
                                        final float phase,
                                        final float limbSwingAmount)
    {
        final float idle = ageInTicks * 0.16F;
        final float motion = limbSwingAmount * 0.18F;

        // Base segment: broad and slow
        base.xRot = -0.18F + Mth.sin(idle + phase) * 0.12F + motion;
        base.yRot = Mth.cos(idle * 0.75F + phase) * 0.10F;
        base.zRot = Mth.sin(idle * 0.85F + phase) * 0.08F;

        // Segment 1
        seg1.xRot = Mth.sin(idle * 1.05F + phase + 0.35F) * 0.14F;
        seg1.yRot = Mth.cos(idle * 0.90F + phase + 0.35F) * 0.06F;
        seg1.zRot = Mth.sin(idle * 0.95F + phase + 0.35F) * 0.06F;

        // Segment 2
        seg2.xRot = Mth.sin(idle * 1.15F + phase + 0.75F) * 0.18F;
        seg2.yRot = Mth.cos(idle * 1.00F + phase + 0.75F) * 0.07F;
        seg2.zRot = Mth.sin(idle * 1.05F + phase + 0.75F) * 0.07F;

        // Segment 3 / tip: most expressive
        seg3.xRot = Mth.sin(idle * 1.30F + phase + 1.15F) * 0.22F;
        seg3.yRot = Mth.cos(idle * 1.10F + phase + 1.15F) * 0.10F;
        seg3.zRot = Mth.sin(idle * 1.18F + phase + 1.15F) * 0.10F;
    }

    private static void animateBottomStalk(final ModelPart stalk,
                                        final float ageInTicks,
                                        final float phase,
                                        final float limbSwingAmount)
    {
        final float idle = ageInTicks * 0.12F;
        final float motion = limbSwingAmount * 0.10F;

        stalk.xRot = -0.26F + Mth.sin(idle + phase) * 0.10F + motion;
        stalk.yRot = Mth.cos(idle * 0.80F + phase) * 0.06F;
        stalk.zRot = Mth.sin(idle * 0.95F + phase) * 0.06F;
    }

    /**
     * Renders this model to the given vertex consumer, using the given pose stack,
     * packed light, packed overlay, and RGBA values.
     *
     * @param poseStack the pose stack to use for rendering
     * @param buffer the vertex consumer to render to
     * @param packedLight the packed light value to use for rendering
     * @param packedOverlay the packed overlay value to use for rendering
     * @param rgba the RGBA value to use for rendering
     */
    @Override
    public void renderToBuffer(final @Nonnull PoseStack poseStack, final @Nonnull VertexConsumer buffer,
                               int packedLight, int packedOverlay, int rgba)
    {
        root.render(poseStack, buffer, packedLight, packedOverlay, rgba);
    }
}
