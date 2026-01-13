package com.strumenta.kolasu.languageserver.semanticHighlighting

enum class SemanticTokenModifier(val legendName: String, val bit: Int) {
	DECLARATION("declaration", 1),
	DEFINITION("definition", 2),
	READ_ONLY("readonly", 4),
	STATIC("static", 8),
	DEPRECATED("deprecated", 16),
	ABSTRACT("abstract", 32),
	ASYNCHRONOUS("async", 64),
	MODIFICATION("modification", 128),
	DOCUMENTATION("documentation", 256),
	DEFAULT_LIBRARY("defaultLibrary", 512)
}