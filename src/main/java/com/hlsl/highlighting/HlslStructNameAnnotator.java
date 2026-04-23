package com.hlsl.highlighting;

import com.hlsl.HlslLanguage;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highlights struct/cbuffer/tbuffer/class/interface/enum names and typedef aliases
 * at every usage site, including names declared in transitively #included files.
 */
public class HlslStructNameAnnotator implements Annotator {

    private static final int MAX_INCLUDE_DEPTH = 16;

    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(?:struct|cbuffer|tbuffer|class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"
    );

    private static final Pattern TYPEDEF = Pattern.compile(
            "\\btypedef\\s+[A-Za-z_][\\w<>,\\s]*?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\[[^\\]]*\\])?\\s*;"
    );

    private static final Pattern INCLUDE = Pattern.compile(
            "(?m)^\\s*#\\s*include\\s+[\"<]([^\">]+)[\">]"
    );

    private static final Key<CachedValue<Set<String>>> STRUCT_NAMES_KEY =
            Key.create("HLSL_STRUCT_NAMES");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!element.getLanguage().is(HlslLanguage.INSTANCE)) return;

        IElementType type = element.getNode().getElementType();
        if (type != HlslTokenTypes.IDENTIFIER
                && type != HlslTokenTypes.FUNCTION_CALL) {
            return;
        }

        Set<String> structNames = getStructNames(element.getContainingFile());
        if (structNames.contains(element.getText())) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .textAttributes(HlslSyntaxHighlighter.STRUCT_NAME)
                    .create();
        }
    }

    private static Set<String> getStructNames(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, STRUCT_NAMES_KEY, () -> {
            Set<String> names = new HashSet<>();
            Set<String> visited = new HashSet<>();
            collectFromFile(file, names, visited, 0);
            return CachedValueProvider.Result.create(names, PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    private static void collectFromFile(PsiFile file,
                                        Set<String> names,
                                        Set<String> visited,
                                        int depth) {
        if (file == null || depth > MAX_INCLUDE_DEPTH) return;
        VirtualFile vf = file.getVirtualFile();
        String key = vf != null ? vf.getPath() : "<in-memory:" + System.identityHashCode(file) + ">";
        if (!visited.add(key)) return;

        String content = file.getText();
        if (content == null) return;

        collectFromText(content, names);

        if (vf == null) return;
        VirtualFile baseDir = vf.getParent();
        if (baseDir == null) return;

        Matcher m = INCLUDE.matcher(content);
        while (m.find()) {
            String includePath = m.group(1);
            VirtualFile included = baseDir.findFileByRelativePath(includePath);
            if (included == null || !included.exists() || included.isDirectory()) continue;
            PsiFile includedPsi = PsiManager.getInstance(file.getProject()).findFile(included);
            if (includedPsi != null) {
                collectFromFile(includedPsi, names, visited, depth + 1);
            }
        }
    }

    private static void collectFromText(String content, Set<String> names) {
        Matcher m = DECLARATION.matcher(content);
        while (m.find()) names.add(m.group(1));
        Matcher t = TYPEDEF.matcher(content);
        while (t.find()) names.add(t.group(1));
    }
}
