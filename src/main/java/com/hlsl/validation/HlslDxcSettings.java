package com.hlsl.validation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Service(Service.Level.APP)
@State(name = "HlslDxcSettings", storages = @Storage("hlslDxcSettings.xml"))
public final class HlslDxcSettings implements PersistentStateComponent<HlslDxcSettings.State> {

    public static class State {
        public String dxcPath = "";
        public String defaultProfile = "ps_6_6";
        public String defaultEntryPoint = "main";
        public String hlslVersion = "2021";
        public boolean enableValidation = true;
        public boolean treatWarningsAsErrors = false;
        // Warning groups (all enabled by default)
        public boolean warnUnusedVariable = true;
        public boolean warnUnusedParameter = true;
        public boolean warnUnreachableCode = true;
        public boolean warnUninitializedVariable = true;
        public boolean warnImplicitTruncation = true;
        public boolean warnConversion = true;
        public boolean warnPayloadAccess = true;
        public boolean warnEffectsSyntax = true;
        public String additionalArgs = "";
    }

    private State state = new State();

    public static HlslDxcSettings getInstance() {
        return ApplicationManager.getApplication().getService(HlslDxcSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public String getDxcPath() { return state.dxcPath; }
    public void setDxcPath(String path) { state.dxcPath = path; }

    public String getDefaultProfile() { return state.defaultProfile; }
    public void setDefaultProfile(String profile) { state.defaultProfile = profile; }

    public String getDefaultEntryPoint() { return state.defaultEntryPoint; }
    public void setDefaultEntryPoint(String entry) { state.defaultEntryPoint = entry; }

    public String getHlslVersion() { return state.hlslVersion; }
    public void setHlslVersion(String version) { state.hlslVersion = version; }

    public boolean isEnableValidation() { return state.enableValidation; }
    public void setEnableValidation(boolean enable) { state.enableValidation = enable; }

    public boolean isTreatWarningsAsErrors() { return state.treatWarningsAsErrors; }
    public void setTreatWarningsAsErrors(boolean treat) { state.treatWarningsAsErrors = treat; }

    public boolean isWarnUnusedVariable() { return state.warnUnusedVariable; }
    public void setWarnUnusedVariable(boolean v) { state.warnUnusedVariable = v; }

    public boolean isWarnUnusedParameter() { return state.warnUnusedParameter; }
    public void setWarnUnusedParameter(boolean v) { state.warnUnusedParameter = v; }

    public boolean isWarnUnreachableCode() { return state.warnUnreachableCode; }
    public void setWarnUnreachableCode(boolean v) { state.warnUnreachableCode = v; }

    public boolean isWarnUninitializedVariable() { return state.warnUninitializedVariable; }
    public void setWarnUninitializedVariable(boolean v) { state.warnUninitializedVariable = v; }

    public boolean isWarnImplicitTruncation() { return state.warnImplicitTruncation; }
    public void setWarnImplicitTruncation(boolean v) { state.warnImplicitTruncation = v; }

    public boolean isWarnConversion() { return state.warnConversion; }
    public void setWarnConversion(boolean v) { state.warnConversion = v; }

    public boolean isWarnPayloadAccess() { return state.warnPayloadAccess; }
    public void setWarnPayloadAccess(boolean v) { state.warnPayloadAccess = v; }

    public boolean isWarnEffectsSyntax() { return state.warnEffectsSyntax; }
    public void setWarnEffectsSyntax(boolean v) { state.warnEffectsSyntax = v; }

    public String getAdditionalArgs() { return state.additionalArgs; }
    public void setAdditionalArgs(String args) { state.additionalArgs = args; }

    /**
     * Returns the resolved DXC path: user setting if non-empty, otherwise auto-detected.
     */
    public @Nullable String getResolvedDxcPath() {
        String path = state.dxcPath;
        if (path != null && !path.isBlank()) {
            return path;
        }
        return autoDetectDxc();
    }

    /**
     * Try to find dxc.exe in common install locations.
     */
    public static @Nullable String autoDetectDxc() {
        // 1. Check PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File dxc = new File(dir, "dxc.exe");
                if (dxc.isFile() && dxc.canExecute()) return dxc.getAbsolutePath();
                dxc = new File(dir, "dxc");
                if (dxc.isFile() && dxc.canExecute()) return dxc.getAbsolutePath();
            }
        }

        // 2. Windows SDK
        Path windowsKits = Path.of("C:\\Program Files (x86)\\Windows Kits\\10\\bin");
        if (Files.isDirectory(windowsKits)) {
            try (Stream<Path> versions = Files.list(windowsKits)) {
                String found = versions
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(v -> v.resolve("x64").resolve("dxc.exe"))
                        .filter(Files::isRegularFile)
                        .map(Path::toString)
                        .findFirst()
                        .orElse(null);
                if (found != null) return found;
            } catch (Exception ignored) {}
        }

        // 3. Vulkan SDK
        String vulkanSdk = System.getenv("VULKAN_SDK");
        if (vulkanSdk != null) {
            File dxc = new File(vulkanSdk, "Bin\\dxc.exe");
            if (dxc.isFile()) return dxc.getAbsolutePath();
            dxc = new File(vulkanSdk, "bin/dxc");
            if (dxc.isFile()) return dxc.getAbsolutePath();
        }

        return null;
    }
}
