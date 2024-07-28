package com.oscimate.oscimate_soulflame.mixin.fire_overlays.client;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.oscimate.oscimate_soulflame.ColorizeMath;
import com.oscimate.oscimate_soulflame.Main;
import net.minecraft.block.FireBlock;
import net.minecraft.client.texture.*;
import net.minecraft.resource.metadata.ResourceMetadata;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Mixin(SpriteLoader.class)
public class SpriteLoaderMixin {

    @Shadow @Final private Identifier id;

    @Unique
    public ByteBuffer deepCopy(ByteBuffer source, ByteBuffer target) {

        int sourceP = source.position();
        int sourceL = source.limit();

        if (null == target) {
            target = ByteBuffer.allocate(source.remaining());
        }
        target.put(source);
        target.flip();

        source.position(sourceP);
        source.limit(sourceL);
        return target;
    }

    @Inject(method = "stitch", at = @At("HEAD"))
    private void addSprites(List<SpriteContents> sp, int mipLevel, Executor executor, CallbackInfoReturnable<SpriteLoader.StitchResult> cir, @Local LocalRef<List<SpriteContents>> sprites) {
        if (id.equals(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)) {
            List<int[]> ints = Main.CONFIG_MANAGER.getCurrentBlockFireColors().stream()
                    .flatMap(map -> map.values().stream())
                    .distinct()
                    .collect(Collectors.toMap(
                            arr -> arr[0] + "-" + arr[1],
                            arr -> arr,
                            (existing, replacement) -> existing
                    ))
                    .values()
                    .stream()
                    .toList();

            ArrayList<Long> pointers = new ArrayList<>();

            ArrayList<SpriteContents> all = new ArrayList<>();

            for(SpriteContents spriteContents : sp) {
                if (spriteContents.getId().equals(new Identifier("oscimate_soulflame:block/blank_fire_0")) || spriteContents.getId().equals(new Identifier("oscimate_soulflame:block/blank_fire_overlay_0")) || spriteContents.getId().equals(new Identifier("oscimate_soulflame:block/blank_fire_1")) || spriteContents.getId().equals(new Identifier("oscimate_soulflame:block/blank_fire_overlay_1"))) {
                    boolean isOverlay = spriteContents.getId().equals(new Identifier("oscimate_soulflame:block/blank_fire_overlay_0")) || spriteContents.getId().equals(new Identifier("oscimate_soulflame:block/blank_fire_overlay_1"));

                    ByteBuffer original = MemoryUtil.memByteBuffer((((NativeImageInvoker)(Object)((SpriteContentsInvoker) spriteContents).getImage())).getPointer(), (int) (((NativeImageInvoker)(Object)((SpriteContentsInvoker) spriteContents).getImage())).getSizeBytes());

                    for (int i = 0; i < ints.size(); i++) {
                        long pointer = MemoryUtil.nmemAlloc(original.capacity());

                        pointers.add(pointer);

                        ByteBuffer baseBuffer = MemoryUtil.memByteBuffer(pointer, original.capacity());

                        deepCopy(original, baseBuffer);

                        ByteBuffer overlayBuffer = null;

                        if (!isOverlay) {
                            overlayBuffer = MemoryUtil.memByteBuffer(pointers.get(i), original.capacity());
                        }

                        Color c = new Color(ints.get(i)[0]);

                        if (isOverlay) {
                            c = new Color(ints.get(i)[1]);
                        }

                        float[] vertexColor = new float[]{c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1f};

                        for (int y = 0; y < 16 * 32; y++) {
                            for (int x = 0; x < 16; x++) {
                                int index = (y * 16 + x) * 4;

                                int baseR = baseBuffer.get(index) & 0xFF;
                                int baseG = baseBuffer.get(index + 1) & 0xFF;
                                int baseB = baseBuffer.get(index + 2) & 0xFF;
                                int baseA = baseBuffer.get(index + 3) & 0xFF;

                                float[] cb = ColorizeMath.applyColorization(new float[]{baseR / 255f, baseG / 255f, baseB / 255f, 1f}, vertexColor);

                                int finalR = (Math.round(cb[0] * 255) & 0xFF);
                                int finalG = (Math.round(cb[1] * 255) & 0xFF);
                                int finalB = (Math.round(cb[2] * 255) & 0xFF);

                                if (!isOverlay) {
                                    int overlayR = overlayBuffer.get(index) & 0xFF;
                                    int overlayG = overlayBuffer.get(index + 1) & 0xFF;
                                    int overlayB = overlayBuffer.get(index + 2) & 0xFF;
                                    int overlayA = overlayBuffer.get(index + 3) & 0xFF;

                                    System.out.println(new Color(overlayR, overlayG, overlayB).getRGB());

                                    finalR = (overlayR * overlayA + finalR * (255 - overlayA)) / 255;
                                    finalG = (overlayG * overlayA + finalG * (255 - overlayA)) / 255;
                                    finalB = (overlayB * overlayA + finalB * (255 - overlayA)) / 255;
                                }

                                baseBuffer.put(index, (byte) finalR);
                                baseBuffer.put(index + 1, (byte) finalG);
                                baseBuffer.put(index + 2, (byte) finalB);
                                baseBuffer.put(index + 3, (byte) (baseA & 0xFF));
                            }
                        }

                        int num = spriteContents.getId().toString().contains("1") ? 1 : 0;

                        if (!isOverlay) {
                            all.add(new SpriteContents((new Identifier("block/fire_"+num+"_" + Math.abs(ints.get(i)[0]) + "_" + Math.abs(ints.get(i)[1]))), new SpriteDimensions(16, 16), NativeImageInvoker.invokeInit(NativeImage.Format.RGBA, 16, 16*32, false, pointer), spriteContents.getMetadata()));
                        }
                    }
                }
            }

            System.out.println(all);

            sprites.set(new ImmutableList.Builder<SpriteContents>()
                    .addAll(sp)
                    .addAll(all)
                    .build());

//            pointers.forEach(MemoryUtil::nmemFree);
        }
    }
}
