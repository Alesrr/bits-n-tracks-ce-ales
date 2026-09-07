package dev.qwxon.bitsntracks.physics;

import com.kipti.bnb.content.kinetics.cogwheel_chain.behaviour.CogwheelChainBehaviour;
import com.kipti.bnb.content.kinetics.cogwheel_chain.graph.CogwheelChain;
import com.kipti.bnb.content.kinetics.cogwheel_chain.graph.PathedCogwheelNode;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.qwxon.bitsntracks.access.BntChainGeometryRefresh;
import dev.qwxon.bitsntracks.access.KineticBlockEntityPhysicsAccess;
import dev.qwxon.bitsntracks.content.BntCogwheelPairing;
import dev.qwxon.bitsntracks.content.kinetics.cogwheel_chain.BntBeltLinks;
import dev.qwxon.bitsntracks.content.kinetics.cogwheel_chain.BntBeltSolver;
import dev.qwxon.bitsntracks.content.kinetics.cogwheel_chain.BntBeltTension;
import dev.qwxon.bitsntracks.content.kinetics.cogwheel_chain.BntChainGeometry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A loop out of length holds its drooping wheels up, lift only ever shortens a droop. */
public final class BntBeltHold {
    private static final double FLAT = 1.0E-6;

    private BntBeltHold() {
    }

    /** Lift the belt puts on one wheel, solved once per tick for the whole loop. */
    public static double at(Level level, KineticBlockEntity wheel) {
        if (!BntPhysicsTuning.isBeltHoldEnabled()
            || !(wheel instanceof KineticBlockEntityPhysicsAccess access)
            || level == null) {
            return 0.0;
        }

        long now = level.getGameTime();
        if (access.bnt$getBeltHoldTick() == now) {
            return access.bnt$getBeltHold();
        }

        access.bnt$setBeltHold(now, 0.0);
        BlockPos controllerPos = controllerPos(wheel);
        if (controllerPos != null) {
            solve(level, controllerPos, now);
        }
        return access.bnt$getBeltHold();
    }

    private static void solve(Level level, BlockPos controllerPos, long now) {
        List<PathedCogwheelNode> nodes = beltOrder(level, controllerPos);
        int count = nodes.size();
        for (int i = 0; i < count; i++) {
            if (level.getBlockEntity(controllerPos.offset(nodes.get(i).localPos()))
                instanceof KineticBlockEntityPhysicsAccess access) {
                access.bnt$setBeltHold(now, 0.0);
            }
        }
        if (count < 3) {
            return;
        }

        Axis axis = BntChainGeometry.sharedAxis(nodes);
        if (axis == null || axis == Axis.Y) {
            return;
        }

        int links = BntBeltLinks.at(level, controllerPos);
        if (links <= BntBeltLinks.UNSET) {
            return;
        }

        Level heldLevel = BntRadiusProvider.level();
        BlockPos heldOrigin = BntRadiusProvider.origin();
        try {
            BntRadiusProvider.setLevel(level);
            BntRadiusProvider.setOrigin(controllerPos);
            apply(level, controllerPos, nodes, axis, links, now);
        } finally {
            BntRadiusProvider.setLevel(heldLevel);
            BntRadiusProvider.setOrigin(heldOrigin);
        }
    }

