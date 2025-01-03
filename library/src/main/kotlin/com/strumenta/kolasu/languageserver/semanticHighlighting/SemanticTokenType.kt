package com.strumenta.kolasu.languageserver.semanticHighlighting

enum class SemanticTokenType (val legendName: String) {
    NAMESPACE("namespace"),
    CLASS("class"),
    ENUM("enum"),
    INTERFACE("interface"),
    STRUCT("struct"),
    TYPE_PARAMETER("typeParameter"),
    TYPE("type"),
    PARAMETER("parameter"),
    VARIABLE("variable"),
    PROPERTY("property"),
    ENUM_MEMBER("enumMember"),
    DECORATOR("decorator"),
    EVENT("event"),
    FUNCTION("function"),
    METHOD("method"),
    MACRO("macro"),
    LABEL("label"),
    COMMENT("comment"),
    STRING("string"),
    KEYWORD("keyword"),
    NUMBER("number"),
    REGULAR_EXPRESSION("regexp"),
    OPERATOR("operator")
}
