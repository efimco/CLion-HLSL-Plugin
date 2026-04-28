package com.hlsl.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public interface HlslTokenTypes {
    // Keywords - control flow
    IElementType KEYWORD = new HlslTokenType("KEYWORD");

    // Types
    IElementType TYPE_KEYWORD = new HlslTokenType("TYPE_KEYWORD");

    // Semantic keywords
    IElementType SEMANTIC = new HlslTokenType("SEMANTIC");

    // Preprocessor
    IElementType PREPROCESSOR = new HlslTokenType("PREPROCESSOR");

    // Built-in functions
    IElementType BUILTIN_FUNCTION = new HlslTokenType("BUILTIN_FUNCTION");
    IElementType FUNCTION_CALL = new HlslTokenType("FUNCTION_CALL");
    IElementType INSTANCE_METHOD_CALL = new HlslTokenType("INSTANCE_METHOD_CALL");
    IElementType FIELD_ACCESS = new HlslTokenType("FIELD_ACCESS");

    // Literals
    IElementType NUMBER = new HlslTokenType("NUMBER");
    IElementType STRING = new HlslTokenType("STRING");

    // Identifiers
    IElementType IDENTIFIER = new HlslTokenType("IDENTIFIER");
    IElementType STRUCT_NAME = new HlslTokenType("STRUCT_NAME");
    IElementType TEMPLATE_TYPE_PARAM = new HlslTokenType("TEMPLATE_TYPE_PARAM");

    // Comments
    IElementType LINE_COMMENT = new HlslTokenType("LINE_COMMENT");
    IElementType BLOCK_COMMENT = new HlslTokenType("BLOCK_COMMENT");

    // Operators and punctuation
    IElementType SEMICOLON = new HlslTokenType("SEMICOLON");
    IElementType COMMA = new HlslTokenType("COMMA");
    IElementType DOT = new HlslTokenType("DOT");
    IElementType COLON = new HlslTokenType("COLON");
    IElementType LPAREN = new HlslTokenType("LPAREN");
    IElementType RPAREN = new HlslTokenType("RPAREN");
    IElementType LBRACE = new HlslTokenType("LBRACE");
    IElementType RBRACE = new HlslTokenType("RBRACE");
    IElementType LBRACKET = new HlslTokenType("LBRACKET");
    IElementType RBRACKET = new HlslTokenType("RBRACKET");
    IElementType OPERATOR = new HlslTokenType("OPERATOR");

    // Token sets
    TokenSet COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT);
    TokenSet WHITESPACES = TokenSet.create(com.intellij.psi.TokenType.WHITE_SPACE);
    TokenSet STRINGS = TokenSet.create(STRING);
}
