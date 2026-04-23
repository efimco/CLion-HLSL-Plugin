package com.hlsl.completion;

import com.hlsl.lexer.HlslLexer;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses HLSL files to extract identifiers and #include directives, caches per-file,
 * and walks the transitive include graph on demand.
 */
@Service(Service.Level.PROJECT)
public final class HlslIncludeIndex {

    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("#\\s*include\\s+[\"<]([^\">]+)[\">]");

    private static final Pattern DEFINE_PATTERN =
            Pattern.compile("#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private static final int MAX_PARENT_WALK = 10;

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    public HlslIncludeIndex(@SuppressWarnings("unused") Project project) {
    }

    private static final class Entry {
        final long modificationStamp;
        final Set<String> identifiers;
        final List<String> includes;

        Entry(long stamp, Set<String> identifiers, List<String> includes) {
            this.modificationStamp = stamp;
            this.identifiers = identifiers;
            this.includes = includes;
        }
    }

    public @NotNull Set<String> collectTransitiveIdentifiers(@Nullable VirtualFile file) {
        if (file == null) return Collections.emptySet();
        Set<String> result = new HashSet<>();
        Set<String> visited = new HashSet<>();
        collect(file, result, visited, 0);
        return result;
    }

    private void collect(VirtualFile file, Set<String> out, Set<String> visited, int depth) {
        if (depth > 32) return;
        if (!visited.add(file.getPath())) return;
        Entry entry = getOrParse(file);
        if (entry == null) return;
        out.addAll(entry.identifiers);
        for (String include : entry.includes) {
            VirtualFile resolved = resolve(file, include);
            if (resolved != null) {
                collect(resolved, out, visited, depth + 1);
            }
        }
    }

    private @Nullable Entry getOrParse(VirtualFile file) {
        long stamp = file.getModificationStamp();
        Entry cached = cache.get(file.getPath());
        if (cached != null && cached.modificationStamp == stamp) {
            return cached;
        }
        Entry parsed = parse(file);
        if (parsed != null) {
            cache.put(file.getPath(), parsed);
        }
        return parsed;
    }

    private @Nullable Entry parse(VirtualFile file) {
        CharSequence text;
        try {
            byte[] bytes = file.contentsToByteArray();
            text = new String(bytes, file.getCharset() != null ? file.getCharset() : StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }

        Set<String> identifiers = new HashSet<>();
        List<String> includes = new ArrayList<>();

        // First pass: collect significant tokens (skip whitespace/comments)
        List<IElementType> types = new ArrayList<>();
        List<String> texts = new ArrayList<>();

        HlslLexer lexer = new HlslLexer();
        lexer.start(text, 0, text.length(), 0);
        while (lexer.getTokenType() != null) {
            IElementType t = lexer.getTokenType();
            if (t != com.intellij.psi.TokenType.WHITE_SPACE
                    && t != HlslTokenTypes.LINE_COMMENT
                    && t != HlslTokenTypes.BLOCK_COMMENT) {
                types.add(t);
                texts.add(text.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString());
            }
            lexer.advance();
        }

        // Second pass: filter to functions, struct names, and global constants
        int braceDepth = 0;
        int parenDepth = 0;
        for (int i = 0; i < types.size(); i++) {
            IElementType t = types.get(i);
            String tx = texts.get(i);

            if (t == HlslTokenTypes.LBRACE) braceDepth++;
            else if (t == HlslTokenTypes.RBRACE) braceDepth = Math.max(0, braceDepth - 1);
            else if (t == HlslTokenTypes.LPAREN) parenDepth++;
            else if (t == HlslTokenTypes.RPAREN) parenDepth = Math.max(0, parenDepth - 1);

            if (t == HlslTokenTypes.FUNCTION_CALL || t == HlslTokenTypes.STRUCT_NAME) {
                identifiers.add(tx);
            } else if (t == HlslTokenTypes.PREPROCESSOR) {
                Matcher incMatch = INCLUDE_PATTERN.matcher(tx);
                if (incMatch.find()) {
                    includes.add(incMatch.group(1));
                }
                Matcher defMatch = DEFINE_PATTERN.matcher(tx);
                if (defMatch.find()) {
                    identifiers.add(defMatch.group(1));
                }
            } else if (braceDepth == 0 && parenDepth == 0
                    && t == HlslTokenTypes.KEYWORD && "const".equals(tx)) {
                String name = findDeclaredName(types, texts, i + 1);
                if (name != null) identifiers.add(name);
            }
        }

        return new Entry(file.getModificationStamp(), identifiers, includes);
    }

    /**
     * After a top-level `const` keyword, skip type tokens and return the first identifier
     * that looks like a variable name (followed by `=`, `;`, `[`, or `:`).
     */
    private static @Nullable String findDeclaredName(List<IElementType> types, List<String> texts, int from) {
        String candidate = null;
        for (int i = from; i < types.size() && i < from + 16; i++) {
            IElementType t = types.get(i);
            if (t == HlslTokenTypes.TYPE_KEYWORD || t == HlslTokenTypes.KEYWORD) {
                continue;
            }
            if (t == HlslTokenTypes.IDENTIFIER
                    || t == HlslTokenTypes.STRUCT_NAME
                    || t == HlslTokenTypes.FUNCTION_CALL) {
                candidate = texts.get(i);
                continue;
            }
            if (t == HlslTokenTypes.OPERATOR && "=".equals(texts.get(i))) return candidate;
            if (t == HlslTokenTypes.SEMICOLON) return candidate;
            if (t == HlslTokenTypes.LBRACKET) return candidate;
            if (t == HlslTokenTypes.COLON) return candidate;
            if (t == HlslTokenTypes.LPAREN) return null;
        }
        return null;
    }

    private static @Nullable VirtualFile resolve(VirtualFile fromFile, String include) {
        String normalized = include.replace('\\', '/');
        VirtualFile dir = fromFile.getParent();
        if (dir == null) return null;

        VirtualFile found = dir.findFileByRelativePath(normalized);
        if (found != null && !found.isDirectory()) return found;

        VirtualFile cur = dir.getParent();
        int walked = 0;
        while (cur != null && walked < MAX_PARENT_WALK) {
            found = cur.findFileByRelativePath(normalized);
            if (found != null && !found.isDirectory()) return found;
            cur = cur.getParent();
            walked++;
        }
        return null;
    }
}
