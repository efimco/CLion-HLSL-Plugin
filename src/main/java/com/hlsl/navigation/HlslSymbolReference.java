package com.hlsl.navigation;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlslSymbolReference extends PsiReferenceBase<PsiElement> {

    private static final Logger LOG = Logger.getInstance(HlslSymbolReference.class);

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "(?m)^\\s*#\\s*include\\s+[\"<]([^\">]+)[\">]"
    );

    private static final int MAX_INCLUDE_DEPTH = 16;

    public HlslSymbolReference(@NotNull PsiElement element) {
        super(element, new TextRange(0, element.getTextLength()), false);
    }

    @Override
    public @Nullable PsiElement resolve() {
        PsiElement element = getElement();
        String symbol = element.getText();
        if (symbol == null || symbol.isBlank()) return null;

        PsiFile file = element.getContainingFile();
        if (file == null) return null;

        String tokenName = element.getNode() != null ? element.getNode().getElementType().toString() : "";
        boolean preferFunction = "HlslTokenType.FUNCTION_CALL".equals(tokenName)
                || "HlslTokenType.INSTANCE_METHOD_CALL".equals(tokenName);
        int usageOffset = element.getTextRange().getStartOffset();

        LOG.warn("HLSL resolve: symbol=" + symbol + " token=" + tokenName
                + " file=" + (file.getVirtualFile() != null ? file.getVirtualFile().getPath() : "<in-memory>"));

        PsiElement found = findInFile(file, symbol, preferFunction, usageOffset);
        if (found != null) {
            LOG.warn("HLSL resolve: found in current file at offset " + found.getTextRange().getStartOffset());
            return found;
        }

        PsiElement viaInclude = findInIncludes(file, symbol, preferFunction, new HashSet<>(), 0);
        LOG.warn("HLSL resolve: include search " + (viaInclude != null
                ? ("found in " + viaInclude.getContainingFile().getVirtualFile().getPath())
                : "returned null"));
        return viaInclude;
    }

    private static @Nullable PsiElement findInFile(PsiFile file,
                                                   String symbol,
                                                   boolean preferFunction,
                                                   int usageOffset) {
        String content = file.getText();
        if (content == null || content.isEmpty()) return null;

        int decl;
        if (preferFunction) {
            decl = findFunctionDeclarationOffset(content, symbol, usageOffset);
            if (decl < 0) decl = findVariableDeclarationOffset(content, symbol, usageOffset);
        } else {
            decl = findVariableDeclarationOffset(content, symbol, usageOffset);
            if (decl < 0) decl = findFunctionDeclarationOffset(content, symbol, usageOffset);
        }
        if (decl < 0) return null;
        return findSymbolElementAt(file, decl, symbol);
    }

    private static @Nullable PsiElement findInIncludes(PsiFile file,
                                                       String symbol,
                                                       boolean preferFunction,
                                                       Set<String> visited,
                                                       int depth) {
        if (depth > MAX_INCLUDE_DEPTH) return null;
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) return null;
        if (!visited.add(vf.getPath())) return null;
        VirtualFile baseDir = vf.getParent();
        if (baseDir == null) return null;

        String content = file.getText();
        if (content == null) return null;

        Matcher m = INCLUDE_PATTERN.matcher(content);
        while (m.find()) {
            String includePath = m.group(1);
            VirtualFile included = baseDir.findFileByRelativePath(includePath);
            if (included == null || !included.exists() || included.isDirectory()) {
                LOG.warn("HLSL include: cannot resolve '" + includePath + "' from " + baseDir.getPath());
                continue;
            }
            PsiFile includedPsi = PsiManager.getInstance(file.getProject()).findFile(included);
            if (includedPsi == null) {
                LOG.warn("HLSL include: PsiManager.findFile returned null for " + included.getPath());
                continue;
            }

            LOG.warn("HLSL include: searching " + included.getPath() + " for '" + symbol + "'");
            PsiElement found = findInFile(includedPsi, symbol, preferFunction, Integer.MAX_VALUE);
            if (found != null) return found;

            PsiElement deeper = findInIncludes(includedPsi, symbol, preferFunction, visited, depth + 1);
            if (deeper != null) return deeper;
        }
        return null;
    }

    private static int findFunctionDeclarationOffset(String content, String name, int usageOffset) {
        String escaped = Pattern.quote(name);

        Pattern pattern = Pattern.compile(
                "(?m)(?:^|[;{}])\\s*(?:(?:inline|static|precise|const|extern|groupshared|uniform|export)\\s+)*" +
                        "(?:[A-Za-z_][A-Za-z0-9_]*(?:\\s*<[^>]+>)?|[A-Za-z_][A-Za-z0-9_]*\\s*[0-9]*x?[0-9]*)\\s+" +
                        "(" + escaped + ")\\s*\\("
        );

        Matcher m = pattern.matcher(content);
        int best = -1;
        while (m.find()) {
            int nameOffset = m.start(1);
            if (nameOffset >= usageOffset) break;

            int openParen = content.indexOf('(', m.end(1) - 1);
            if (openParen < 0) continue;
            int closeParen = findMatchingParen(content, openParen);
            if (closeParen < 0) continue;

            int next = skipWhitespace(content, closeParen + 1);
            if (next >= content.length()) continue;

            char tail = content.charAt(next);
            if (tail == '{' || tail == ';' || tail == ':') {
                best = nameOffset;
            }
        }
        return best;
    }

    private static int findVariableDeclarationOffset(String content, String name, int usageOffset) {
        String escaped = Pattern.quote(name);

        Pattern localOrGlobal = Pattern.compile(
                "(?m)(?:^|[;{}(,])\\s*" +
                        "(?:(?:const|static|groupshared|uniform|extern|in|out|inout|row_major|column_major|volatile|precise)\\s+)*" +
                        "(?:[A-Za-z_][A-Za-z0-9_]*(?:\\s*<[^>]+>)?|[A-Za-z_][A-Za-z0-9_]*\\s*[0-9]*x?[0-9]*)\\s+" +
                        "(" + escaped + ")\\b"
        );

        Matcher m = localOrGlobal.matcher(content);
        int best = -1;
        while (m.find()) {
            int nameOffset = m.start(1);
            if (nameOffset >= usageOffset) break;

            if (isInsideCommentOrString(content, nameOffset)) continue;
            best = nameOffset;
        }

        return best;
    }

    private static @Nullable PsiElement findSymbolElementAt(PsiFile file, int offset, String symbol) {
        PsiElement direct = file.findElementAt(offset);
        if (direct != null && symbol.equals(direct.getText())) return direct;

        for (int i = Math.max(0, offset - 2); i <= offset + 2; i++) {
            PsiElement e = file.findElementAt(i);
            if (e != null && symbol.equals(e.getText())) {
                return e;
            }
        }
        return null;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findMatchingParen(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isInsideCommentOrString(String content, int offset) {
        boolean inString = false;
        boolean inBlock = false;
        boolean inLine = false;

        for (int i = 0; i < content.length() && i < offset; i++) {
            char c = content.charAt(i);
            char n = i + 1 < content.length() ? content.charAt(i + 1) : '\0';

            if (inLine) {
                if (c == '\n') inLine = false;
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\' && i + 1 < content.length()) {
                    i++;
                    continue;
                }
                if (c == '"') inString = false;
                continue;
            }

            if (c == '/' && n == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            }
        }

        return inString || inBlock || inLine;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return new Object[0];
    }
}
