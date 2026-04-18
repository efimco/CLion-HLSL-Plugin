package com.hlsl.navigation;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public class HlslGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement,
                                                              int offset,
                                                              Editor editor) {
        if (sourceElement == null || sourceElement.getNode() == null) return null;
        IElementType type = sourceElement.getNode().getElementType();
        if (!HlslTokenTypes.IDENTIFIER.equals(type)
                && !HlslTokenTypes.FUNCTION_CALL.equals(type)
                && !HlslTokenTypes.FIELD_ACCESS.equals(type)
                && !HlslTokenTypes.INSTANCE_METHOD_CALL.equals(type)) {
            return null;
        }
        PsiElement target = new HlslSymbolReference(sourceElement).resolve();
        return target != null ? new PsiElement[]{target} : null;
    }
}
