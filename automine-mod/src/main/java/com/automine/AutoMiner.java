package com.automine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Client-side mining bot. It behaves like a player: it switches to the best hotbar tool,
 * smoothly turns its head toward a block, walks (and jumps) toward it, and holds the
 * attack button until the block breaks.
 */
public class AutoMiner {
	public static boolean enabled = false;
	public static Block target = null;

	private static final Random RNG = new Random();
	private static final Map<BlockPos, Integer> SKIPPED = new HashMap<>();

	private static BlockPos current = null;
	private static int ticks = 0;
	private static int cooldown = 0;
	private static int searchCooldown = 0;
	private static int noTargetTicks = 0;
	private static int stuckTicks = 0;
	private static int aimTicks = 0;
	private static Vec3d lastPos = Vec3d.ZERO;

	private static final int RADIUS = 20;
	private static final int VERTICAL = 10;

	public static void start(MinecraftClient mc) {
		if (target == null) {
			msg(mc, "§eSet a block first: §f/mine <block>§e (example: /mine red_sand)");
			return;
		}
		enabled = true;
		current = null;
		cooldown = 0;
		searchCooldown = 0;
		noTargetTicks = 0;
		stuckTicks = 0;
		aimTicks = 0;
		SKIPPED.clear();
		if (mc.player != null) lastPos = mc.player.getEntityPos();
		msg(mc, "§aAutomine on §7(mining " + target.getName().getString() + ")");
	}

	public static void stop(MinecraftClient mc, String message) {
		enabled = false;
		current = null;
		releaseKeys(mc);
		if (message != null) msg(mc, message);
	}

	private static void msg(MinecraftClient mc, String s) {
		if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
	}

	private static void releaseKeys(MinecraftClient mc) {
		mc.options.forwardKey.setPressed(false);
		mc.options.jumpKey.setPressed(false);
		mc.options.attackKey.setPressed(false);
		mc.options.sprintKey.setPressed(false);
	}

	public static void tick(MinecraftClient mc) {
		if (!enabled) return;
		ClientPlayerEntity p = mc.player;
		ClientWorld w = mc.world;
		if (p == null || w == null || target == null) {
			enabled = false;
			releaseKeys(mc);
			return;
		}
		if (mc.currentScreen != null) { // paused while a menu/inventory is open
			releaseKeys(mc);
			return;
		}
		ticks++;

		// safety: stop if we get hurt or are low on health
		if (p.hurtTime > 0 || p.getHealth() < 6f) {
			stop(mc, "§cAutomine bot stopped §7(you took damage)");
			return;
		}

		// expire temporary "skip this block" entries
		for (Iterator<Map.Entry<BlockPos, Integer>> it = SKIPPED.entrySet().iterator(); it.hasNext(); ) {
			if (it.next().getValue() < ticks) it.remove();
		}

		if (cooldown > 0) { // tiny human-like pause between blocks
			cooldown--;
			mc.options.attackKey.setPressed(false);
			mc.options.forwardKey.setPressed(false);
			return;
		}

		// is the current block still valid?
		if (current != null && (!w.getBlockState(current).isOf(target) || SKIPPED.containsKey(current))) {
			current = null;
			mc.options.attackKey.setPressed(false);
			cooldown = 1 + RNG.nextInt(4);
			return;
		}

		// find a new block
		if (current == null) {
			mc.options.forwardKey.setPressed(false);
			mc.options.jumpKey.setPressed(false);
			if (searchCooldown-- > 0) return;
			searchCooldown = 5;
			current = findTarget(w, p);
			if (current == null) {
				noTargetTicks += 5;
				if (noTargetTicks >= 100) {
					stop(mc, "§cAutomine bot stopped §7(no " + target.getName().getString() + " found nearby)");
				}
				return;
			}
			noTargetTicks = 0;
			stuckTicks = 0;
			aimTicks = 0;
			lastPos = p.getEntityPos();
		}

		BlockState state = w.getBlockState(current);
		selectTool(p, state);

		Vec3d eye = p.getEyePos();
		Vec3d aim = Vec3d.ofCenter(current);
		rotateToward(p, eye, aim);

		double reach = p.getBlockInteractionRange();
		double dist = eye.distanceTo(aim);
		boolean inReach = dist <= reach - 0.4;

		HitResult hit = p.raycast(reach, 1.0f, false);
		boolean looking = hit instanceof BlockHitResult bhr
				&& bhr.getType() == HitResult.Type.BLOCK
				&& bhr.getBlockPos().equals(current);

		if (inReach && looking) {
			// mine it
			mc.options.forwardKey.setPressed(false);
			mc.options.sprintKey.setPressed(false);
			mc.options.jumpKey.setPressed(false);
			mc.options.attackKey.setPressed(true);
			aimTicks = 0;
			stuckTicks = 0;
		} else if (inReach) {
			// still turning, or something is in the way
			mc.options.forwardKey.setPressed(false);
			mc.options.attackKey.setPressed(false);
			if (++aimTicks > 40) skip(current, 200);
		} else {
			// walk toward it like a player
			mc.options.attackKey.setPressed(false);
			mc.options.forwardKey.setPressed(true);
			mc.options.sprintKey.setPressed(dist > 6);
			boolean needJump = p.horizontalCollision || (aim.y - p.getY() > 1.5 && horizontalDist(p, aim) < 2.5);
			mc.options.jumpKey.setPressed(needJump && p.isOnGround());

			if (p.getEntityPos().squaredDistanceTo(lastPos) > 0.09) {
				lastPos = p.getEntityPos();
				stuckTicks = 0;
			} else if (++stuckTicks > 60) {
				skip(current, 600);
			}
		}
	}

