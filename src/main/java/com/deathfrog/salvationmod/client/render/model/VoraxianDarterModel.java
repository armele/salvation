package com.deathfrog.salvationmod.client.render.model;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.entity.VoraxianDarterEntity;
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

public class VoraxianDarterModel<T extends VoraxianDarterEntity> extends EntityModel<T>
{
    @SuppressWarnings("null")
    @Nonnull public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "voraxian_darter");
    @Nonnull public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(LAYER, "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart eyes;
    private final ModelPart left_eye;
    private final ModelPart right_eye;
    private final ModelPart maw;
    private final ModelPart side_one;
    private final ModelPart side_two;
    private final ModelPart tail_one;
    private final ModelPart tail_two;
    private final ModelPart tail_three;
    private final ModelPart stalk0;
    private final ModelPart stalk0_seg1;
    private final ModelPart stalk0_seg2;
    private final ModelPart stalk0_seg3;
    private final ModelPart legs;
    private final ModelPart leg_front_left_1;
    private final ModelPart leg_front_left_2;
    private final ModelPart leg_front_left_3;
    private final ModelPart leg_front_right_1;
    private final ModelPart leg_front_right_2;
    private final ModelPart leg_front_right_3;
    private final ModelPart leg_mid_left_1;
    private final ModelPart leg_mid_left_2;
    private final ModelPart leg_mid_left_3;
    private final ModelPart leg_mid_right_1;
    private final ModelPart leg_mid_right_2;
    private final ModelPart leg_mid_right_3;
    private final ModelPart leg_back_left_1;
    private final ModelPart leg_back_left_2;
    private final ModelPart leg_back_left_3;
    private final ModelPart leg_back_right_1;
    private final ModelPart leg_back_right_2;
    private final ModelPart leg_back_right_3;

    public VoraxianDarterModel(final ModelPart bakedRoot)
    {
        this.root = bakedRoot.getChild("root");
        this.body = this.root.getChild("body");
        this.eyes = this.body.getChild("eyes");
        this.left_eye = this.eyes.getChild("left_eye");
        this.right_eye = this.eyes.getChild("right_eye");
        this.maw = this.body.getChild("maw");
        this.side_one = this.maw.getChild("side_one");
        this.side_two = this.maw.getChild("side_two");
        this.tail_one = this.body.getChild("tail_one");
        this.tail_two = this.tail_one.getChild("tail_two");
        this.tail_three = this.tail_two.getChild("tail_three");
        this.stalk0 = this.tail_three.getChild("stalk0");
        this.stalk0_seg1 = this.stalk0.getChild("stalk0_seg1");
        this.stalk0_seg2 = this.stalk0_seg1.getChild("stalk0_seg2");
        this.stalk0_seg3 = this.stalk0_seg2.getChild("stalk0_seg3");
        this.legs = this.root.getChild("legs");
        this.leg_front_left_1 = this.legs.getChild("leg_front_left_1");
        this.leg_front_left_2 = this.leg_front_left_1.getChild("leg_front_left_2");
        this.leg_front_left_3 = this.leg_front_left_2.getChild("leg_front_left_3");
        this.leg_front_right_1 = this.legs.getChild("leg_front_right_1");
        this.leg_front_right_2 = this.leg_front_right_1.getChild("leg_front_right_2");
        this.leg_front_right_3 = this.leg_front_right_2.getChild("leg_front_right_3");
        this.leg_mid_left_1 = this.legs.getChild("leg_mid_left_1");
        this.leg_mid_left_2 = this.leg_mid_left_1.getChild("leg_mid_left_2");
        this.leg_mid_left_3 = this.leg_mid_left_2.getChild("leg_mid_left_3");
        this.leg_mid_right_1 = this.legs.getChild("leg_mid_right_1");
        this.leg_mid_right_2 = this.leg_mid_right_1.getChild("leg_mid_right_2");
        this.leg_mid_right_3 = this.leg_mid_right_2.getChild("leg_mid_right_3");
        this.leg_back_left_1 = this.legs.getChild("leg_back_left_1");
        this.leg_back_left_2 = this.leg_back_left_1.getChild("leg_back_left_2");
        this.leg_back_left_3 = this.leg_back_left_2.getChild("leg_back_left_3");
        this.leg_back_right_1 = this.legs.getChild("leg_back_right_1");
        this.leg_back_right_2 = this.leg_back_right_1.getChild("leg_back_right_2");
        this.leg_back_right_3 = this.leg_back_right_2.getChild("leg_back_right_3");
    }

    @SuppressWarnings({"unused", "null"})
    public static LayerDefinition createBodyLayer()
    {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 8.6667F, -0.4667F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 40).addBox(-6.0F, 3.3333F, -5.5333F, 12.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(7, 45).addBox(-5.0F, 1.3333F, -2.5333F, 10.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(104, 51).addBox(-7.0F, 0.3333F, -3.5333F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 51).addBox(1.0F, 0.3333F, -3.5333F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyes = body.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(-4.0F, -4.0F, 0.0F));

		PartDefinition left_eye = eyes.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(110, 38).addBox(-14.5F, 4.5F, 0.1F, 2.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(10.5F, 0.8333F, -6.5333F));

		PartDefinition right_eye = eyes.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(104, 38).addBox(11.5F, 4.5F, 0.1F, 2.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 0.8333F, -6.5333F));

		PartDefinition maw = body.addOrReplaceChild("maw", CubeListBuilder.create(), PartPose.offset(0.0F, 6.3333F, -6.0333F));

		PartDefinition side_one = maw.addOrReplaceChild("side_one", CubeListBuilder.create().texOffs(0, 47).addBox(0.4885F, -2.0F, -9.9146F, 3.0F, 3.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0115F, -1.6F, -2.8147F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0115F, -1.6F, -4.8147F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0115F, -1.6F, -6.8147F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0115F, -1.6F, -8.8147F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

		PartDefinition side_two = maw.addOrReplaceChild("side_two", CubeListBuilder.create().texOffs(0, 47).addBox(-3.5504F, -2.0F, -10.027F, 3.0F, 3.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0504F, -1.5F, -7.927F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0504F, -1.5F, -5.927F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0504F, -1.5F, -3.927F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-1.0504F, -1.5F, -9.927F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.0F));

		PartDefinition tail_one = body.addOrReplaceChild("tail_one", CubeListBuilder.create().texOffs(4, 40).addBox(-4.0F, -2.1065F, -0.591F, 8.0F, 4.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 5.9398F, 5.0577F));

		PartDefinition tail_two = tail_one.addOrReplaceChild("tail_two", CubeListBuilder.create().texOffs(10, 44).addBox(-3.0F, -1.713F, -1.1821F, 6.0F, 3.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.1065F, 10.591F));

		PartDefinition tail_three = tail_two.addOrReplaceChild("tail_three", CubeListBuilder.create().texOffs(12, 44).addBox(-2.0F, -1.25F, 0.0F, 4.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.037F, 5.8179F));

		PartDefinition stalk0 = tail_three.addOrReplaceChild("stalk0", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.25F, -0.75F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.75F));

		PartDefinition stalk0_seg1 = stalk0.addOrReplaceChild("stalk0_seg1", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.25F, -0.5F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 2.75F));

		PartDefinition stalk0_seg2 = stalk0_seg1.addOrReplaceChild("stalk0_seg2", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -0.25F, 3.0F));

		PartDefinition stalk0_seg3 = stalk0_seg2.addOrReplaceChild("stalk0_seg3", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));

		PartDefinition stalk0_stinger = stalk0_seg3.addOrReplaceChild("stalk0_stinger", CubeListBuilder.create().texOffs(116, 42).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 3.0F));

		PartDefinition legs = root.addOrReplaceChild("legs", CubeListBuilder.create().texOffs(9, 39).addBox(-1.0F, -0.6905F, -12.9571F, 2.0F, 5.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0238F, 8.4238F));

		PartDefinition leg_front_left_1 = legs.addOrReplaceChild("leg_front_left_1", CubeListBuilder.create().texOffs(62, 33).addBox(-0.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, -0.4984F, 5.9486F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_front_left_2 = leg_front_left_1.addOrReplaceChild("leg_front_left_2", CubeListBuilder.create(), PartPose.offset(3.4239F, 0.3827F, 0.0F));

		PartDefinition leg_front_left_two_r1 = leg_front_left_2.addOrReplaceChild("leg_front_left_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.9239F, -0.8827F, -0.5F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_front_left_3 = leg_front_left_2.addOrReplaceChild("leg_front_left_3", CubeListBuilder.create(), PartPose.offset(2.8415F, 0.2984F, -0.5333F));

		PartDefinition leg_front_left_three_r1 = leg_front_left_3.addOrReplaceChild("leg_front_left_three_r1", CubeListBuilder.create().texOffs(64, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7654F, 0.1522F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_front_right_1 = legs.addOrReplaceChild("leg_front_right_1", CubeListBuilder.create().texOffs(62, 33).addBox(-3.5F, -1.8333F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, -0.1905F, 6.0762F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_front_right_2 = leg_front_right_1.addOrReplaceChild("leg_front_right_2", CubeListBuilder.create(), PartPose.offset(-3.4239F, 0.0494F, 0.0F));

		PartDefinition leg_front_right_two_r1 = leg_front_right_2.addOrReplaceChild("leg_front_right_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.9239F, -0.8827F, -0.5F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_front_right_3 = leg_front_right_2.addOrReplaceChild("leg_front_right_3", CubeListBuilder.create(), PartPose.offset(-2.8415F, 0.2984F, -0.5333F));

		PartDefinition leg_front_right_three_r1 = leg_front_right_3.addOrReplaceChild("leg_front_right_three_r1", CubeListBuilder.create().texOffs(64, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7654F, 0.1522F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_mid_left_1 = legs.addOrReplaceChild("leg_mid_left_1", CubeListBuilder.create().texOffs(62, 33).addBox(-0.5F, -1.8333F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, -0.1905F, 1.0762F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_mid_left_2 = leg_mid_left_1.addOrReplaceChild("leg_mid_left_2", CubeListBuilder.create(), PartPose.offset(3.4239F, 0.0494F, 0.0F));

		PartDefinition leg_mid_left_two_r1 = leg_mid_left_2.addOrReplaceChild("leg_mid_left_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.9239F, -0.8827F, -0.5F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_mid_left_3 = leg_mid_left_2.addOrReplaceChild("leg_mid_left_3", CubeListBuilder.create(), PartPose.offset(2.8415F, 0.2984F, -0.5333F));

		PartDefinition leg_mid_left_three_r1 = leg_mid_left_3.addOrReplaceChild("leg_mid_left_three_r1", CubeListBuilder.create().texOffs(64, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7654F, 0.1522F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_mid_right_1 = legs.addOrReplaceChild("leg_mid_right_1", CubeListBuilder.create().texOffs(62, 33).addBox(-3.5F, -1.8333F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, -0.1905F, 1.0762F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_mid_right_2 = leg_mid_right_1.addOrReplaceChild("leg_mid_right_2", CubeListBuilder.create(), PartPose.offset(-3.4239F, 0.0494F, 0.0F));

		PartDefinition leg_mid_right_two_r1 = leg_mid_right_2.addOrReplaceChild("leg_mid_right_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.9239F, -0.8827F, -0.5F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_mid_right_3 = leg_mid_right_2.addOrReplaceChild("leg_mid_right_3", CubeListBuilder.create(), PartPose.offset(-2.8415F, 0.2984F, -0.5333F));

		PartDefinition leg_mid_right_three_r1 = leg_mid_right_3.addOrReplaceChild("leg_mid_right_three_r1", CubeListBuilder.create().texOffs(64, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7654F, 0.1522F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_back_left_1 = legs.addOrReplaceChild("leg_back_left_1", CubeListBuilder.create().texOffs(62, 33).addBox(-0.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, -0.5238F, -3.9238F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_back_left_2 = leg_back_left_1.addOrReplaceChild("leg_back_left_2", CubeListBuilder.create(), PartPose.offset(3.3066F, 0.4732F, -0.5167F));

		PartDefinition leg_back_left_two_r1 = leg_back_left_2.addOrReplaceChild("leg_back_left_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.8066F, -0.9732F, 0.0167F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_back_left_3 = leg_back_left_2.addOrReplaceChild("leg_back_left_3", CubeListBuilder.create(), PartPose.offset(2.9588F, 0.2079F, -0.0167F));

		PartDefinition leg_back_left_three_r1 = leg_back_left_3.addOrReplaceChild("leg_back_left_three_r1", CubeListBuilder.create().texOffs(64, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.7654F, 0.1522F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_back_right_1 = legs.addOrReplaceChild("leg_back_right_1", CubeListBuilder.create().texOffs(62, 33).addBox(-3.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, -0.5238F, -3.9238F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_back_right_2 = leg_back_right_1.addOrReplaceChild("leg_back_right_2", CubeListBuilder.create(), PartPose.offset(-3.4239F, 0.3827F, 0.0F));

		PartDefinition leg_back_right_two_r1 = leg_back_right_2.addOrReplaceChild("leg_back_right_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.9239F, -0.8827F, -0.5F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_back_right_3 = leg_back_right_2.addOrReplaceChild("leg_back_right_3", CubeListBuilder.create(), PartPose.offset(-2.8415F, 0.2984F, -0.5333F));

		PartDefinition leg_back_right_three_r1 = leg_back_right_3.addOrReplaceChild("leg_back_right_three_r1", CubeListBuilder.create().texOffs(64, 35).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7654F, 0.1522F, 0.0F, 0.0F, 0.0F, 0.3927F));

		return LayerDefinition.create(meshdefinition, 128, 64);
    }

    @Override
    public void setupAnim(final @Nonnull T entity, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw, final float headPitch)
    {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        final float swimAmount = Mth.clamp((float) entity.getDeltaMovement().length() * 5.0F + limbSwingAmount * 0.30F, 0.0F, 1.0F);
        final float swimCycle = ageInTicks * (0.20F + swimAmount * 0.30F);
        final float steerYaw = Mth.clamp(netHeadYaw * Mth.DEG_TO_RAD, -0.45F, 0.45F);
        final float steerPitch = Mth.clamp(headPitch * Mth.DEG_TO_RAD, -0.30F, 0.30F);
        final float idleRipple = ageInTicks * 0.12F;

        // Keep the torso relatively stable and let the tail carry most of the swim read.
        this.root.xRot += steerPitch * 0.10F;
        this.root.yRot += steerYaw * 0.12F;
        this.body.xRot += steerPitch * 0.14F + Mth.sin(idleRipple) * 0.02F;
        this.body.yRot += steerYaw * 0.08F;
        this.body.y += Mth.sin(swimCycle) * 0.22F * swimAmount;
        this.legs.y += Mth.sin(swimCycle + 0.35F) * 0.12F * swimAmount;

        this.eyes.yRot += steerYaw * 0.18F;
        this.eyes.xRot += steerPitch * 0.14F;
        this.left_eye.xRot += Mth.cos(ageInTicks * 0.08F) * 0.02F;
        this.right_eye.xRot += Mth.cos(ageInTicks * 0.08F) * 0.02F;

        this.animateRowingLeg(this.leg_front_left_1, this.leg_front_left_2, this.leg_front_left_3, true, swimCycle, swimAmount);
        this.animateRowingLeg(this.leg_front_right_1, this.leg_front_right_2, this.leg_front_right_3, false, swimCycle, swimAmount);
        this.animateRowingLeg(this.leg_mid_left_1, this.leg_mid_left_2, this.leg_mid_left_3, true, swimCycle, swimAmount);
        this.animateRowingLeg(this.leg_mid_right_1, this.leg_mid_right_2, this.leg_mid_right_3, false, swimCycle, swimAmount);
        this.animateRowingLeg(this.leg_back_left_1, this.leg_back_left_2, this.leg_back_left_3, true, swimCycle, swimAmount);
        this.animateRowingLeg(this.leg_back_right_1, this.leg_back_right_2, this.leg_back_right_3, false, swimCycle, swimAmount);

        this.animateTail(swimCycle, steerYaw, swimAmount);
        this.applyMawPose(ageInTicks, swimAmount);
    }

    private void animateRowingLeg(
        final ModelPart upper,
        final ModelPart middle,
        final ModelPart lower,
        final boolean leftSide,
        final float swimCycle,
        final float swimAmount)
    {
        final float direction = leftSide ? 1.0F : -1.0F;
        final float stroke = Mth.sin(swimCycle) * swimAmount;
        final float recovery = Mth.cos(swimCycle) * swimAmount;

        // All legs row together: power stroke sweeps back, recovery folds inward.
        upper.xRot += 0.20F + stroke * 0.42F;
        upper.yRot += direction * (0.04F + recovery * 0.04F);
        upper.zRot += direction * (0.12F + stroke * 0.10F);

        middle.zRot += direction * (-0.36F - stroke * 0.28F);
        middle.xRot += recovery * 0.05F;

        lower.zRot += direction * (0.30F + stroke * 0.22F);
        lower.xRot += -stroke * 0.12F;
    }

    private void animateTail(final float swimCycle, final float steerYaw, final float swimAmount)
    {
        final float undulation = 0.08F + swimAmount * 0.20F;
        final float idleLift = 0.03F;

        // Primary motion goal: tail undulates up/down, with amplitude increasing toward the tip.
        this.tail_one.xRot += idleLift + Mth.sin(swimCycle + 0.00F) * undulation;
        this.tail_two.xRot += idleLift + Mth.sin(swimCycle + 0.55F) * (undulation * 1.15F);
        this.tail_three.xRot += idleLift + Mth.sin(swimCycle + 1.10F) * (undulation * 1.35F);
        this.stalk0.xRot += idleLift + Mth.sin(swimCycle + 1.65F) * (undulation * 1.55F);
        this.stalk0_seg1.xRot += idleLift + Mth.sin(swimCycle + 2.15F) * (undulation * 1.75F);
        this.stalk0_seg2.xRot += idleLift + Mth.sin(swimCycle + 2.65F) * (undulation * 1.90F);
        this.stalk0_seg3.xRot += idleLift + Mth.sin(swimCycle + 3.10F) * (undulation * 2.05F);

        // Steering stays subtle so the motion still reads as vertical undulation first.
        final float steering = steerYaw * (0.04F + swimAmount * 0.05F);
        this.tail_one.yRot += steering * 0.25F;
        this.tail_two.yRot += steering * 0.40F;
        this.tail_three.yRot += steering * 0.55F;
        this.stalk0.yRot += steering * 0.75F;
        this.stalk0_seg1.yRot += steering * 0.95F;
        this.stalk0_seg2.yRot += steering * 1.10F;
        this.stalk0_seg3.yRot += steering * 1.25F;
    }

    private void applyMawPose(final float ageInTicks, final float swimAmount)
    {
        final float openAmount = 0.08F + (0.5F + 0.5F * Mth.sin(ageInTicks * 0.10F)) * (0.12F + swimAmount * 0.08F);
        final float openYaw = openAmount * 1.05F;

        // The darter's BlockBench maw is modeled closed at rest, so animate outward from neutral.
        this.side_one.yRot -= openYaw;
        this.side_two.yRot += openYaw;
        this.side_one.zRot -= openAmount * 0.10F;
        this.side_two.zRot += openAmount * 0.10F;
        this.maw.xRot -= openAmount * 0.15F;
    }

    @Override
    public void renderToBuffer(final @Nonnull PoseStack poseStack, final @Nonnull VertexConsumer vertexConsumer, final int packedLight, final int packedOverlay, final int color)
    {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
