package com.yamikhal.frostline.weather.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yamikhal.frostline.weather.WeatherConfig;
import com.yamikhal.frostline.weather.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws precipitation in place of vanilla's renderSnowAndRain, with vanilla's textures and
 * shader (so shader packs still treat it as weather).
 *
 * Snow is two layers of camera-facing column quads:
 *
 *   main  always drawn where it snows. Light, slow snowfall when calm (calmSnowfall); in a
 *         storm it widens to stormRadius, turns opaque and falls faster.
 *   near  only in storms: a denser, faster layer close to the camera (stormSnowfall).
 *
 * In a storm the columns slant with the wind: points above eye level are shifted upwind and
 * points below downwind, so flakes scrolling down the quad travel with the wind. Scroll
 * phases are accumulated per frame and wrapped at whole texture repeats, so changing speed
 * never makes the texture jump. Rain columns are drawn like vanilla.
 *
 * Per frame there is no world access and no allocation; ColumnCache supplies the columns.
 */
final class SnowRenderer {

    private static final ResourceLocation RAIN = new ResourceLocation("textures/environment/rain.png");
    private static final ResourceLocation SNOW = new ResourceLocation("textures/environment/snow.png");

    private static double lastTime = -1;
    private static float phaseSlow;
    private static float phaseFast;
    private static float phaseNear;
    private static float phaseRain;

    private SnowRenderer() {
    }

