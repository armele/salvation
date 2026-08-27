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

public class CorruptedRabbitModel<T extends Mob> extends EntityModel<T>
{
    @SuppressWarnings("null")
    @Nonnull public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "corrupted_rabbit"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public CorruptedRabbitModel(final ModelPart root)
    {
        this.root = root.getChild("root");
        this.head = this.root.getChild("head");
        this.body = this.root.getChild("body");
        this.rightArm = this.root.getChild("right_arm");
        this.leftArm = this.root.getChild("left_arm");
        this.rightLeg = this.root.getChild("right_leg");
        this.leftLeg = this.root.getChild("left_leg");
    }

    @SuppressWarnings({"null"})
    public static LayerDefinition createBodyLayer()
    {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition definition = mesh.getRoot();
        final PartDefinition root = definition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        root.addOrReplaceChild("head", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-3.0F, -5.1206F, -3.9649F, 6.0F, 5.0F, 10.0F, new CubeDeformation(0.0F))
            .texOffs(12, 10).addBox(-1.5F, -3.2206F, -4.9649F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
            .texOffs(9, 18).addBox(-4.0F, -9.3206F, 4.0F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
            .texOffs(11, 4).addBox(2.0F, -9.3206F, 4.0F, 2.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)),
            PartPose.offset(0.0F, -9.8794F, -3.2851F));

        final PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, -1.9703F, 1.5869F));
        body.addOrReplaceChild("body_r1", CubeListBuilder.create().texOffs(9, 12)
            .addBox(-2.5F, -2.1522F, -3.2654F, 5.0F, 7.0F, 5.0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0.0F, -7.0011F, -0.5084F, 0.3927F, 0.0F, 0.0F));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(9, 14)
            .addBox(-4.0F, -1.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.5F, -7.0F, -0.25F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(6, 14).mirror()
            .addBox(0.0F, -1.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(2.5F, -7.0F, -0.25F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(8, 12).mirror()
            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-2.75F, -5.0F, 3.75F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(6, 13)
            .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(2.75F, -5.0F, 3.75F));

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(final @Nonnull T entity, float limbSwing, float limbSwingAmount,
        float ageInTicks, float netHeadYaw, float headPitch)
    {
        this.head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        this.head.xRot = headPitch * Mth.DEG_TO_RAD;
        this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
        this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.2F * limbSwingAmount;
        this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 0.6F * limbSwingAmount;
        this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 0.6F * limbSwingAmount;
        if (entity.isAggressive())
        {
            final float swing = Mth.sin(ageInTicks * 0.8F) * 0.8F;
            this.rightArm.xRot -= swing;
            this.leftArm.xRot -= swing;
        }
        this.body.xRot = 0.08F;
    }

    @Override
    public void renderToBuffer(final @Nonnull PoseStack poseStack, final @Nonnull VertexConsumer buffer,
        int packedLight, int packedOverlay, int rgba)
    {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, rgba);
    }
}
