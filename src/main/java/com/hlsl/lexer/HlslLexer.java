package com.hlsl.lexer;

import com.hlsl.psi.HlslTokenTypes;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class HlslLexer extends LexerBase {
    private CharSequence buffer;
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;
    private int state; // 0 = normal, 1 = expecting struct/cbuffer/tbuffer name, 2 = after dot (field access)

    private static final Set<String> STRUCT_DECLARING_KEYWORDS = Set.of(
            "struct", "cbuffer", "tbuffer", "class", "enum", "interface"
    );

    private static final Set<String> KEYWORDS = Set.of(
            // Control flow
            "if", "else", "for", "while", "do", "switch", "case", "default",
            "break", "continue", "return", "discard",
            // Storage/qualifier
            "struct", "cbuffer", "tbuffer", "typedef", "extern", "static",
            "volatile", "inline", "shared", "groupshared", "uniform",
            "const", "row_major", "column_major", "packoffset", "register",
            "namespace",
            // Input/output
            "in", "out", "inout",
            // Misc
            "true", "false", "NULL",
            "pass", "technique", "technique10", "technique11",
            "compile", "compile_fragment",
            "VertexShader", "PixelShader", "GeometryShader",
            "HullShader", "DomainShader", "ComputeShader",
            "export", "nointerpolation", "linear", "centroid", "noperspective",
            "sample", "precise", "globallycoherent",
            "snorm", "unorm"
    );

    private static final Set<String> TYPE_KEYWORDS = Set.of(
            // Scalar types
            "void", "bool", "int", "uint", "dword", "half", "float", "double",
            "min16float", "min10float", "min16int", "min12int", "min16uint",
            "string",
            // Vector types
            "bool1", "bool2", "bool3", "bool4",
            "int1", "int2", "int3", "int4",
            "uint1", "uint2", "uint3", "uint4",
            "half1", "half2", "half3", "half4",
            "float1", "float2", "float3", "float4",
            "double1", "double2", "double3", "double4",
            // Matrix types
            "float2x2", "float2x3", "float2x4",
            "float3x2", "float3x3", "float3x4",
            "float4x2", "float4x3", "float4x4",
            "half2x2", "half2x3", "half2x4",
            "half3x2", "half3x3", "half3x4",
            "half4x2", "half4x3", "half4x4",
            "int2x2", "int2x3", "int2x4",
            "int3x2", "int3x3", "int3x4",
            "int4x2", "int4x3", "int4x4",
            "uint2x2", "uint2x3", "uint2x4",
            "uint3x2", "uint3x3", "uint3x4",
            "uint4x2", "uint4x3", "uint4x4",
            "bool2x2", "bool2x3", "bool2x4",
            "bool3x2", "bool3x3", "bool3x4",
            "bool4x2", "bool4x3", "bool4x4",
            "double2x2", "double2x3", "double2x4",
            "double3x2", "double3x3", "double3x4",
            "double4x2", "double4x3", "double4x4",
            "vector", "matrix",
            // Texture/sampler types
            "sampler", "sampler1D", "sampler2D", "sampler3D", "samplerCUBE",
            "SamplerState", "SamplerComparisonState",
            "texture", "Texture1D", "Texture1DArray",
            "Texture2D", "Texture2DArray", "Texture2DMS", "Texture2DMSArray",
            "Texture3D", "TextureCube", "TextureCubeArray",
            // Buffer types
            "Buffer", "StructuredBuffer", "RWStructuredBuffer",
            "ByteAddressBuffer", "RWByteAddressBuffer",
            "AppendStructuredBuffer", "ConsumeStructuredBuffer",
            "RWBuffer", "RWTexture1D", "RWTexture1DArray",
            "RWTexture2D", "RWTexture2DArray", "RWTexture3D",
            // Other resource types
            "InputPatch", "OutputPatch",
            "PointStream", "LineStream", "TriangleStream",
            "RaytracingAccelerationStructure",
            "RayDesc", "RayQuery",
            "ConstantBuffer",
            // SM 6.6 descriptor heap types
            "ResourceDescriptorHeap", "SamplerDescriptorHeap"
    );

    private static final Set<String> BUILTIN_FUNCTIONS = Set.of(
            "abs", "acos", "all", "AllMemoryBarrier", "AllMemoryBarrierWithGroupSync",
            "any", "asdouble", "asfloat", "asin", "asint", "asuint", "atan", "atan2",
            "ceil", "clamp", "clip", "cos", "cosh", "countbits", "cross",
            "D3DCOLORtoUBYTE4", "ddx", "ddx_coarse", "ddx_fine", "ddy", "ddy_coarse", "ddy_fine",
            "degrees", "determinant", "DeviceMemoryBarrier", "DeviceMemoryBarrierWithGroupSync",
            "distance", "dot", "dst",
            "EvaluateAttributeAtCentroid", "EvaluateAttributeAtSample", "EvaluateAttributeSnapped",
            "exp", "exp2",
            "f16tof32", "f32tof16", "faceforward", "firstbithigh", "firstbitlow",
            "floor", "fma", "fmod", "frac", "frexp", "fwidth",
            "GetRenderTargetSampleCount", "GetRenderTargetSamplePosition",
            "GroupMemoryBarrier", "GroupMemoryBarrierWithGroupSync",
            "InterlockedAdd", "InterlockedAnd", "InterlockedCompareExchange",
            "InterlockedCompareStore", "InterlockedExchange", "InterlockedMax",
            "InterlockedMin", "InterlockedOr", "InterlockedXor",
            "isfinite", "isinf", "isnan",
            "ldexp", "length", "lerp", "lit", "log", "log10", "log2",
            "mad", "max", "min", "modf", "msad4", "mul",
            "noise", "normalize",
            "pow", "printf", "Process2DQuadTessFactorsAvg", "Process2DQuadTessFactorsMax",
            "Process2DQuadTessFactorsMin", "ProcessIsolineTessFactors",
            "ProcessQuadTessFactorsAvg", "ProcessQuadTessFactorsMax",
            "ProcessQuadTessFactorsMin", "ProcessTriTessFactorsAvg",
            "ProcessTriTessFactorsMax", "ProcessTriTessFactorsMin",
            "radians", "rcp", "reflect", "refract", "reversebits", "round", "rsqrt",
            "saturate", "sign", "sin", "sincos", "sinh", "smoothstep", "sqrt", "step",
            "tan", "tanh", "tex1D", "tex1Dbias", "tex1Dgrad", "tex1Dlod", "tex1Dproj",
            "tex2D", "tex2Dbias", "tex2Dgrad", "tex2Dlod", "tex2Dproj",
            "tex3D", "tex3Dbias", "tex3Dgrad", "tex3Dlod", "tex3Dproj",
            "texCUBE", "texCUBEbias", "texCUBEgrad", "texCUBElod", "texCUBEproj",
            "transpose", "trunc",
            "WaveActiveAllEqual", "WaveActiveBitAnd", "WaveActiveBitOr", "WaveActiveBitXor",
            "WaveActiveCountBits", "WaveActiveMax", "WaveActiveMin", "WaveActiveProduct",
            "WaveActiveSum", "WaveGetLaneCount", "WaveGetLaneIndex", "WaveIsFirstLane",
            "WavePrefixCountBits", "WavePrefixProduct", "WavePrefixSum",
            "WaveReadLaneAt", "WaveReadLaneFirst",
            "DispatchRaysIndex", "DispatchRaysDimensions", "WorldRayOrigin",
            "WorldRayDirection", "ObjectRayOrigin", "ObjectRayDirection",
            "RayTMin", "RayTCurrent", "RayFlags",
            "InstanceIndex", "InstanceID", "PrimitiveIndex",
            "ObjectToWorld3x4", "ObjectToWorld4x3", "WorldToObject3x4", "WorldToObject4x3",
            "HitKind", "TraceRay", "ReportHit", "CallShader", "IgnoreHit", "AcceptHitAndEndSearch"
    );

    private static final Set<String> SEMANTICS = Set.of(
            "SV_Position", "SV_Target", "SV_Target0", "SV_Target1", "SV_Target2",
            "SV_Target3", "SV_Target4", "SV_Target5", "SV_Target6", "SV_Target7",
            "SV_Depth", "SV_DepthGreaterEqual", "SV_DepthLessEqual",
            "SV_VertexID", "SV_InstanceID", "SV_PrimitiveID",
            "SV_IsFrontFace", "SV_SampleIndex",
            "SV_ClipDistance", "SV_ClipDistance0", "SV_ClipDistance1",
            "SV_CullDistance", "SV_CullDistance0", "SV_CullDistance1",
            "SV_Coverage", "SV_RenderTargetArrayIndex", "SV_ViewportArrayIndex",
            "SV_DispatchThreadID", "SV_GroupID", "SV_GroupIndex", "SV_GroupThreadID",
            "SV_DomainLocation", "SV_InsideTessFactor", "SV_OutputControlPointID",
            "SV_TessFactor", "SV_GSInstanceID", "SV_StencilRef",
            "POSITION", "POSITION0", "POSITION1",
            "COLOR", "COLOR0", "COLOR1",
            "TEXCOORD", "TEXCOORD0", "TEXCOORD1", "TEXCOORD2", "TEXCOORD3",
            "TEXCOORD4", "TEXCOORD5", "TEXCOORD6", "TEXCOORD7",
            "NORMAL", "NORMAL0", "NORMAL1",
            "TANGENT", "TANGENT0",
            "BINORMAL", "BINORMAL0",
            "BLENDWEIGHT", "BLENDWEIGHT0",
            "BLENDINDICES", "BLENDINDICES0",
            "PSIZE", "PSIZE0",
            "FOG", "TESSFACTOR", "VFACE", "VPOS", "DEPTH"
    );

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        this.tokenType = null;
        this.state = initialState;
        advance();
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        tokenStart = tokenEnd;
        if (tokenStart >= bufferEnd) {
            tokenType = null;
            return;
        }

        char c = buffer.charAt(tokenStart);

        // Whitespace
        if (Character.isWhitespace(c)) {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd && Character.isWhitespace(buffer.charAt(tokenEnd))) {
                tokenEnd++;
            }
            tokenType = com.intellij.psi.TokenType.WHITE_SPACE;
            return;
        }

        // Line comment
        if (c == '/' && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '/') {
            tokenEnd = tokenStart + 2;
            while (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) != '\n') {
                tokenEnd++;
            }
            tokenType = HlslTokenTypes.LINE_COMMENT;
            return;
        }

        // Block comment
        if (c == '/' && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '*') {
            tokenEnd = tokenStart + 2;
            while (tokenEnd + 1 < bufferEnd) {
                if (buffer.charAt(tokenEnd) == '*' && buffer.charAt(tokenEnd + 1) == '/') {
                    tokenEnd += 2;
                    tokenType = HlslTokenTypes.BLOCK_COMMENT;
                    return;
                }
                tokenEnd++;
            }
            tokenEnd = bufferEnd;
            tokenType = HlslTokenTypes.BLOCK_COMMENT;
            return;
        }

        // Preprocessor directive
        if (c == '#') {
            tokenEnd = tokenStart + 1;
            // consume the directive including line continuations
            while (tokenEnd < bufferEnd) {
                char pc = buffer.charAt(tokenEnd);
                if (pc == '\\' && tokenEnd + 1 < bufferEnd && buffer.charAt(tokenEnd + 1) == '\n') {
                    tokenEnd += 2;
                    continue;
                }
                if (pc == '\n') break;
                tokenEnd++;
            }
            tokenType = HlslTokenTypes.PREPROCESSOR;
            state = 0;
            return;
        }

        // String literal
        if (c == '"') {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd) {
                char sc = buffer.charAt(tokenEnd);
                if (sc == '\\' && tokenEnd + 1 < bufferEnd) {
                    tokenEnd += 2;
                    continue;
                }
                if (sc == '"') {
                    tokenEnd++;
                    break;
                }
                if (sc == '\n') break;
                tokenEnd++;
            }
            tokenType = HlslTokenTypes.STRING;
            return;
        }

        // Number literal (int, float, hex)
        if (Character.isDigit(c) || (c == '.' && tokenStart + 1 < bufferEnd && Character.isDigit(buffer.charAt(tokenStart + 1)))) {
            tokenEnd = tokenStart;
            if (c == '0' && tokenEnd + 1 < bufferEnd && (buffer.charAt(tokenEnd + 1) == 'x' || buffer.charAt(tokenEnd + 1) == 'X')) {
                tokenEnd += 2;
                while (tokenEnd < bufferEnd && isHexDigit(buffer.charAt(tokenEnd))) {
                    tokenEnd++;
                }
            } else {
                while (tokenEnd < bufferEnd && Character.isDigit(buffer.charAt(tokenEnd))) {
                    tokenEnd++;
                }
                if (tokenEnd < bufferEnd && buffer.charAt(tokenEnd) == '.') {
                    tokenEnd++;
                    while (tokenEnd < bufferEnd && Character.isDigit(buffer.charAt(tokenEnd))) {
                        tokenEnd++;
                    }
                }
                if (tokenEnd < bufferEnd && (buffer.charAt(tokenEnd) == 'e' || buffer.charAt(tokenEnd) == 'E')) {
                    tokenEnd++;
                    if (tokenEnd < bufferEnd && (buffer.charAt(tokenEnd) == '+' || buffer.charAt(tokenEnd) == '-')) {
                        tokenEnd++;
                    }
                    while (tokenEnd < bufferEnd && Character.isDigit(buffer.charAt(tokenEnd))) {
                        tokenEnd++;
                    }
                }
            }
            // Suffix: f, h, u, l, etc
            if (tokenEnd < bufferEnd) {
                char s = buffer.charAt(tokenEnd);
                if (s == 'f' || s == 'F' || s == 'h' || s == 'H' || s == 'u' || s == 'U' || s == 'l' || s == 'L') {
                    tokenEnd++;
                }
            }
            tokenType = HlslTokenTypes.NUMBER;
            state = 0;
            return;
        }

        // Identifier or keyword
        if (Character.isLetter(c) || c == '_') {
            tokenEnd = tokenStart + 1;
            while (tokenEnd < bufferEnd && (Character.isLetterOrDigit(buffer.charAt(tokenEnd)) || buffer.charAt(tokenEnd) == '_')) {
                tokenEnd++;
            }
            String word = buffer.subSequence(tokenStart, tokenEnd).toString();

            if (state == 2) {
                tokenType = isFollowedByCallParen(tokenEnd)
                        ? HlslTokenTypes.INSTANCE_METHOD_CALL
                        : HlslTokenTypes.FIELD_ACCESS;
                state = 0;
            } else if (KEYWORDS.contains(word)) {
                tokenType = HlslTokenTypes.KEYWORD;
                if (STRUCT_DECLARING_KEYWORDS.contains(word)) {
                    state = 1;
                } else {
                    state = 0;
                }
            } else if (TYPE_KEYWORDS.contains(word)) {
                tokenType = HlslTokenTypes.TYPE_KEYWORD;
                state = 0;
            } else if (BUILTIN_FUNCTIONS.contains(word)) {
                tokenType = HlslTokenTypes.BUILTIN_FUNCTION;
                state = 0;
            } else if (SEMANTICS.contains(word)) {
                tokenType = HlslTokenTypes.SEMANTIC;
                state = 0;
            } else if (state == 1) {
                tokenType = HlslTokenTypes.STRUCT_NAME;
                state = 0;
            } else if (isFollowedByCallParen(tokenEnd)) {
                tokenType = HlslTokenTypes.FUNCTION_CALL;
                state = 0;
            } else {
                tokenType = HlslTokenTypes.IDENTIFIER;
            }
            return;
        }

        // Punctuation
        tokenEnd = tokenStart + 1;
        switch (c) {
            case ';': tokenType = HlslTokenTypes.SEMICOLON; state = 0; return;
            case ',': tokenType = HlslTokenTypes.COMMA; state = 0; return;
            case '.': tokenType = HlslTokenTypes.DOT; state = 2; return;
            case ':': tokenType = HlslTokenTypes.COLON; state = 0; return;
            case '(': tokenType = HlslTokenTypes.LPAREN; state = 0; return;
            case ')': tokenType = HlslTokenTypes.RPAREN; state = 0; return;
            case '{': tokenType = HlslTokenTypes.LBRACE; state = 0; return;
            case '}': tokenType = HlslTokenTypes.RBRACE; state = 0; return;
            case '[': tokenType = HlslTokenTypes.LBRACKET; state = 0; return;
            case ']': tokenType = HlslTokenTypes.RBRACKET; state = 0; return;
            case '+': case '-': case '*': case '/': case '%':
            case '&': case '|': case '^': case '~': case '!':
            case '<': case '>': case '=': case '?':
                // Consume multi-character operators
                if (tokenEnd < bufferEnd) {
                    char next = buffer.charAt(tokenEnd);
                    if ((c == '=' && next == '=') || (c == '!' && next == '=') ||
                        (c == '<' && next == '=') || (c == '>' && next == '=') ||
                        (c == '&' && next == '&') || (c == '|' && next == '|') ||
                        (c == '+' && next == '+') || (c == '-' && next == '-') ||
                        (c == '<' && next == '<') || (c == '>' && next == '>') ||
                        (c == '+' && next == '=') || (c == '-' && next == '=') ||
                        (c == '*' && next == '=') || (c == '/' && next == '=') ||
                        (c == '%' && next == '=') || (c == '&' && next == '=') ||
                        (c == '|' && next == '=') || (c == '^' && next == '=')) {
                        tokenEnd++;
                    }
                }
                tokenType = HlslTokenTypes.OPERATOR;
                return;
            default:
                tokenType = com.intellij.psi.TokenType.BAD_CHARACTER;
        }
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private boolean isFollowedByCallParen(int fromOffset) {
        int i = fromOffset;
        while (i < bufferEnd && Character.isWhitespace(buffer.charAt(i))) {
            i++;
        }
        return i < bufferEnd && buffer.charAt(i) == '(';
    }
}
