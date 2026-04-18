package com.hlsl.navigation;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HlslSymbolReference extends PsiReferenceBase<PsiElement> {

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

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

        String content = file.getText();
        if (content == null || content.isEmpty()) return null;

        int usageOffset = element.getTextRange().getStartOffset();
        String tokenName = element.getNode() != null ? element.getNode().getElementType().toString() : "";

        int declarationOffset;
        if ("HlslTokenType.FUNCTION_CALL".equals(tokenName)
                || "HlslTokenType.INSTANCE_METHOD_CALL".equals(tokenName)) {
            declarationOffset = findFunctionDeclarationOffset(content, symbol, usageOffset);
            if (declarationOffset < 0) {
                declarationOffset = findVariableDeclarationOffset(content, symbol, usageOffset);
            }
        } else {
            declarationOffset = findVariableDeclarationOffset(content, symbol, usageOffset);
            if (declarationOffset < 0) {
                declarationOffset = findFunctionDeclarationOffset(content, symbol, usageOffset);
            }
        }

        if (declarationOffset < 0) return null;
        return findSymbolElementAt(file, declarationOffset, symbol);
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
