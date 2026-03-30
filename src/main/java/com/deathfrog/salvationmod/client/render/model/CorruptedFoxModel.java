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

public class CorruptedFoxModel <T extends Mob> extends EntityModel<T>
{
    @SuppressWarnings("null")
    @Nonnull public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_fox");
    
    @Nonnull public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(LAYER, "main");

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart body;
    private final ModelPart right_arm;
    private final ModelPart left_arm;
    private final ModelPart right_leg;
    private final ModelPart left_leg;

    public CorruptedFoxModel(final ModelPart root)
    {
		this.root = root.getChild("root");
		this.head = this.root.getChild("head");
		this.body = this.root.getChild("body");
		this.right_arm = this.root.getChild("right_arm");
		this.left_arm = this.root.getChild("left_arm");
		this.right_leg = this.root.getChild("right_leg");
		this.left_leg = this.root.getChild("left_leg");
    }

    @SuppressWarnings({"unused", "null"})
    public static LayerDefinition createBodyLayer()
    {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 9.4917F, 1.8333F));

		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(1, 5).addBox(-4.0F, -6.425F, -2.625F, 8.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(6, 18).addBox(-2.0F, -2.525F, -5.625F, 4.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(8, 1).addBox(-4.0F, -8.525F, 1.375F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(15, 1).addBox(2.0F, -8.525F, 1.375F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.0667F, -2.2083F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(30, 1).addBox(-2.0F, 1.6538F, 1.7336F, 4.0F, 4.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.8545F, 0.4331F));

		PartDefinition body_r1 = body.addOrReplaceChild("body_r1", CubeListBuilder.create().texOffs(24, 15).addBox(-2.0F, -3.0F, -4.0F, 6.0F, 11.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, -2.3462F, -0.2664F, 0.3927F, 0.0F, 0.0F));

		PartDefinition left_arm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(4, 24).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 0.5083F, -1.8333F));

		PartDefinition right_arm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(4, 24).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-4.0F, 0.5083F, -1.8333F));

		PartDefinition left_leg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(13, 24).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.5F, 9.5083F, 1.1667F));

		PartDefinition right_leg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(13, 24).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, 9.5083F, 1.1667F));

		return LayerDefinition.create(meshdefinition, 64, 32);
	}

    @Override
    public void setupAnim(final @Nonnull T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch)
    {
        // Head look
        this.head.yRot = netHeadYaw * (Mth.PI / 180.0F);
        this.head.xRot = headPitch * (Mth.PI / 180.0F);

        // Walk cycle (biped-ish)
        this.right_leg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
        this.left_leg.xRot  = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.2F * limbSwingAmount;

        // Arms swing lightly while walking
        this.right_arm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.6F * limbSwingAmount;
        this.left_arm.xRot  = Mth.cos(limbSwing * 0.6662F) * 0.6F * limbSwingAmount;

        // Zombie-ish melee tell: if attacking, swing arms more aggressively
        // Vanilla zombies use more complex “attackTime” blending; this is intentionally simple.
        if (entity.isAggressive())
        {
            float swing = Mth.sin(ageInTicks * 0.8F) * 0.8F;
            this.right_arm.xRot -= swing;
            this.left_arm.xRot  -= swing;
        }

        // Subtle hunch
        this.body.xRot = 0.08F;
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