package com.hlsl.folding;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class HlslFoldingBuilder extends FoldingBuilderEx {

    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        Deque<ASTNode> braceStack = new ArrayDeque<>();

        ASTNode node = root.getNode().getFirstChildNode();
        while (node != null) {
            IElementType type = node.getElementType();
            if (type == HlslTokenTypes.LBRACE) {
                braceStack.push(node);
            } else if (type == HlslTokenTypes.RBRACE) {
                ASTNode open = braceStack.pollFirst();
                if (open != null) {
                    int start = open.getStartOffset();
                    int end = node.getStartOffset() + node.getTextLength();
                    if (isMultiLine(document, start, end)) {
                        descriptors.add(new FoldingDescriptor(open, new TextRange(start, end)));
                    }
                }
            } else if (type == HlslTokenTypes.BLOCK_COMMENT) {
                int start = node.getStartOffset();
                int end = start + node.getTextLength();
                if (isMultiLine(document, start, end)) {
                    descriptors.add(new FoldingDescriptor(node, new TextRange(start, end)));
                }
            }
            node = node.getTreeNext();
        }

        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    private static boolean isMultiLine(Document document, int start, int end) {
        if (end > document.getTextLength()) return false;
        return document.getLineNumber(start) != document.getLineNumber(end);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        IElementType type = node.getElementType();
        if (type == HlslTokenTypes.LBRACE) {
            return "{...}";
        }
        if (type == HlslTokenTypes.BLOCK_COMMENT) {
            return "/*...*/";
        }
        return "...";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
