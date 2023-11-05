# Kolasu language server library

Given a Kolasu parser, create a language server that communicates with lsp compliant code editors. Essentially providing editors for the language.

## Structure of the project

This repository contains four projects:

1. `library` contains the code to create a language server and respond to lsp requests. Also contains helpers to test language server. These are published as JARs to Github packages.
2. `plugin` contains the code of a gradle plugin that takes care of adding all the dependencies and adds two tasks: One to create the vscode extension and another to launch it as development extension in an editor.
3. `examples/entity` is a toy example that uses the language server in its default configuration. Thanks to some conventions, a single line is enough to configure the language server in this case.
4. `examples/rpg` uses Strumenta's RPG parser and this library to create an editor for RPG files. It supports go to definition and references requests thanks to its symbol resolution module. 

## How to use

The easiest way to use this library is to:

1. add this repository to the sources where gradle looks for plugins
2. add the `com.strumenta.kolasu.language-server-plugin` version `0.0.0` to the list of gradle plugins
3. optionally configure the language server by adding a gradle extension called `languageServer`
4. run the `createVscodeExtension` gradle task
5. run the `launchVscodeExtension` gradle task

## Features

Only a subset of the [Language Server Protocol] messages are supported for now.

Thankfully, most of the infrastructure messages are covered. This include:
* [Lifecycle messages] like initialize and exit
* [Document synchronization] messages like did open or change a file
* [Workspace features] like workspace symbols and file watchers
* [Window features] like showing notifications and progress of tasks

From here on, development can focus on implementing [language features]. For now, only `definition`, `references` and `documentSymbols` are implemented and tested.

### Initialization

Here is a table to explain the initialization process of the language server:

| Client                                | Server                                    | Notes                                                                        |
|---------------------------------------|-------------------------------------------|------------------------------------------------------------------------------|
| initialize(folders)                   |                                           | store folders                                                                |
|                                       | initialize(capabilities)                  | capabilities increase with symbol resolution module                          |
| didOpen(uri, text)                    |                                           | ignored since index is not initialized                                       |
| documentSymbol(uri)                   |                                           | ignored since index is not initialized                                       |
| setTrace(level)                       |                                           | store level                                                                  |
| initialized()                         |                                           |                                                                              |
|                                       | registerFileWatchers(folders, extensions) | client detects even files created outside the editor                         |
|                                       | registerConfigurationWatchers(section)    | parsing behavior can be configured from the editor                           |
| didChangeConfiguration(configuration) |                                           | store configuration, reset index, parse all files in workspace and for each: |
|                                       | publishDiagnostics(uri, diagnostics[])    |                                                                              |
|                                       | reportProgress(percent)                   | based on the amount of bytes parsed / total amount to parse                  |

If the language server has a symbol resolution module, its capabilities will include the `definition` and `find usages` features, otherwise only parsing errors and document outlines are reported.

[Language Server Protocol]: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/
[Lifecycle messages]: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#lifeCycleMessages-side
[Document synchronization]: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textSynchronization-side
[Window features]: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#windowFeatures-side
[Workspace features]: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspaceFeatures-side
[language features]: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#languageFeatures-side