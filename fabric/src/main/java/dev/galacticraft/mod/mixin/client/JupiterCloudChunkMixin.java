package dev.galacticraft.mod.mixin.client;

import dev.galacticraft.mod.client.render.dimension.JupiterCloudRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public class JupiterCloudChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void galacticraft$refreshChangedCloud(BlockPos pos, BlockState state, boolean moved,
                                                  CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (((LevelChunk) (Object) this).getLevel().isClientSide()
                && (JupiterCloudRenderer.isCloud(state) || previous != null && JupiterCloudRenderer.isCloud(previous))) {
            JupiterCloudRenderer.invalidate(pos);
        }
    }
}