    private static void apply(
        Level level, BlockPos controllerPos, List<PathedCogwheelNode> nodes, Axis axis, int links, long now
    ) {
        int count = nodes.size();
        Vec3[] centres = new Vec3[count];
        double[] drops = new double[count];
        double[] xs = new double[count];
        double[] ys = new double[count];
        double[] restXs = new double[count];
        double[] restYs = new double[count];
        double[] radii = new double[count];
        int[] sides = new int[count];

        for (int i = 0; i < count; i++) {
            PathedCogwheelNode node = nodes.get(i);
            BlockPos nodePos = controllerPos.offset(node.localPos());
            BlockState state = level.getBlockState(nodePos);
            Vec3 rest = nodePos.getCenter()
                .add(0.0, CogwheelSizeHelper.getVerticalOffset(state.getBlock()), 0.0)
                .add(BntCogwheelPairing.seamOffset(state));
            BlockEntity be = level.getBlockEntity(nodePos);
            if (be instanceof KineticBlockEntityPhysicsAccess access) {
                rest = rest.add(
                    access.bnt$getAlignmentOffsetX(), access.bnt$getAlignmentOffsetY(), access.bnt$getAlignmentOffsetZ());
            }
            drops[i] = be instanceof KineticBlockEntity kinetic
                ? Math.max(0.0, BntPhysicsEvents.getRawRenderExtension(kinetic, 1.0F))
                : 0.0;

            centres[i] = rest.subtract(0.0, drops[i], 0.0);
            xs[i] = BntChainGeometry.planarX(centres[i], axis);
            ys[i] = BntChainGeometry.planarY(centres[i], axis);
            restXs[i] = BntChainGeometry.planarX(rest, axis);
            restYs[i] = BntChainGeometry.planarY(rest, axis);
            radii[i] = BntChainGeometry.trackRadius(node);
            sides[i] = node.side();
        }

        double drooped = BntBeltSolver.beltLength(xs, ys, radii, sides);
        double resting = BntBeltSolver.beltLength(restXs, restYs, radii, sides);
        if (!Double.isFinite(drooped) || drooped >= Double.MAX_VALUE
            || !Double.isFinite(resting) || resting >= Double.MAX_VALUE) {
            return;
        }

        double allowance = BntBeltLinks.length(links) + BntBeltLinks.slackLength(BntBeltTension.at(level, controllerPos));
        double deficit = drooped - Math.max(allowance, resting);
        if (deficit <= 0.0) {
            return;
        }

        double travel = 0.0;
        for (double drop : drops) {
            travel += drop;
        }
        if (deficit > 2.0 * travel + FLAT) {
            return;
        }

        double[] gradient = new double[count];
        double weight = 0.0;
        for (int i = 0; i < count; i++) {
            Vec3 toPrevious = centres[(i - 1 + count) % count].subtract(centres[i]);
            Vec3 toNext = centres[(i + 1) % count].subtract(centres[i]);
            if (toPrevious.lengthSqr() < FLAT || toNext.lengthSqr() < FLAT) {
                continue;
            }
            gradient[i] = -(toPrevious.normalize().y + toNext.normalize().y);
            if (gradient[i] < 0.0) {
                weight += gradient[i] * gradient[i];
            }
        }
        if (weight < FLAT) {
            return;
        }

        double cap = BntPhysicsTuning.getBeltMaxHold();
        for (int i = 0; i < count; i++) {
            if (gradient[i] >= 0.0) {
                continue;
            }
            double lift = Math.min(-deficit * gradient[i] / weight, Math.min(cap, drops[i]));
            if (lift <= 0.0) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(controllerPos.offset(nodes.get(i).localPos()));
            if (be instanceof KineticBlockEntityPhysicsAccess access) {
                access.bnt$setBeltHold(now, lift);
            }
        }
    }

    private static List<PathedCogwheelNode> beltOrder(Level level, BlockPos controllerPos) {
        if (!(level.getBlockEntity(controllerPos) instanceof SmartBlockEntity smart)
            || !(smart.getBehaviour(CogwheelChainBehaviour.TYPE) instanceof CogwheelChainBehaviour behaviour)) {
            return List.of();
        }
        CogwheelChain chain = behaviour.getControlledChain();
        if (chain == null) {
            return List.of();
        }
        List<PathedCogwheelNode> latched = chain instanceof BntChainGeometryRefresh refreshable
            ? refreshable.bnt$latchedBeltOrder()
            : List.of();
        return latched.size() >= 2 ? latched : chain.getChainPathCogwheelNodes();
    }

    private static BlockPos controllerPos(KineticBlockEntity wheel) {
        CogwheelChainBehaviour behaviour = (CogwheelChainBehaviour)wheel.getBehaviour(CogwheelChainBehaviour.TYPE);
        if (behaviour == null || !behaviour.isPartOfChain()) {
            return null;
        }
        if (behaviour.getControlledChain() != null) {
            return wheel.getBlockPos();
        }
        return behaviour.getControllerOffset() == null ? null : wheel.getBlockPos().offset(behaviour.getControllerOffset());
    }
}
