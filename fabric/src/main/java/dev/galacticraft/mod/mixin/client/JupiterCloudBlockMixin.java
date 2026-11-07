package dev.galacticraft.mod.mixin.client;

import dev.galacticraft.mod.client.render.dimension.JupiterCloudRenderer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Collision and block storage remain vanilla; only the dense cloud mesh is replaced. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class JupiterCloudBlockMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void galacticraft$volumeInsteadOfCloudCube(CallbackInfoReturnable<RenderShape> cir) {
        if (JupiterCloudRenderer.isActive() && JupiterCloudRenderer.isCloud((BlockState) (Object) this)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }
}