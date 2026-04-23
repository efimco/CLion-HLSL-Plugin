package com.hlsl.formatting;

import com.hlsl.HlslLanguage;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class HlslClangFormatService extends AsyncDocumentFormattingService {

    private static final Set<Feature> FEATURES = EnumSet.of(Feature.FORMAT_FRAGMENTS, Feature.AD_HOC_FORMATTING);

    @Override
    public @NotNull Set<Feature> getFeatures() {
        return FEATURES;
    }

    @Override
    public boolean canFormat(@NotNull PsiFile file) {
        if (file.getLanguage() != HlslLanguage.INSTANCE) return false;
        HlslClangFormatSettings settings = HlslClangFormatSettings.getInstance();
        if (!settings.isEnabled()) return false;
        return settings.getResolvedClangFormatPath() != null;
    }

    @Override
    protected @NotNull String getName() {
        return "clang-format (HLSL)";
    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        return "HLSL clang-format";
    }

    @Override
    protected @Nullable FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest request) {
        HlslClangFormatSettings settings = HlslClangFormatSettings.getInstance();
        String clangFormat = settings.getResolvedClangFormatPath();
        if (clangFormat == null) return null;

        VirtualFile vfile = request.getContext().getVirtualFile();
        // Append .cpp so clang-format applies C++ style rules (HLSL extension is unknown to clang-format).
        // Keeping the real path prefix lets clang-format locate .clang-format in the file's directory tree.
        String assumeFilename = (vfile != null ? vfile.getPath() : "file.hlsl") + ".cpp";
        File workingDir = vfile != null ? new File(vfile.getPath()).getParentFile() : null;

        String documentText = request.getDocumentText();
        List<TextRange> ranges = request.getFormattingRanges();

        List<String> command = new ArrayList<>();
        command.add(clangFormat);
        command.add("-style=file");
        String fallback = settings.getFallbackStyle();
        command.add("-fallback-style=" + (fallback == null || fallback.isBlank() ? "LLVM" : fallback));
        command.add("-assume-filename=" + assumeFilename);

        boolean isFullDocument = ranges.size() == 1
                && ranges.get(0).getStartOffset() == 0
                && ranges.get(0).getEndOffset() >= documentText.length();
        if (!isFullDocument) {
            for (TextRange range : ranges) {
                command.add("-offset=" + range.getStartOffset());
                command.add("-length=" + range.getLength());
            }
        }

        return new FormattingTask() {
            private Process process;
            private volatile boolean cancelled = false;

            @Override
            public void run() {
                try {
                    ProcessBuilder pb = new ProcessBuilder(command);
                    if (workingDir != null && workingDir.isDirectory()) {
                        pb.directory(workingDir);
                    }
                    pb.redirectErrorStream(false);
                    process = pb.start();

                    try (OutputStream os = process.getOutputStream()) {
                        os.write(documentText.getBytes(StandardCharsets.UTF_8));
                    }

                    byte[] out = process.getInputStream().readAllBytes();
                    byte[] err = process.getErrorStream().readAllBytes();
                    int exit = process.waitFor();

                    if (cancelled) return;

                    if (exit == 0) {
                        request.onTextReady(new String(out, StandardCharsets.UTF_8));
                    } else {
                        String stderr = new String(err, StandardCharsets.UTF_8);
                        request.onError("clang-format failed",
                                stderr.isBlank() ? "Exit code " + exit : stderr);
                    }
                } catch (Exception e) {
                    if (!cancelled) {
                        String msg = e.getMessage();
                        request.onError("clang-format error", msg != null ? msg : e.toString());
                    }
                }
            }

            @Override
            public boolean cancel() {
                cancelled = true;
                if (process != null) process.destroy();
                return true;
            }
        };
    }
}
