package com.deathfrog.salvationmod.client.render.model;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.salvationmod.SalvationMod;
import com.deathfrog.salvationmod.entity.VoraxianMawEntity;
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

public class VoraxianMawModel<T extends Mob> extends EntityModel<T>
{
    @SuppressWarnings("null")
    @Nonnull public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(SalvationMod.MODID, "voraxian_maw");
    
    @Nonnull public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(LAYER, "main");

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart eyes;
	private final ModelPart left_eye;
	private final ModelPart right_eye;
	private final ModelPart maw;
	private final ModelPart side_one;
	private final ModelPart side_two;
	private final ModelPart stalk0;
	private final ModelPart stalk0_seg1;
	private final ModelPart stalk0_seg2;
	private final ModelPart stalk0_seg3;
	private final ModelPart stalk0_orb;

    public VoraxianMawModel(final ModelPart bakedRoot)
    {
		this.root = bakedRoot.getChild("root");
		this.body = this.root.getChild("body");
		this.eyes = this.body.getChild("eyes");
		this.left_eye = this.eyes.getChild("left_eye");
		this.right_eye = this.eyes.getChild("right_eye");
		this.maw = this.body.getChild("maw");
		this.side_one = this.maw.getChild("side_one");
		this.side_two = this.maw.getChild("side_two");
		this.stalk0 = this.body.getChild("stalk0");
		this.stalk0_seg1 = this.stalk0.getChild("stalk0_seg1");
		this.stalk0_seg2 = this.stalk0_seg1.getChild("stalk0_seg2");
		this.stalk0_seg3 = this.stalk0_seg2.getChild("stalk0_seg3");
		this.stalk0_orb = this.stalk0_seg3.getChild("stalk0_orb");
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

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 15.6667F, -0.4667F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 40).addBox(-6.0F, -3.6667F, -5.5333F, 12.0F, 12.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(-7.0F, -4.6667F, 1.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(104, 52).addBox(1.0F, -4.6667F, 1.4667F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyes = body.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(-4.0F, -4.0F, 0.0F));

		PartDefinition left_eye = eyes.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(25, 0).addBox(-2.5F, -2.5F, -1.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(10.5F, 0.8333F, -6.5333F));

		PartDefinition right_eye = eyes.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(24, 0).addBox(-2.5F, -2.5F, -1.0F, 5.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 0.8333F, -6.5333F));

		PartDefinition maw = body.addOrReplaceChild("maw", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition side_one = maw.addOrReplaceChild("side_one", CubeListBuilder.create().texOffs(0, 47).addBox(-1.5F, -3.5F, -5.0F, 3.0F, 7.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -3.4F, -3.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -3.4F, -1.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -3.4F, 0.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, -3.4F, 2.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, 2.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, 0.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, -1.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(-3.0F, 2.4F, -3.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 3.8333F, -8.5333F, 0.0F, -0.3927F, 0.0F));

		PartDefinition side_two = maw.addOrReplaceChild("side_two", CubeListBuilder.create().texOffs(0, 47).addBox(-1.5F, -3.5F, -5.0F, 3.0F, 7.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, -2.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, -0.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, 1.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -3.4F, 1.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -3.4F, -0.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -3.4F, -2.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, -3.4F, -4.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(1, 1).addBox(1.0F, 2.4F, -4.9F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, 3.8333F, -8.5333F, 0.0F, 0.3927F, 0.0F));

		PartDefinition stalk0 = body.addOrReplaceChild("stalk0", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, -0.5F, -0.5F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(1.0F, 2.8333F, 5.9667F));

		PartDefinition stalk0_seg1 = stalk0.addOrReplaceChild("stalk0_seg1", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, 2.0F, 3.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.5F, 0.5F));

		PartDefinition stalk0_seg2 = stalk0_seg1.addOrReplaceChild("stalk0_seg2", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, 6.0F, 7.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk0_seg3 = stalk0_seg2.addOrReplaceChild("stalk0_seg3", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, 10.0F, 11.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition stalk0_orb = stalk0_seg3.addOrReplaceChild("stalk0_orb", CubeListBuilder.create().texOffs(87, 44).addBox(-2.0F, 13.0F, 15.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -4.0F, 0.0F));

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

        LivingEntity target = entity.getTarget();

        final boolean hasTarget = target != null && target.isAlive();
        final float attackChompProgress = entity instanceof VoraxianMawEntity mawEntity
            ? mawEntity.getAttackChompProgress()
            : 0.0F;
        final float threatChompProgress = entity instanceof VoraxianMawEntity mawEntity
            ? mawEntity.getThreatChompProgress()
            : 0.0F;
        final boolean aggressive = attackChompProgress > 0.0F;

        /*
        * --------------------------------
        * Tail / stalk lash
        * --------------------------------
        *
        * At rest:
        * - gentle swaying wave
        *
        * Attacking:
        * - faster, wider lash
        * - a little extra twist on the orb
        */
        final float idleSpeed = 0.08F;
        final float idleAmpYaw = 0.10F;
        final float idleAmpPitch = 0.04F;

        final float attackSpeed = 0.42F;
        final float attackAmpYaw = 0.38F;
        final float attackAmpPitch = 0.14F;

        final float tailSpeed = aggressive ? attackSpeed : idleSpeed;
        final float tailAmpYaw = aggressive ? attackAmpYaw : idleAmpYaw;
        final float tailAmpPitch = aggressive ? attackAmpPitch : idleAmpPitch;

        // Build a traveling wave down the segmented stalk.
        this.stalk0.yRot += Mth.sin(ageInTicks * tailSpeed) * tailAmpYaw;
        this.stalk0.xRot += Mth.cos(ageInTicks * tailSpeed * 0.85F) * tailAmpPitch;

        this.stalk0_seg1.yRot += Mth.sin(ageInTicks * tailSpeed + 0.55F) * (tailAmpYaw * 1.15F);
        this.stalk0_seg1.xRot += Mth.cos(ageInTicks * tailSpeed * 0.85F + 0.55F) * (tailAmpPitch * 1.10F);

        this.stalk0_seg2.yRot += Mth.sin(ageInTicks * tailSpeed + 1.10F) * (tailAmpYaw * 1.30F);
        this.stalk0_seg2.xRot += Mth.cos(ageInTicks * tailSpeed * 0.85F + 1.10F) * (tailAmpPitch * 1.20F);

        this.stalk0_seg3.yRot += Mth.sin(ageInTicks * tailSpeed + 1.70F) * (tailAmpYaw * 1.45F);
        this.stalk0_seg3.xRot += Mth.cos(ageInTicks * tailSpeed * 0.85F + 1.70F) * (tailAmpPitch * 1.30F);

        this.stalk0_orb.yRot += Mth.sin(ageInTicks * tailSpeed + 2.25F) * (tailAmpYaw * 1.70F);
        this.stalk0_orb.xRot += Mth.cos(ageInTicks * tailSpeed * 0.85F + 2.25F) * (tailAmpPitch * 1.45F);

        if (aggressive)
        {
            // Extra agitation while attacking.
            this.stalk0_orb.zRot += Mth.sin(ageInTicks * 0.95F) * 0.10F;
        }

        /*
        * --------------------------------
        * Eyes subtly track the target
        * --------------------------------
        */
        if (hasTarget && target != null)
        {
            Vec3 fromEyes = entity.getEyePosition();
            Vec3 toTarget = target.getEyePosition().subtract(NullnessBridge.assumeNonnull(fromEyes));

            double flatDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
            float targetYaw = (float)(Mth.atan2(toTarget.z, toTarget.x) * (180F / Math.PI)) - 90F;
            float targetPitch = (float)(-(Mth.atan2(toTarget.y, flatDist) * (180F / Math.PI)));

            float relYaw = Mth.wrapDegrees(targetYaw - entity.getYRot());
            float relPitch = targetPitch;

            float eyeYaw = Mth.clamp(relYaw * ((float)Math.PI / 180F), -0.22F, 0.22F);
            float eyePitch = Mth.clamp(relPitch * ((float)Math.PI / 180F), -0.15F, 0.15F);

            this.left_eye.yRot += eyeYaw;
            this.left_eye.xRot += eyePitch;

            this.right_eye.yRot += eyeYaw;
            this.right_eye.xRot += eyePitch;
        }
        else
        {
            // Small idle eye drift so the face never feels frozen.
            float eyeDriftYaw = Mth.sin(ageInTicks * 0.06F) * 0.03F;
            float eyeDriftPitch = Mth.cos(ageInTicks * 0.05F) * 0.02F;

            this.left_eye.yRot += eyeDriftYaw;
            this.left_eye.xRot += eyeDriftPitch;

            this.right_eye.yRot += eyeDriftYaw;
            this.right_eye.xRot += eyeDriftPitch;
        }

        this.applyMawChompPose(ageInTicks, hasTarget, threatChompProgress, attackChompProgress);
    }

    private void applyMawChompPose(
        final float ageInTicks,
        final boolean hasTarget,
        final float threatChompProgress,
        final float attackChompProgress)
    {
        final float idleChewPhase = 0.5F + 0.5F * Mth.sin(ageInTicks * 0.06F);
        final float idleChew = 0.18F + 0.82F * idleChewPhase;
        final float stalkingChew = hasTarget
            ? 0.12F + 0.18F * (0.5F + 0.5F * Mth.sin(ageInTicks * 0.14F + 0.7F))
            : 0.0F;
        final float passiveChomp = Mth.clamp(idleChew + stalkingChew, 0.0F, 1.0F);
        final float threatChew = Mth.sin(threatChompProgress * Mth.PI);
        final float attackSnap = Mth.sin(attackChompProgress * Mth.PI);
        final float chompAmount = Math.max(passiveChomp, Math.max(threatChew, attackSnap));

        // The baked pose is about 0.3927 rad open on each side, so 0.42F fully closes with a small decisive overlap.
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
