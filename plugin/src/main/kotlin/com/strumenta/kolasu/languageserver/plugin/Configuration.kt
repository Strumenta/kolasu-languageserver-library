package com.strumenta.kolasu.languageserver.plugin

import java.nio.file.Path

open class Configuration {
    lateinit var editor: String
    lateinit var language: String
    lateinit var version: String
    lateinit var publisher: String
    lateinit var repository: String
    lateinit var fileExtensions: List<String>
    lateinit var textmateGrammarScope: String
    lateinit var entryPointPath: Path
    lateinit var serverJarPath: Path
    lateinit var examplesPath: Path
    lateinit var textmateGrammarPath: Path
    lateinit var logoPath: Path
    lateinit var fileIconPath: Path
    lateinit var languageClientPath: Path
    lateinit var packageDefinitionPath: Path
    lateinit var licensePath: Path
    lateinit var outputPath: Path
}
