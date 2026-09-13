package com.yamikhal.frostline.mixin;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies the mixins under mixin.railways only when Railways Untold is installed, so
 * Frostline runs unchanged without it. Uses FML's loading mod list, which exists before
 * any mod class is loaded.
 */
public class FrostlineMixinPlugin implements IMixinConfigPlugin {

    private static final String RAILWAYS_PACKAGE = "com.yamikhal.frostline.mixin.railways.";

    private boolean railwaysPresent;

    @Override
    public void onLoad(String mixinPackage) {
        railwaysPresent = isModPresent("railwaysuntold");
    }

    private static boolean isModPresent(String modId) {
        try {
            LoadingModList list = LoadingModList.get();
            return list != null && list.getModFileById(modId) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.startsWith(RAILWAYS_PACKAGE) || railwaysPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
