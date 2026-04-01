package com.deathfrog.salvationmod.client.render.model;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class VoraxianOverlordModel<T extends Mob> extends EntityModel<T>
{
    @SuppressWarnings("null")
    @Nonnull public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "voraxian_overlord");
    
    @Nonnull public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(LAYER, "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart eyes;
	private final ModelPart left_eye;
	private final ModelPart right_eye;
	private final ModelPart maw;
	private final ModelPart side_one;
	private final ModelPart side_two;
	private final ModelPart rightarm;
	private final ModelPart right_bicep;
	private final ModelPart right_forearm;
	private final ModelPart right_hand;
	private final ModelPart rh_top;
	private final ModelPart rh_bottom;
	private final ModelPart leftarm;
	private final ModelPart left_bicep;
	private final ModelPart left_forearm;
	private final ModelPart left_hand;
	private final ModelPart lh_top;
	private final ModelPart lh_bottom;
	private final ModelPart trunk;
	private final ModelPart down_one;
	private final ModelPart down_two;
	private final ModelPart down_three;

    public VoraxianOverlordModel(final ModelPart bakedRoot)
    {
		this.root = bakedRoot.getChild("root");
		this.body = this.root.getChild("body");
		this.eyes = this.body.getChild("eyes");
		this.left_eye = this.eyes.getChild("left_eye");
		this.right_eye = this.eyes.getChild("right_eye");
		this.maw = this.body.getChild("maw");
		this.side_one = this.maw.getChild("side_one");
		this.side_two = this.maw.getChild("side_two");
		this.rightarm = this.body.getChild("rightarm");
		this.right_bicep = this.rightarm.getChild("right_bicep");
		this.right_forearm = this.right_bicep.getChild("right_forearm");
		this.right_hand = this.right_forearm.getChild("right_hand");
		this.rh_top = this.right_hand.getChild("rh_top");
		this.rh_bottom = this.right_hand.getChild("rh_bottom");
		this.leftarm = this.body.getChild("leftarm");
		this.left_bicep = this.leftarm.getChild("left_bicep");
		this.left_forearm = this.left_bicep.getChild("left_forearm");
		this.left_hand = this.left_forearm.getChild("left_hand");
		this.lh_top = this.left_hand.getChild("lh_top");
		this.lh_bottom = this.left_hand.getChild("lh_bottom");
		this.trunk = this.body.getChild("trunk");
		this.down_one = this.trunk.getChild("down_one");
		this.down_two = this.down_one.getChild("down_two");
		this.down_three = this.down_two.getChild("down_three");
    }

    /**
     * Creates the body layer definition for the Voraxian entity.
     *
     * @return The body layer definition for the Voraxian entity.
     */
    @SuppressWarnings({"unused", "null"})
    public static LayerDefinition createBodyLayer()
    {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(6.0F, -10.3333F, -0.4667F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 39).addBox(-18.0F, -3.6667F, -5.5333F, 24.0F, 12.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(0, 39).addBox(-14.0F, -18.6667F, -5.5333F, 16.0F, 10.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(6, 22).addBox(-12.0F, -16.6667F, -7.5333F, 12.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(104, 51).addBox(-15.0F, -19.6667F, 1.4667F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 51).addBox(-3.0F, -19.6667F, 1.4667F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(28, 6).addBox(-15.0F, -19.6667F, -6.5333F, 18.0F, 6.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(-19.0F, -4.6667F, 1.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(1.0F, -4.6667F, 1.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyes = body.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(-6.0F, -21.1667F, -7.5333F));

		PartDefinition left_eye = eyes.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(24, -1).addBox(-3.5F, -2.5F, -1.0F, 5.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(14.5F, 0.0F, 0.0F));

		PartDefinition stalk_r1 = left_eye.addOrReplaceChild("stalk_r1", CubeListBuilder.create().texOffs(71, 0).addBox(-8.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 3.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition right_eye = eyes.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(23, -1).addBox(-1.5F, -2.5F, -1.0F, 5.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-14.5F, 0.0F, 0.0F));

		PartDefinition stalk_r2 = right_eye.addOrReplaceChild("stalk_r2", CubeListBuilder.create().texOffs(71, 0).addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 3.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition maw = body.addOrReplaceChild("maw", CubeListBuilder.create(), PartPose.offset(-6.0F, 5.8333F, -13.5333F));

		PartDefinition side_one = maw.addOrReplaceChild("side_one", CubeListBuilder.create().texOffs(0, 45).addBox(-1.5F, -5.5F, -5.0F, 3.0F, 9.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -5.4F, -3.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -5.4F, -1.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -5.4F, 0.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -5.4F, 2.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, 2.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, 0.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, -1.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, -3.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 0.0F, 0.0F, 0.0F, -0.3927F, 0.0F));

		PartDefinition side_two = maw.addOrReplaceChild("side_two", CubeListBuilder.create().texOffs(0, 45).addBox(-1.5F, -5.5F, -5.0F, 3.0F, 9.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, -2.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, -0.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, 1.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -5.4F, 1.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -5.4F, -0.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -5.4F, -2.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -5.4F, -4.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, -4.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 0.0F, 0.0F, 0.0F, 0.3927F, 0.0F));

		PartDefinition rightarm = body.addOrReplaceChild("rightarm", CubeListBuilder.create().texOffs(104, 52).addBox(-8.6667F, -4.3333F, -1.6667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(8, 44).addBox(-7.6667F, -3.3333F, -4.6667F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(-18.3333F, 4.6667F, 1.1333F));

		PartDefinition right_bicep = rightarm.addOrReplaceChild("right_bicep", CubeListBuilder.create(), PartPose.offset(-7.9534F, 3.7523F, -0.3333F));

		PartDefinition rightside_bicep_r1 = right_bicep.addOrReplaceChild("rightside_bicep_r1", CubeListBuilder.create().texOffs(8, 44).addBox(-7.537F, -3.3003F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.3566F, 0.2928F, -0.3333F, 0.0F, 0.0F, -0.3927F));

		PartDefinition right_forearm = right_bicep.addOrReplaceChild("right_forearm", CubeListBuilder.create(), PartPose.offset(-3.1783F, 7.1036F, -0.3333F));

		PartDefinition rightside_forearm_r1 = right_forearm.addOrReplaceChild("rightside_forearm_r1", CubeListBuilder.create().texOffs(8, 44).addBox(-5.5307F, -0.3045F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5349F, -0.8108F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition right_hand = right_forearm.addOrReplaceChild("right_hand", CubeListBuilder.create(), PartPose.offset(2.7304F, 8.1585F, 0.0F));

		PartDefinition rh_top = right_hand.addOrReplaceChild("rh_top", CubeListBuilder.create().texOffs(116, 12).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(116, 12).addBox(-1.0F, 0.0F, 1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7346F, -2.3478F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition rh_bottom = right_hand.addOrReplaceChild("rh_bottom", CubeListBuilder.create(), PartPose.offset(-1.7346F, -0.6522F, 0.0F));

		PartDefinition claw_one_r1 = rh_bottom.addOrReplaceChild("claw_one_r1", CubeListBuilder.create().texOffs(116, 12).addBox(-1.2242F, -0.8457F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition leftarm = body.addOrReplaceChild("leftarm", CubeListBuilder.create().texOffs(104, 52).addBox(2.6667F, -4.3333F, -1.6667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(8, 44).addBox(-0.3333F, -3.3333F, -4.6667F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(6.3333F, 4.6667F, 1.1333F));

		PartDefinition left_bicep = leftarm.addOrReplaceChild("left_bicep", CubeListBuilder.create(), PartPose.offset(7.1317F, 3.8559F, -0.6667F));

		PartDefinition left_bicep_r1 = left_bicep.addOrReplaceChild("left_bicep_r1", CubeListBuilder.create().texOffs(8, 44).addBox(0.0F, -2.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5349F, -1.1892F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition left_forearm = left_bicep.addOrReplaceChild("left_forearm", CubeListBuilder.create(), PartPose.offset(2.75F, 6.5F, 0.0F));

		PartDefinition left_forearm_r1 = left_forearm.addOrReplaceChild("left_forearm_r1", CubeListBuilder.create().texOffs(8, 44).addBox(-2.4693F, -0.3045F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7151F, -0.3108F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition left_hand = left_forearm.addOrReplaceChild("left_hand", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.7151F, 8.3108F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition lh_top = left_hand.addOrReplaceChild("lh_top", CubeListBuilder.create().texOffs(116, 12).addBox(-1.0F, 0.0F, -3.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(116, 12).addBox(-1.0F, 0.0F, 1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, -2.0F, 0.0F));

		PartDefinition lh_bottom = left_hand.addOrReplaceChild("lh_bottom", CubeListBuilder.create().texOffs(116, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(1.5F, -1.0F, 0.0F));

		PartDefinition trunk = body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(104, 52).addBox(-13.0F, -2.6667F, 5.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(-5.0F, -2.6667F, 5.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(8, 31).addBox(-12.0F, -1.6667F, -9.5333F, 12.0F, 12.0F, 20.0F, new CubeDeformation(0.0F))
		.texOffs(20, 43).addBox(-10.0F, -9.6667F, -3.5333F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition down_one = trunk.addOrReplaceChild("down_one", CubeListBuilder.create().texOffs(10, 35).addBox(-11.0F, 10.3333F, -7.5333F, 10.0F, 8.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition down_two = down_one.addOrReplaceChild("down_two", CubeListBuilder.create().texOffs(18, 39).addBox(-9.0F, 18.3333F, -5.5333F, 6.0F, 8.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition down_three = down_two.addOrReplaceChild("down_three", CubeListBuilder.create().texOffs(105, 49).addBox(-7.0F, 26.3333F, -3.5333F, 2.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

    /**
     * Set up animations for a given entity.
     *
     * @param entity      the entity to animate
     * @param limbSwing  the current limb swing amount
     * @param limbSwingAmount the total limb swing amount
     * @param ageInTicks   the age of the entity in ticks
     * @param netHeadYaw    the yaw of the entity's head
     * @param headPitch    the pitch of the entity's head
     */
    @Override
    public void setupAnim(
        final @Nonnull T entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch)
    {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        final LivingEntity target = entity.getTarget();
        final boolean hasTarget = target != null && target.isAlive();
        final boolean aggressive = entity.isAggressive();
        final float walkAmount = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        final float partialTick = ageInTicks - entity.tickCount;
        final float attackProgress = Mth.clamp(entity.getAttackAnim(partialTick), 0.0F, 1.0F);

        this.animateBody(ageInTicks, walkAmount, aggressive);
        this.animateEyes(entity, ageInTicks, target, hasTarget);
        this.animateTrunk(ageInTicks, walkAmount, attackProgress);
        this.animateArm(this.rightarm, this.right_bicep, this.right_forearm, this.right_hand,
            this.rh_top, this.rh_bottom, false, limbSwing, walkAmount, ageInTicks, attackProgress, aggressive);
        this.animateArm(this.leftarm, this.left_bicep, this.left_forearm, this.left_hand,
            this.lh_top, this.lh_bottom, true, limbSwing, walkAmount, ageInTicks, attackProgress, aggressive);
        this.applyMawChompPose(ageInTicks, hasTarget, attackProgress);
    }

    private void animateBody(
        final float ageInTicks,
        final float walkAmount,
        final boolean aggressive)
    {
        this.body.y += Mth.sin(ageInTicks * 0.10F) * (0.7F + walkAmount * 0.4F);
        this.body.xRot += Mth.cos(ageInTicks * 0.05F) * 0.03F + walkAmount * 0.03F;
        this.body.yRot += Mth.sin(ageInTicks * 0.04F) * (aggressive ? 0.02F : 0.04F);
        this.body.zRot += Mth.sin(ageInTicks * 0.06F) * 0.025F;
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
            final Vec3 toTarget = target.getEyePosition().subtract(NullnessBridge.assumeNonnull(fromEyes));

            final double flatDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            final float targetYaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180F / Math.PI)) - 90F;
            final float targetPitch = (float) (-(Mth.atan2(toTarget.y, flatDist) * (180F / Math.PI)));

            final float relYaw = Mth.wrapDegrees(targetYaw - entity.getYRot());
            final float eyeYaw = Mth.clamp(relYaw * ((float) Math.PI / 180F), -0.18F, 0.18F);
            final float eyePitch = Mth.clamp(targetPitch * ((float) Math.PI / 180F), -0.12F, 0.12F);

            this.left_eye.yRot += eyeYaw;
            this.left_eye.xRot += eyePitch;
            this.right_eye.yRot += eyeYaw;
            this.right_eye.xRot += eyePitch;
            return;
        }

        final float eyeDriftYaw = Mth.sin(ageInTicks * 0.06F) * 0.025F;
        final float eyeDriftPitch = Mth.cos(ageInTicks * 0.05F) * 0.02F;

        this.left_eye.yRot += eyeDriftYaw;
        this.left_eye.xRot += eyeDriftPitch;
        this.right_eye.yRot += eyeDriftYaw;
        this.right_eye.xRot += eyeDriftPitch;
    }

    private void animateTrunk(
        final float ageInTicks,
        final float walkAmount,
        final float attackProgress)
    {
        final float trunkTime = ageInTicks * 0.08F;
        final float attackArc = Mth.sin(attackProgress * Mth.PI);

        this.trunk.yRot += Mth.sin(trunkTime) * 0.05F;
        this.trunk.xRot += Mth.cos(trunkTime * 0.9F) * 0.02F;
        this.trunk.zRot += Mth.sin(trunkTime * 0.8F) * 0.08F;

        this.down_one.yRot += Mth.sin(trunkTime + 0.55F) * 0.04F;
        this.down_one.xRot += Mth.cos(trunkTime * 0.95F + 0.55F) * 0.02F;
        this.down_one.zRot += Mth.sin(trunkTime + 0.55F) * (0.12F + walkAmount * 0.03F);

        this.down_two.yRot += Mth.sin(trunkTime + 1.10F) * 0.05F;
        this.down_two.xRot += Mth.cos(trunkTime * 1.02F + 1.10F) * 0.025F;
        this.down_two.zRot += Mth.sin(trunkTime + 1.10F) * (0.16F + walkAmount * 0.04F);

        this.down_three.yRot += Mth.sin(trunkTime + 1.65F) * 0.06F;
        this.down_three.xRot += Mth.cos(trunkTime * 1.08F + 1.65F) * 0.03F;
        this.down_three.zRot += Mth.sin(trunkTime + 1.65F) * (0.20F + walkAmount * 0.05F + attackArc * 0.04F);
    }

    private void animateArm(
        final ModelPart shoulder,
        final ModelPart bicep,
        final ModelPart forearm,
        final ModelPart hand,
        final ModelPart clawTop,
        final ModelPart clawBottom,
        final boolean leftSide,
        final float limbSwing,
        final float walkAmount,
        final float ageInTicks,
        final float attackProgress,
        final boolean aggressive)
    {
        final float side = leftSide ? 1.0F : -1.0F;
        final float walkPhase = leftSide ? 0.9F : 0.0F;
        final float idlePhase = leftSide ? 1.7F : 0.55F;
        final float armSwing = Mth.cos(limbSwing * 0.55F + walkPhase) * walkAmount;
        final float idleFlex = Mth.sin(ageInTicks * 0.07F + idlePhase);
        final float attackArc = Mth.sin(attackProgress * Mth.PI);
        final float attackDrive = attackProgress * attackProgress;
        final float lift = 0.35F + walkAmount * 0.18F + (0.5F + 0.5F * idleFlex) * 0.10F + attackArc * 0.26F;

        shoulder.xRot += -0.18F + armSwing * 0.06F + idleFlex * 0.03F - attackArc * 0.10F;
        shoulder.yRot += side * (0.10F + idleFlex * 0.03F + attackArc * 0.06F);
        shoulder.zRot += side * (0.04F + armSwing * 0.08F + idleFlex * 0.04F);

        bicep.xRot += -0.65F + armSwing * 0.10F + idleFlex * 0.04F - attackArc * 0.08F;
        bicep.yRot += side * (idleFlex * 0.03F);
        bicep.zRot += side * (-0.32F + lift);

        forearm.xRot += 0.48F - armSwing * 0.10F + Mth.cos(ageInTicks * 0.09F + idlePhase) * 0.04F - attackDrive * 0.35F;
        forearm.yRot += side * (Mth.sin(ageInTicks * 0.08F + idlePhase) * 0.04F + attackArc * 0.08F);
        forearm.zRot += side * (-0.18F + lift * 0.82F - armSwing * 0.05F);

        hand.xRot += -0.06F + attackArc * 0.12F;
        hand.yRot += side * (Mth.sin(ageInTicks * 0.10F + idlePhase) * 0.03F);
        hand.zRot += side * (-0.06F + lift * 0.46F + Mth.cos(ageInTicks * 0.11F + idlePhase) * 0.03F);

        final float idleClaw = 0.05F + (0.5F + 0.5F * Mth.sin(ageInTicks * 0.12F + idlePhase)) * 0.08F;
        final float poisedClaw = aggressive ? 0.03F : 0.0F;
        final float snapClaw = attackArc * 0.34F;
        final float clawClose = Math.max(idleClaw + poisedClaw, snapClaw);

        clawTop.zRot += side * clawClose;
        clawBottom.zRot -= side * clawClose;
    }

    private void applyMawChompPose(
        final float ageInTicks,
        final boolean hasTarget,
        final float attackProgress)
    {
        final float idleChewPhase = 0.5F + 0.5F * Mth.sin(ageInTicks * 0.06F);
        final float idleChew = 0.18F + 0.82F * idleChewPhase;
        final float stalkingChew = hasTarget
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
