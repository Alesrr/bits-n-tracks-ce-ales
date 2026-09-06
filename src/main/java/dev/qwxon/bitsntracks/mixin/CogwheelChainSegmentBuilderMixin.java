package dev.qwxon.bitsntracks.mixin;

import com.kipti.bnb.content.kinetics.cogwheel_chain.graph.RenderedChainPathNode;
import com.kipti.bnb.content.kinetics.cogwheel_chain.segment.CogwheelChainSegment.SegmentType;
import com.kipti.bnb.content.kinetics.cogwheel_chain.segment.CogwheelChainSegmentBuilder;
import dev.qwxon.bitsntracks.access.BntRunShapeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    value = {CogwheelChainSegmentBuilder.class},
    remap = false
)
public class CogwheelChainSegmentBuilderMixin {
    @Inject(
        method = {"determineSegmentType"},
        at = {@At("HEAD")},
        cancellable = true
    )
    private static void bnt$typeRunShapeAsRun(
        RenderedChainPathNode nodeA, RenderedChainPathNode nodeB, CallbackInfoReturnable<SegmentType> cir) {
        if (bnt$isRunShape(nodeA) || bnt$isRunShape(nodeB)) {
            cir.setReturnValue(SegmentType.BETWEEN_NODES);
        }
    }

    private static boolean bnt$isRunShape(RenderedChainPathNode node) {
        return (Object)node instanceof BntRunShapeNode shape && shape.bnt$isRunShape();
    }
}
