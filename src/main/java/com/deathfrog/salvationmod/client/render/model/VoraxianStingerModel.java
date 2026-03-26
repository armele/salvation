package com.deathfrog.salvationmod.client.render.model;

import javax.annotation.Nonnull;

import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.entity.VoraxianStingerEntity;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class VoraxianStingerModel<T extends VoraxianStingerEntity> extends EntityModel<T>
{
    @Nonnull public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "voraxian_stinger");
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
    // private final ModelPart stalk0_stinger;
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


    public VoraxianStingerModel(final ModelPart bakedRoot)
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
		// this.stalk0_stinger = this.stalk0_seg3.getChild("stalk0_stinger");
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




    @SuppressWarnings("unused")
    public static LayerDefinition createBodyLayer()
    {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 8.6667F, -0.4667F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 40).addBox(-6.0F, 3.3333F, -5.5333F, 12.0F, 5.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(7, 45).addBox(-5.0F, 1.3333F, -2.5333F, 10.0F, 5.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(-7.0F, 0.3333F, -3.5333F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(1.0F, 0.3333F, -3.5333F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyes = body.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(-4.0F, -4.0F, 0.0F));

		PartDefinition left_eye = eyes.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(25, 0).addBox(-2.5F, -0.5F, -0.9F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(10.5F, 0.8333F, -6.5333F));

		PartDefinition stalk_r1 = left_eye.addOrReplaceChild("stalk_r1", CubeListBuilder.create().texOffs(91, 0).addBox(-1.1F, -6.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, 7.5F, 2.1F, 0.0F, 0.0F, 0.3927F));

		PartDefinition right_eye = eyes.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(24, 0).addBox(-3.5F, -0.5F, -0.9F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 0.8333F, -6.5333F));

		PartDefinition stalk_r2 = right_eye.addOrReplaceChild("stalk_r2", CubeListBuilder.create().texOffs(91, 0).addBox(-0.9F, -6.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 7.5F, 2.1F, 0.0F, 0.0F, -0.3927F));

		PartDefinition maw = body.addOrReplaceChild("maw", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition side_one = maw.addOrReplaceChild("side_one", CubeListBuilder.create().texOffs(0, 47).addBox(-1.5F, -1.0F, -10.0F, 3.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -0.6F, -2.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -0.6F, -4.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -0.6F, -6.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -0.6F, -8.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.8308F, 6.3333F, -3.7406F, 0.0F, -0.3927F, 0.0F));

		PartDefinition side_two = maw.addOrReplaceChild("side_two", CubeListBuilder.create().texOffs(0, 47).addBox(-1.5F, -1.0F, -5.0F, 3.0F, 2.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -0.5F, -2.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -0.5F, -0.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -0.5F, 1.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -0.5F, -4.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 6.3333F, -8.5333F, 0.0F, 0.3927F, 0.0F));

		PartDefinition tail_one = body.addOrReplaceChild("tail_one", CubeListBuilder.create(), PartPose.offset(0.0F, 5.7686F, 5.005F));

		PartDefinition body_rear_r1 = tail_one.addOrReplaceChild("body_rear_r1", CubeListBuilder.create().texOffs(4, 40).addBox(-4.0F, -2.0F, 0.0F, 8.0F, 4.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0647F, -0.5383F, 0.3927F, 0.0F, 0.0F));

		PartDefinition tail_two = tail_one.addOrReplaceChild("tail_two", CubeListBuilder.create().texOffs(15, 49).addBox(-3.0F, -8.0833F, -1.5167F, 6.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.852F, 8.9784F));

		PartDefinition tail_three = tail_two.addOrReplaceChild("tail_three", CubeListBuilder.create(), PartPose.offset(0.0F, -7.6574F, -1.1525F));

		PartDefinition body_rear_three_r1 = tail_three.addOrReplaceChild("body_rear_three_r1", CubeListBuilder.create().texOffs(12, 44).addBox(-2.0F, -1.0F, -7.0F, 4.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.574F, 0.6358F, -0.3927F, 0.0F, 0.0F));

		PartDefinition stalk0 = tail_three.addOrReplaceChild("stalk0", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.926F, -5.3642F));

		PartDefinition stalk0_seg1 = stalk0.addOrReplaceChild("stalk0_seg1", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.0F, -3.5F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -3.5F));

		PartDefinition stalk0_seg2 = stalk0_seg1.addOrReplaceChild("stalk0_seg2", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -3.5F));

		PartDefinition stalk0_seg3 = stalk0_seg2.addOrReplaceChild("stalk0_seg3", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -1.0F, -2.5F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -3.5F));

		PartDefinition stalk0_stinger = stalk0_seg3.addOrReplaceChild("stalk0_stinger", CubeListBuilder.create().texOffs(116, 42).addBox(-0.5F, -0.5F, -5.5F, 1.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -2.0F));

		PartDefinition legs = root.addOrReplaceChild("legs", CubeListBuilder.create().texOffs(5, 43).addBox(-5.0F, 6.3333F, -4.5333F, 10.0F, 5.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_front_left_1 = legs.addOrReplaceChild("leg_front_left_1", CubeListBuilder.create().texOffs(62, 33).addBox(-0.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, 11.5F, -3.5F, -0.3927F, 0.0F, 0.0F));

		PartDefinition leg_front_left_2 = leg_front_left_1.addOrReplaceChild("leg_front_left_2", CubeListBuilder.create(), PartPose.offset(-1.5F, 0.5F, 0.5F));

		PartDefinition leg_front_left_two_r1 = leg_front_left_2.addOrReplaceChild("leg_front_left_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_front_left_3 = leg_front_left_2.addOrReplaceChild("leg_front_left_3", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_front_left_three_r1 = leg_front_left_3.addOrReplaceChild("leg_front_left_three_r1", CubeListBuilder.create().texOffs(64, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 0.3333F, -1.0333F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_front_right_1 = legs.addOrReplaceChild("leg_front_right_1", CubeListBuilder.create().texOffs(62, 33).addBox(-3.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, 11.5F, -3.5F, -0.3927F, 0.0F, 0.0F));

		PartDefinition leg_front_right_2 = leg_front_right_1.addOrReplaceChild("leg_front_right_2", CubeListBuilder.create(), PartPose.offset(1.5F, 0.5F, 0.5F));

		PartDefinition leg_front_right_two_r1 = leg_front_right_2.addOrReplaceChild("leg_front_right_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, -1.0F, -1.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_front_right_3 = leg_front_right_2.addOrReplaceChild("leg_front_right_3", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_front_right_three_r1 = leg_front_right_3.addOrReplaceChild("leg_front_right_three_r1", CubeListBuilder.create().texOffs(64, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 0.3333F, -1.0333F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_mid_left_1 = legs.addOrReplaceChild("leg_mid_left_1", CubeListBuilder.create().texOffs(62, 33).addBox(-0.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(4.5F, 11.5F, 0.5F));

		PartDefinition leg_mid_left_2 = leg_mid_left_1.addOrReplaceChild("leg_mid_left_2", CubeListBuilder.create(), PartPose.offset(-1.5F, 0.5F, 0.5F));

		PartDefinition leg_mid_left_two_r1 = leg_mid_left_2.addOrReplaceChild("leg_mid_left_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_mid_left_3 = leg_mid_left_2.addOrReplaceChild("leg_mid_left_3", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_mid_left_three_r1 = leg_mid_left_3.addOrReplaceChild("leg_mid_left_three_r1", CubeListBuilder.create().texOffs(64, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 0.3333F, -1.0333F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_mid_right_1 = legs.addOrReplaceChild("leg_mid_right_1", CubeListBuilder.create().texOffs(62, 33).addBox(-3.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.5F, 11.5F, 0.5F));

		PartDefinition leg_mid_right_2 = leg_mid_right_1.addOrReplaceChild("leg_mid_right_2", CubeListBuilder.create(), PartPose.offset(1.5F, 0.5F, 0.5F));

		PartDefinition leg_mid_right_two_r1 = leg_mid_right_2.addOrReplaceChild("leg_mid_right_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, -1.0F, -1.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_mid_right_3 = leg_mid_right_2.addOrReplaceChild("leg_mid_right_3", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_mid_right_three_r1 = leg_mid_right_3.addOrReplaceChild("leg_mid_right_three_r1", CubeListBuilder.create().texOffs(64, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 0.3333F, -1.0333F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_back_left_1 = legs.addOrReplaceChild("leg_back_left_1", CubeListBuilder.create().texOffs(62, 33).addBox(-0.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.5F, 11.5F, 4.5F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_back_left_2 = leg_back_left_1.addOrReplaceChild("leg_back_left_2", CubeListBuilder.create(), PartPose.offset(-1.5F, 0.5F, 0.5F));

		PartDefinition leg_back_left_two_r1 = leg_back_left_2.addOrReplaceChild("leg_back_left_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(0.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, -1.0F, -1.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition leg_back_left_3 = leg_back_left_2.addOrReplaceChild("leg_back_left_3", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_back_left_three_r1 = leg_back_left_3.addOrReplaceChild("leg_back_left_three_r1", CubeListBuilder.create().texOffs(64, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(7.0F, 0.3333F, -1.0333F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_back_right_1 = legs.addOrReplaceChild("leg_back_right_1", CubeListBuilder.create().texOffs(62, 33).addBox(-3.5F, -1.5F, -1.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.5F, 11.5F, 4.5F, 0.3927F, 0.0F, 0.0F));

		PartDefinition leg_back_right_2 = leg_back_right_1.addOrReplaceChild("leg_back_right_2", CubeListBuilder.create(), PartPose.offset(1.5F, 0.5F, 0.5F));

		PartDefinition leg_back_right_two_r1 = leg_back_right_2.addOrReplaceChild("leg_back_right_two_r1", CubeListBuilder.create().texOffs(62, 33).addBox(-4.0F, -1.0F, -1.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, -1.0F, -1.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leg_back_right_3 = leg_back_right_2.addOrReplaceChild("leg_back_right_3", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_back_right_three_r1 = leg_back_right_3.addOrReplaceChild("leg_back_right_three_r1", CubeListBuilder.create().texOffs(64, 37).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, 0.3333F, -1.0333F, 0.0F, 0.0F, 0.3927F));

		return LayerDefinition.create(meshdefinition, 128, 64);
    }

    /**
     * Animates the model according to the given parameters.
     *
     * @param entity The entity to animate.
     * @param limbSwing The speed at which the entity is moving its limbs.
     * @param limbSwingAmount The amount of movement in the entity's limbs.
     * @param ageInTicks The age of the entity in ticks.
     * @param netHeadYaw The net head yaw of the entity, in radians.
     * @param headPitch The head pitch of the entity, in radians.
     */
    @Override
    public void setupAnim(final @Nonnull T entity, final float limbSwing, final float limbSwingAmount, final float ageInTicks, final float netHeadYaw, final float headPitch)
    {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        final boolean aggressive = entity.isAggressive();
        final float walkAmount = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        final float partialTick = ageInTicks - entity.tickCount;
        final float attackProgress = Mth.clamp(entity.getAttackAnim(partialTick), 0.0F, 1.0F);
        final LivingEntity target = entity.getTarget();
        final boolean hasTarget = target != null && target.isAlive();

        this.body.y += Mth.sin(limbSwing * 0.9F) * walkAmount * 0.35F;
        this.body.xRot += Mth.cos(limbSwing * 0.45F) * walkAmount * 0.035F;
        this.body.zRot += Mth.sin(ageInTicks * 0.06F) * 0.02F;
        this.eyes.yRot += Mth.clamp(netHeadYaw * ((float) Math.PI / 180.0F), -0.35F, 0.35F) * 0.12F;
        this.eyes.xRot += Mth.clamp(headPitch * ((float) Math.PI / 180.0F), -0.25F, 0.25F) * 0.08F;

        this.animateLegPair(this.leg_front_left_1, this.leg_front_left_2, this.leg_front_left_3, true, limbSwing, walkAmount, 0.0F);
        this.animateLegPair(this.leg_front_right_1, this.leg_front_right_2, this.leg_front_right_3, false, limbSwing, walkAmount, 0.0F);
        this.animateLegPair(this.leg_mid_left_1, this.leg_mid_left_2, this.leg_mid_left_3, true, limbSwing, walkAmount, Mth.PI);
        this.animateLegPair(this.leg_mid_right_1, this.leg_mid_right_2, this.leg_mid_right_3, false, limbSwing, walkAmount, Mth.PI);
        this.animateLegPair(this.leg_back_left_1, this.leg_back_left_2, this.leg_back_left_3, true, limbSwing, walkAmount, 0.65F);
        this.animateLegPair(this.leg_back_right_1, this.leg_back_right_2, this.leg_back_right_3, false, limbSwing, walkAmount, 0.65F);

        this.animateEyes(entity, ageInTicks, target, hasTarget);
        this.animateTail(ageInTicks, netHeadYaw, walkAmount, aggressive, attackProgress);
        this.applyMawChompPose(ageInTicks, aggressive, attackProgress);
    }

    private void animateEyes(
        final T entity,
        final float ageInTicks,
        final LivingEntity target,
        final boolean hasTarget)
    {
        if (hasTarget && target != null)
        {
            final Vec3 fromEyes = entity.getEyePosition();
            final Vec3 toTarget = target.getEyePosition().subtract(fromEyes);

            final double flatDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            final float targetYaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180F / Math.PI)) - 90F;
            final float targetPitch = (float) (-(Mth.atan2(toTarget.y, flatDist) * (180F / Math.PI)));

            final float relYaw = Mth.wrapDegrees(targetYaw - entity.getYRot());
            final float eyeYaw = Mth.clamp(relYaw * ((float) Math.PI / 180F), -0.22F, 0.22F);
            final float eyePitch = Mth.clamp(targetPitch * ((float) Math.PI / 180F), -0.15F, 0.15F);

            this.left_eye.yRot += eyeYaw;
            this.left_eye.xRot += eyePitch;
            this.right_eye.yRot += eyeYaw;
            this.right_eye.xRot += eyePitch;
            return;
        }

        final float eyeDriftYaw = Mth.sin(ageInTicks * 0.06F) * 0.03F;
        final float eyeDriftPitch = Mth.cos(ageInTicks * 0.05F) * 0.02F;

        this.left_eye.yRot += eyeDriftYaw;
        this.left_eye.xRot += eyeDriftPitch;
        this.right_eye.yRot += eyeDriftYaw;
        this.right_eye.xRot += eyeDriftPitch;
    }

    /**
     * Animates a single leg of the model according to the given parameters.
     * 
     * @param upper The upper part of the leg.
     * @param middle The middle part of the leg.
     * @param lower The lower part of the leg.
     * @param leftSide Whether the leg is on the left side of the model.
     * @param limbSwing The speed at which the entity is moving its limbs.
     * @param limbSwingAmount The amount of movement in the entity's limbs.
     * @param phase The phase of the walk animation that the leg is currently in.
     */
    private void animateLegPair(
        final ModelPart upper,
        final ModelPart middle,
        final ModelPart lower,
        final boolean leftSide,
        final float limbSwing,
        final float limbSwingAmount,
        final float phase)
    {
        final float direction = leftSide ? 1.0F : -1.0F;
        final float swing = Mth.cos(limbSwing * 0.9F + phase) * limbSwingAmount;
        final float lift = Mth.sin(limbSwing * 0.9F + phase) * limbSwingAmount;

        upper.xRot += swing * 0.35F;
        upper.yRot += direction * (0.14F + swing * 0.16F);
        upper.zRot += direction * (0.10F + lift * 0.18F);

        middle.zRot += direction * (-0.24F - swing * 0.36F);
        middle.yRot += direction * (lift * 0.08F);

        lower.zRot += direction * (0.18F + swing * 0.26F);
        lower.xRot += -lift * 0.10F;
    }

    /**
     * Animates the tail of the model according to the given parameters.
     * 
     * The tail is divided into three segments, each with its own animation.
     * The first segment is the most active, with the most movement occurring when the entity is attacking.
     * The second and third segments follow suit, but with less movement.
     *
     * @param ageInTicks The age of the entity in ticks.
     * @param netHeadYaw The yaw of the entity's head.
     * @param walkAmount The amount of movement in the entity's walk animation.
     * @param aggressive Whether the entity is aggressive.
     * @param attackProgress The progress of the entity's attack animation.
     */
    private void animateTail(
        final float ageInTicks,
        final float netHeadYaw,
        final float walkAmount,
        final boolean aggressive,
        final float attackProgress)
    {
        final float idleWave = ageInTicks * 0.08F;
        final float poised = aggressive ? 1.0F : 0.0F;
        final float attackArc = Mth.sin(attackProgress * Mth.PI);
        final float attackDrive = attackProgress * attackProgress;
        final float yawTrack = Mth.clamp(netHeadYaw * ((float) Math.PI / 180.0F), -0.45F, 0.45F) * (0.15F + attackArc * 0.20F);

        this.tail_one.xRot += -0.20F - poised * 0.22F - attackArc * 0.95F + Mth.cos(idleWave) * 0.04F + walkAmount * 0.05F;
        this.tail_one.yRot += yawTrack * 0.35F + Mth.sin(idleWave) * 0.04F;

        this.tail_two.xRot += -0.12F - poised * 0.18F - attackArc * 0.70F + Mth.cos(idleWave + 0.55F) * 0.05F;
        this.tail_two.yRot += yawTrack * 0.55F + Mth.sin(idleWave + 0.55F) * 0.06F;

        this.tail_three.xRot += 0.22F + poised * 0.18F - attackDrive * 1.10F + Mth.cos(idleWave + 1.10F) * 0.06F;
        this.tail_three.yRot += yawTrack * 0.80F + Mth.sin(idleWave + 1.10F) * 0.08F;

        this.stalk0.xRot += 0.36F + poised * 0.18F - attackDrive * 0.80F + Mth.cos(idleWave + 1.55F) * 0.08F;
        this.stalk0.yRot += yawTrack + Mth.sin(idleWave + 1.55F) * 0.08F;

        this.stalk0_seg1.xRot += 0.24F + poised * 0.20F - attackDrive * 0.70F + Mth.cos(idleWave + 1.95F) * 0.09F;
        this.stalk0_seg1.yRot += yawTrack * 1.10F + Mth.sin(idleWave + 1.95F) * 0.10F;

        this.stalk0_seg2.xRot += 0.18F + poised * 0.18F - attackDrive * 0.58F + Mth.cos(idleWave + 2.35F) * 0.10F;
        this.stalk0_seg2.yRot += yawTrack * 1.20F + Mth.sin(idleWave + 2.35F) * 0.11F;

        this.stalk0_seg3.xRot += 0.12F + poised * 0.14F - attackDrive * 0.42F + Mth.cos(idleWave + 2.75F) * 0.11F;
        this.stalk0_seg3.yRot += yawTrack * 1.30F + Mth.sin(idleWave + 2.75F) * 0.12F;

        if (attackProgress > 0.0F)
        {
            this.stalk0_seg3.zRot += Mth.sin(attackProgress * Mth.PI * 2.0F) * 0.10F;
        }
    }

    /**
     * Applies the maw chomp pose to the model, based on the given age in ticks, aggressive status, and attack progress.
     * 
     * The maw chomp pose is a combination of idle chewing, stalking, and attacking animations.
     * The idle chewing animation is a sinusoidal movement that occurs on the Y axis of the model.
     * The stalking animation is a sinusoidal movement that occurs on the Y axis of the model, with a larger amplitude than the idle chewing animation.
     * The attacking animation is a snap-like movement that occurs on the Y axis of the model.
     * 
     * The maw chomp pose is combined by taking the maximum of the passive chewing animation and the attacking animation.
     * The resulting pose is then applied to the model by rotating the sides of the maw and the body of the model.
     * 
     * @param ageInTicks The age of the entity in ticks.
     * @param aggressive Whether the entity is aggressive.
     * @param attackProgress The progress of the entity's attack animation.
     */
    private void applyMawChompPose(
        final float ageInTicks,
        final boolean aggressive,
        final float attackProgress)
    {
        final float idleChewPhase = 0.5F + 0.5F * Mth.sin(ageInTicks * 0.06F);
        final float idleChew = 0.18F + 0.82F * idleChewPhase;
        final float stalkingChew = aggressive
            ? 0.12F + 0.18F * (0.5F + 0.5F * Mth.sin(ageInTicks * 0.14F + 0.7F))
            : 0.0F;
        final float passiveChomp = Mth.clamp(idleChew + stalkingChew, 0.0F, 1.0F);
        final float attackSnap = Mth.sin(attackProgress * Mth.PI);
        final float chompAmount = Math.max(passiveChomp, attackSnap);

        final float closeYaw = chompAmount * 0.42F;
        this.side_one.yRot += closeYaw;
        this.side_two.yRot -= closeYaw;

        final float lateralSweep = Mth.sin(ageInTicks * 0.08F) * 0.06F;
        final float jawTwist = chompAmount * 0.045F;
        this.side_one.zRot += jawTwist;
        this.side_two.zRot -= jawTwist;
        this.side_one.x += lateralSweep * chompAmount;
        this.side_two.x += lateralSweep * chompAmount;

        this.maw.xRot += chompAmount * 0.045F;
        this.body.xRot -= chompAmount * 0.02F;
    }

    /**
     * Renders this model to the given vertex consumer, using the given pose stack,
     * packed light, packed overlay, and RGBA values.
     *
     * @param poseStack the pose stack to use for rendering
     * @param vertexConsumer the vertex consumer to render to
     * @param packedLight the packed light value to use for rendering
     * @param packedOverlay the packed overlay value to use for rendering
     * @param color the RGBA value to use for rendering
     */
    @Override
    public void renderToBuffer(final @Nonnull PoseStack poseStack, final @Nonnull VertexConsumer vertexConsumer, final int packedLight, final int packedOverlay, final int color)
    {
        this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }
}
