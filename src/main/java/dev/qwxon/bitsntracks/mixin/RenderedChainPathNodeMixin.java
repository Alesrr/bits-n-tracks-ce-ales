package dev.qwxon.bitsntracks.mixin;

import com.kipti.bnb.content.kinetics.cogwheel_chain.graph.RenderedChainPathNode;
import dev.qwxon.bitsntracks.access.BntRunShapeNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(
    value = {RenderedChainPathNode.class},
    remap = false
)
public class RenderedChainPathNodeMixin implements BntRunShapeNode {
    @Unique
    private boolean bnt$runShape;

    @Override
    public boolean bnt$isRunShape() {
        return this.bnt$runShape;
    }

    @Override
    public void bnt$markRunShape() {
        this.bnt$runShape = true;
    }
}
