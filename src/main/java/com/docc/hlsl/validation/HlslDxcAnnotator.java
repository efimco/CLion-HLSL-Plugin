package com.docc.hlsl.validation;

import com.docc.hlsl.HlslFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlslDxcAnnotator extends ExternalAnnotator<HlslDxcAnnotator.CollectedInfo, List<HlslDxcAnnotator.DxcDiagnostic>> {

    private static final Logger LOG = Logger.getInstance(HlslDxcAnnotator.class);

    // DXC error format: <file>:<line>:<col>: error: <message>
    private static final Pattern DIAG_PATTERN = Pattern.compile(
            "^(?:.*?:(\\d+):(\\d+):\\s*)?(error|warning|note):\\s*(.+)$"
    );

    public static class CollectedInfo {
        final String fileContent;
        final String filePath;
        final PsiFile psiFile;

        CollectedInfo(String fileContent, String filePath, PsiFile psiFile) {
            this.fileContent = fileContent;
            this.filePath = filePath;
            this.psiFile = psiFile;
        }
    }

    public static class DxcDiagnostic {
        final int line;       // 1-based, 0 = unknown
        final int column;     // 1-based, 0 = unknown
        final String severity; // "error", "warning", "note", or "info" for plugin messages
        final String message;

        DxcDiagnostic(int line, int column, String severity, String message) {
            this.line = line;
            this.column = column;
            this.severity = severity;
            this.message = message;
        }
    }

    @Override
    public @Nullable CollectedInfo collectInformation(@NotNull PsiFile file) {
        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        if (!settings.isEnableValidation()) return null;

        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) return null;
        if (!(file.getFileType() instanceof HlslFileType)) return null;

        Document doc = FileDocumentManager.getInstance().getDocument(vFile);
        if (doc == null) return null;

        return new CollectedInfo(doc.getText(), vFile.getPath(), file);
    }

    @Override
    public @Nullable List<DxcDiagnostic> doAnnotate(CollectedInfo info) {
        if (info == null) return null;

        HlslDxcSettings settings = HlslDxcSettings.getInstance();
        String dxcPath = settings.getResolvedDxcPath();

        List<DxcDiagnostic> diagnostics = new ArrayList<>();

        if (dxcPath == null || dxcPath.isBlank()) {
            diagnostics.add(new DxcDiagnostic(0, 0, "info",
                    "DXC not found. Configure path in Settings \u2192 Tools \u2192 HLSL / DXC, " +
                    "or install Windows SDK / Vulkan SDK."));
            return diagnostics;
        }

        // Check that the executable exists
        if (!new File(dxcPath).isFile() && !isInPath(dxcPath)) {
            diagnostics.add(new DxcDiagnostic(0, 0, "info",
                    "DXC executable not found at: " + dxcPath +
                    ". Update path in Settings \u2192 Tools \u2192 HLSL / DXC."));
            return diagnostics;
        }

        String profile = extractPragmaValue(info.fileContent, "profile", settings.getDefaultProfile());
        String entryPoint = extractPragmaValue(info.fileContent, "entry", settings.getDefaultEntryPoint());

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("hlsl_validate_", ".hlsl");
            Files.writeString(tempFile, info.fileContent, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(dxcPath);
            command.add("-T");
            command.add(profile);
            command.add("-E");
            command.add(entryPoint);
            command.add("-nologo");
            // Start with all warnings enabled, then suppress unchecked groups
            command.add("-Weverything");
            // Suppress noisy DXC built-in warnings
            command.add("-Wno-unused-macros");
            command.add("-Wno-missing-prototypes");
            command.add("-Wno-missing-variable-declarations");
            if (!settings.isWarnUnusedVariable()) command.add("-Wno-unused-variable");
            if (!settings.isWarnUnusedParameter()) command.add("-Wno-unused-parameter");
            if (!settings.isWarnUnreachableCode()) {
                command.add("-Wno-unreachable-code");
                command.add("-Wno-unreachable-code-return");
            }
            if (!settings.isWarnUninitializedVariable()) command.add("-Wno-uninitialized");
            if (!settings.isWarnImplicitTruncation()) command.add("-Wno-implicit-truncation");
            if (!settings.isWarnConversion()) command.add("-Wno-conversion");
            if (!settings.isWarnPayloadAccess()) command.add("-Wno-payload-access-warning");
            if (!settings.isWarnEffectsSyntax()) command.add("-Wno-effects-syntax");
            // Disable DXIL validation (we only want compilation diagnostics)
            command.add("-Vd");
            // HLSL version (2021 needed for ResourceDescriptorHeap etc.)
            String hlslVersion = settings.getHlslVersion();
            if (hlslVersion != null && !hlslVersion.isBlank()) {
                command.add("-HV");
                command.add(hlslVersion);
            }
            if (settings.isTreatWarningsAsErrors()) {
                command.add("-WX");
            }
            // Add include path from original file's directory
            String originalDir = new File(info.filePath).getParent();
            if (originalDir != null) {
                command.add("-I");
                command.add(originalDir);
            }
            command.add(tempFile.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read all output (diagnostics go to stderr, merged via redirectErrorStream)
            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                diagnostics.add(new DxcDiagnostic(0, 0, "warning", "DXC timed out after 30 seconds."));
                return diagnostics;
            }

            // Parse output lines for diagnostics
            for (String line : outputLines) {
                // Skip <built-in> diagnostics from DXC internals
                if (line.contains("<built-in>")) continue;
                // Suppress missing entry point error — expected for non-main shader files
                if (line.contains("missing entry point definition")) continue;
                Matcher m = DIAG_PATTERN.matcher(line.trim());
                if (m.matches()) {
                    int diagLine = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
                    int diagCol = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                    String severity = m.group(3);
                    String message = m.group(4);
                    diagnostics.add(new DxcDiagnostic(diagLine, diagCol, severity, message));
                }
            }

            // If DXC exited with error but we didn't parse any diagnostics, show raw output
            if (process.exitValue() != 0 && diagnostics.isEmpty() && !outputLines.isEmpty()) {
                String rawOutput = String.join(" | ", outputLines).trim();
                if (!rawOutput.isEmpty()) {
                    diagnostics.add(new DxcDiagnostic(0, 0, "error", rawOutput));
                }
            }

            return diagnostics;

        } catch (Exception e) {
            LOG.warn("DXC validation failed: " + e.getMessage(), e);
            diagnostics.add(new DxcDiagnostic(0, 0, "info",
                    "DXC execution failed: " + e.getMessage()));
            return diagnostics;
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void apply(@NotNull PsiFile file, List<DxcDiagnostic> diagnostics, @NotNull AnnotationHolder holder) {
        if (diagnostics == null || diagnostics.isEmpty()) return;

        Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        if (document == null) return;

        for (DxcDiagnostic diag : diagnostics) {
            HighlightSeverity severity = switch (diag.severity) {
                case "error" -> HighlightSeverity.ERROR;
                case "warning" -> HighlightSeverity.WARNING;
                case "info" -> HighlightSeverity.WEAK_WARNING;
                default -> HighlightSeverity.INFORMATION;
            };

            if (diag.line > 0 && diag.line <= document.getLineCount()) {
                int lineStartOffset = document.getLineStartOffset(diag.line - 1);
                int lineEndOffset = document.getLineEndOffset(diag.line - 1);

                int startOffset = lineStartOffset;
                if (diag.column > 0) {
                    startOffset = Math.min(lineStartOffset + diag.column - 1, lineEndOffset);
                }
                if (startOffset >= lineEndOffset) {
                    startOffset = lineStartOffset;
                }

                TextRange range = new TextRange(startOffset, lineEndOffset);
                holder.newAnnotation(severity, "DXC: " + diag.message)
                        .range(range)
                        .create();
            } else {
                // No line info — annotate the first line
                if (document.getLineCount() > 0) {
                    TextRange range = new TextRange(
                            document.getLineStartOffset(0),
                            document.getLineEndOffset(0));
                    holder.newAnnotation(severity, "DXC: " + diag.message)
                            .range(range)
                            .create();
                }
            }
        }
    }

    private static String extractPragmaValue(String content, String pragmaKey, String defaultValue) {
        Pattern p = Pattern.compile(
                "^\\s*(?://)?\\s*#pragma\\s+hlsl\\s+" + Pattern.quote(pragmaKey) + "\\s+(\\S+)",
                Pattern.MULTILINE
        );
        Matcher m = p.matcher(content);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }

    private static boolean isInPath(String executable) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return false;
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (new File(dir, executable).isFile()) return true;
            if (new File(dir, executable + ".exe").isFile()) return true;
        }
        return false;
    }
}
