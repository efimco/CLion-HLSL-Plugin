package com.hlsl.navigation;

import com.hlsl.HlslLanguage;
import com.hlsl.psi.HlslTokenTypes;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class HlslReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement().withLanguage(HlslLanguage.INSTANCE),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                           @NotNull ProcessingContext context) {
                        if (element.getNode() == null) return PsiReference.EMPTY_ARRAY;
                        var type = element.getNode().getElementType();
                        if (!type.equals(HlslTokenTypes.IDENTIFIER)
                                && !type.equals(HlslTokenTypes.FUNCTION_CALL)
                                && !type.equals(HlslTokenTypes.FIELD_ACCESS)
                                && !type.equals(HlslTokenTypes.INSTANCE_METHOD_CALL)) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        return new PsiReference[]{new HlslSymbolReference(element)};
                    }
                }
        );
    }
}