    static boolean render(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture,
                          double camX, double camY, double camZ) {
        ColumnCache c = WeatherClient.COLUMNS;
        if (!c.valid) {
            return true;
        }
        WeatherConfig.Client cfg = WeatherConfig.CLIENT;
        float rain = level.getRainLevel(partialTick);
        float storm = WeatherClient.stormAt(partialTick);
        float calm = cfg.calmSnowfall.get().floatValue();
        float mainAlpha = Mth.lerp(storm, calm, 1f);
        float nearAlpha = storm * cfg.stormSnowfall.get().floatValue();
        float density = Mth.lerp(storm, cfg.calmDensity.get().floatValue(), cfg.stormDensity.get().floatValue());
        boolean drawRain = rain > 0 && c.rainColumns > 0;
        boolean drawSnow = c.snowColumns > 0 && mainAlpha > 0.01f && density > 0.01f;

        double time = ticks + partialTick;
        double dt = lastTime < 0 || time < lastTime || time - lastTime > 20 ? 0 : time - lastTime;
        lastTime = time;
        phaseSlow = wrap(phaseSlow + (float) dt * Mth.lerp(storm, 0.0022f, 0.014f));
        phaseFast = wrap(phaseFast + (float) dt * Mth.lerp(storm, 0.0034f, 0.021f));
        phaseNear = wrap(phaseNear + (float) dt * Mth.lerp(storm, 0.008f, 0.04f));
        phaseRain = wrap(phaseRain + (float) dt * 0.11f);
        if (!drawRain && !drawSnow) {
            return true;
        }

        double heading = Wind.direction(level.getGameTime() + partialTick);
        double slant = Math.min(0.9, cfg.windSlant.get() * storm * WeatherClient.gust());
        double slantX = Math.cos(heading) * slant;
        double slantZ = Math.sin(heading) * slant;
        float sway = (float) ((ticks % 8400 + partialTick) * 0.15);

        int eyeX = Mth.floor(camX);
        int eyeY = Mth.floor(camY);
        int eyeZ = Mth.floor(camZ);

        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getParticleShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        if (drawRain) {
            int radius = Minecraft.useFancyGraphics() ? 10 : 5;
            RenderSystem.setShaderTexture(0, RAIN);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            for (int z = eyeZ - radius; z <= eyeZ + radius; z++) {
                for (int x = eyeX - radius; x <= eyeX + radius; x++) {
                    int i = c.index(x, z);
                    if (i < 0 || c.precipitation[i] != ColumnCache.RAIN) {
                        continue;
                    }
                    float fade = fade(x, z, camX, camZ, radius, 0.5f);
                    if (fade <= 0) {
                        continue;
                    }
                    int h = hash(x, z);
                    int y0 = Math.max(eyeY - radius, c.height[i]);
                    int y1 = Math.max(eyeY + radius, c.height[i]);
                    if (y0 != y1) {
                        float phase = phaseRain * (1 + (h & 3) * 0.25f) + unit(h >>> 16);
                        column(buffer, x, z, y0, y1, camX, camY, camZ, 0, 0, 0, 1, 0.25f, wrap(phase), fade * rain, c.light[i]);
                    }
                }
            }
            tesselator.end();
        }

        if (drawSnow) {
            int mainRadius = Math.min(ColumnCache.RADIUS - 1, Math.round(Mth.lerp(storm, 8, cfg.stormRadius.get())));
            int nearRadius = Math.min(mainRadius, 6);
            RenderSystem.setShaderTexture(0, SNOW);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            for (int z = eyeZ - mainRadius; z <= eyeZ + mainRadius; z++) {
                for (int x = eyeX - mainRadius; x <= eyeX + mainRadius; x++) {
                    int i = c.index(x, z);
                    if (i < 0 || c.precipitation[i] != ColumnCache.SNOW) {
                        continue;
                    }
                    int h = hash(x, z);
                    // Density: each column has a fixed rank; columns ranked above the density
                    // are skipped, and those near the cut fade, so changing density never pops.
                    float mainKeep = keep(unit(h >>> 24), density);
                    float nearKeep = keep(unit(h >>> 1), density);
                    if (mainKeep <= 0 && nearKeep <= 0) {
                        continue;
                    }
                    float u = unit(h >>> 8) + Mth.sin(sway * (0.6f + unit(h >>> 20) * 0.8f) + h) * 0.03f * (1 + 2 * storm);
                    float v = unit(h >>> 16);

                    float fade = fade(x, z, camX, camZ, mainRadius, 0.3f);
                    int y0 = Math.max(eyeY - 10, c.height[i]);
                    int y1 = Math.max(eyeY + 10, c.height[i]);
                    if (mainKeep > 0 && fade > 0 && y0 != y1) {
                        float phase = ((h & 1) == 0 ? phaseSlow : phaseFast) + v;
                        column(buffer, x, z, y0, y1, camX, camY, camZ, slantX, slantZ, u, 1, 0.25f, phase, fade * mainAlpha * mainKeep, c.snowLight[i]);
                    }

                    if (nearKeep > 0 && nearAlpha > 0.01f && Math.abs(x - eyeX) <= nearRadius && Math.abs(z - eyeZ) <= nearRadius) {
                        float nearFade = fade(x, z, camX, camZ, nearRadius, 0f);
                        int n0 = Math.max(eyeY - 6, c.height[i]);
                        int n1 = Math.max(eyeY + 6, c.height[i]);
                        if (nearFade > 0 && n0 != n1) {
                            column(buffer, x, z, n0, n1, camX, camY, camZ, slantX * 1.3, slantZ * 1.3,
                                    u + 0.5f, 2, 0.5f, phaseNear + v, nearFade * nearAlpha * nearKeep, c.snowLight[i]);
                        }
                    }
                }
            }
            tesselator.end();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
        return true;
    }

    /**
     * One column quad facing the camera, from y0 to y1, sheared by the slant around eye level.
     * Texture v runs downward from y0; subtracting the phase scrolls flakes down.
     */
    private static void column(BufferBuilder buffer, int x, int z, int y0, int y1,
                               double camX, double camY, double camZ, double slantX, double slantZ,
                               float u, float uWidth, float vScale, float phase, float alpha, int light) {
        double ox = x + 0.5 - camX;
        double oz = z + 0.5 - camZ;
        double len = Math.sqrt(ox * ox + oz * oz);
        double sx = len < 1.0E-4 ? 0.5 : -oz / len * 0.5;
        double sz = len < 1.0E-4 ? 0 : ox / len * 0.5;
        double bottom = y0 - camY;
        double top = y1 - camY;
        double tx = ox - slantX * top;
        double tz = oz - slantZ * top;
        double bx = ox - slantX * bottom;
        double bz = oz - slantZ * bottom;
        float vTop = y0 * vScale - phase;
        float vBottom = y1 * vScale - phase;
        buffer.vertex(tx - sx, top, tz - sz).uv(u, vTop).color(1f, 1f, 1f, alpha).uv2(light).endVertex();
        buffer.vertex(tx + sx, top, tz + sz).uv(u + uWidth, vTop).color(1f, 1f, 1f, alpha).uv2(light).endVertex();
        buffer.vertex(bx + sx, bottom, bz + sz).uv(u + uWidth, vBottom).color(1f, 1f, 1f, alpha).uv2(light).endVertex();
        buffer.vertex(bx - sx, bottom, bz - sz).uv(u, vBottom).color(1f, 1f, 1f, alpha).uv2(light).endVertex();
    }

    /** Vanilla's radial fade: 0 outside the radius, falling from 1 at the centre to floor at the edge. */
    private static float fade(int x, int z, double camX, double camZ, int radius, float floor) {
        double dx = x + 0.5 - camX;
        double dz = z + 0.5 - camZ;
        float d = (float) Math.sqrt(dx * dx + dz * dz) / radius;
        if (d > 1) {
            return 0;
        }
        return (1 - d * d) * (1 - floor) + floor;
    }

    /** 1 for columns ranked well below the density, fading to 0 across the last 0.1 above it. */
    private static float keep(float rank, float density) {
        return Mth.clamp((density - rank) * 10 + 1, 0, 1);
    }

    private static int hash(int x, int z) {
        int h = x * 3129871 ^ z * 116129781;
        return h * h * 42317861 + h * 11;
    }

    private static float unit(int bits) {
        return (bits & 0xFF) / 255f;
    }

    private static float wrap(float f) {
        return f - Mth.floor(f);
    }
}