	private static double horizontalDist(ClientPlayerEntity p, Vec3d v) {
		double dx = v.x - p.getX(), dz = v.z - p.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static void skip(BlockPos pos, int forTicks) {
		SKIPPED.put(pos, ticks + forTicks);
	}

	/** Smooth, slightly randomized head turning instead of snapping. */
	private static void rotateToward(ClientPlayerEntity p, Vec3d eye, Vec3d aim) {
		double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
		float targetYaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
		float targetPitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);

		float dYaw = MathHelper.wrapDegrees(targetYaw - p.getYaw());
		float dPitch = targetPitch - p.getPitch();

		float maxStep = 8f + RNG.nextFloat() * 10f;
		// ease out when close
		float yawStep = MathHelper.clamp(dYaw * 0.6f, -maxStep, maxStep);
		float pitchStep = MathHelper.clamp(dPitch * 0.6f, -maxStep, maxStep);

		p.setYaw(p.getYaw() + yawStep);
		p.setPitch(MathHelper.clamp(p.getPitch() + pitchStep, -90f, 90f));
	}

	/** Picks the hotbar slot that mines this block fastest (a shovel for sand/dirt/gravel...). */
	private static void selectTool(ClientPlayerEntity p, BlockState state) {
		PlayerInventory inv = p.getInventory();
		int best = inv.getSelectedSlot();
		float bestSpeed = usableSpeed(inv.getStack(best), state);
		for (int i = 0; i < 9; i++) {
			float s = usableSpeed(inv.getStack(i), state);
			if (s > bestSpeed + 0.01f) {
				bestSpeed = s;
				best = i;
			}
		}
		if (best != inv.getSelectedSlot()) inv.setSelectedSlot(best);
	}

	private static float usableSpeed(ItemStack stack, BlockState state) {
		// don't use a tool that is about to break
		if (stack.isDamageable() && stack.getMaxDamage() - stack.getDamage() <= 1) return 0f;
		return stack.getMiningSpeedMultiplier(state);
	}

	private static BlockPos findTarget(ClientWorld w, ClientPlayerEntity p) {
		BlockPos origin = p.getBlockPos();
		Vec3d eye = p.getEyePos();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				for (int y = -VERTICAL; y <= VERTICAL; y++) {
					m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
					BlockState s = w.getBlockState(m);
					if (!s.isOf(target)) continue;
					if (SKIPPED.containsKey(m)) continue;
					if (s.getHardness(w, m) < 0) continue; // unbreakable
					if (!exposed(w, m)) continue;
					double score = eye.squaredDistanceTo(Vec3d.ofCenter(m)) + y * y * 2.0;
					if (score < bestScore) {
						bestScore = score;
						best = m.toImmutable();
					}
				}
			}
		}
		return best;
	}

	/** Only target blocks that have at least one open side (so a player could actually hit them). */
	private static boolean exposed(ClientWorld w, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockPos n = pos.offset(d);
			if (w.getBlockState(n).getCollisionShape(w, n).isEmpty()) return true;
		}
		return false;
	}
}
