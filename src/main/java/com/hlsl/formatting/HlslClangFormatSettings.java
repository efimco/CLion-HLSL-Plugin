package com.hlsl.formatting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@Service(Service.Level.APP)
@State(name = "HlslClangFormatSettings", storages = @Storage("hlslClangFormatSettings.xml"))
public final class HlslClangFormatSettings implements PersistentStateComponent<HlslClangFormatSettings.State> {

    public static class State {
        public String clangFormatPath = "";
        public String fallbackStyle = "LLVM";
        public boolean enabled = true;
    }

    private State state = new State();

    public static HlslClangFormatSettings getInstance() {
        return ApplicationManager.getApplication().getService(HlslClangFormatSettings.class);
    }

    @Override
    public @Nullable State getState() { return state; }

    @Override
    public void loadState(@NotNull State state) { this.state = state; }

    public String getClangFormatPath() { return state.clangFormatPath; }
    public void setClangFormatPath(String path) { state.clangFormatPath = path; }

    public String getFallbackStyle() { return state.fallbackStyle; }
    public void setFallbackStyle(String style) { state.fallbackStyle = style; }

    public boolean isEnabled() { return state.enabled; }
    public void setEnabled(boolean enabled) { state.enabled = enabled; }

    public @Nullable String getResolvedClangFormatPath() {
        if (state.clangFormatPath != null && !state.clangFormatPath.isBlank()) {
            return state.clangFormatPath;
        }
        return autoDetect();
    }

    public static @Nullable String autoDetect() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            File f = new File(dir, "clang-format.exe");
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
            f = new File(dir, "clang-format");
            if (f.isFile() && f.canExecute()) return f.getAbsolutePath();
        }
        return null;
    }
}
