# Kolasu language server library

Given a Kolasu parser, create a language server that communicates with lsp compliant code editors. Essentially providing editors for the language.

### Initialization

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

### User actions

