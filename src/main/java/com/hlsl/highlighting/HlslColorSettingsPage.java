package com.hlsl.highlighting;

import com.hlsl.HlslIcons;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class HlslColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            new AttributesDescriptor("Keyword", HlslSyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("Type", HlslSyntaxHighlighter.TYPE_KEYWORD),
            new AttributesDescriptor("Built-in function", HlslSyntaxHighlighter.BUILTIN_FUNCTION),
            new AttributesDescriptor("Function call", HlslSyntaxHighlighter.FUNCTION_CALL),
            new AttributesDescriptor("Instance method call", HlslSyntaxHighlighter.INSTANCE_METHOD_CALL),
            new AttributesDescriptor("Field access", HlslSyntaxHighlighter.FIELD_ACCESS),
            new AttributesDescriptor("Semantic", HlslSyntaxHighlighter.SEMANTIC),
            new AttributesDescriptor("Preprocessor directive", HlslSyntaxHighlighter.PREPROCESSOR),
            new AttributesDescriptor("Number", HlslSyntaxHighlighter.NUMBER),
            new AttributesDescriptor("String", HlslSyntaxHighlighter.STRING),
            new AttributesDescriptor("Line comment", HlslSyntaxHighlighter.LINE_COMMENT),
            new AttributesDescriptor("Block comment", HlslSyntaxHighlighter.BLOCK_COMMENT),
            new AttributesDescriptor("Operator", HlslSyntaxHighlighter.OPERATOR),
            new AttributesDescriptor("Semicolon", HlslSyntaxHighlighter.SEMICOLON),
            new AttributesDescriptor("Comma", HlslSyntaxHighlighter.COMMA),
            new AttributesDescriptor("Dot", HlslSyntaxHighlighter.DOT),
            new AttributesDescriptor("Parentheses", HlslSyntaxHighlighter.PAREN),
            new AttributesDescriptor("Braces", HlslSyntaxHighlighter.BRACE),
            new AttributesDescriptor("Brackets", HlslSyntaxHighlighter.BRACKET),
            new AttributesDescriptor("Struct / Class name", HlslSyntaxHighlighter.STRUCT_NAME),
            new AttributesDescriptor("Identifier", HlslSyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Template type parameter", HlslSyntaxHighlighter.TEMPLATE_TYPE_PARAM),
            new AttributesDescriptor("Bad character", HlslSyntaxHighlighter.BAD_CHARACTER),
    };

    @Override
    public @Nullable Icon getIcon() {
        return HlslIcons.FILE;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new HlslSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                #include "common.hlsli"
                #define MAX_LIGHTS 8
                
                // Constant buffer for per-frame data
                cbuffer PerFrame : register(b0)
                {
                    float4x4 ViewProjection;
                    float3 CameraPosition;
                    float Time;
                };
                
                /* Vertex shader input structure */
                struct VSInput
                {
                    float3 Position : POSITION;
                    float3 Normal : NORMAL;
                    float2 TexCoord : TEXCOORD0;
                    float4 Color : COLOR0;
                };
                
                struct PSInput
                {
                    float4 Position : SV_Position;
                    float3 WorldNormal : TEXCOORD1;
                    float2 TexCoord : TEXCOORD0;
                    float4 Color : COLOR0;
                };
                
                Texture2D DiffuseMap : register(t0);
                SamplerState LinearSampler : register(s0);
                
                PSInput VSMain(VSInput input)
                {
                    PSInput output;
                    float4 worldPos = mul(float4(input.Position, 1.0f), World);
                    output.Position = mul(worldPos, ViewProjection);
                    output.WorldNormal = normalize(mul(input.Normal, (float3x3)World));
                    output.TexCoord = input.TexCoord;
                    output.Color = input.Color;
                    return output;
                }
                
                float4 PSMain(PSInput input) : SV_Target
                {
                    float4 texColor = DiffuseMap.Sample(LinearSampler, input.TexCoord);
                    float3 lightDir = normalize(float3(1.0, 1.0, -1.0));
                    float ndotl = saturate(dot(input.WorldNormal, lightDir));
                    float3 diffuse = texColor.rgb * ndotl;
                    return float4(diffuse, texColor.a);
                }
                """;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "HLSL";
    }
}
