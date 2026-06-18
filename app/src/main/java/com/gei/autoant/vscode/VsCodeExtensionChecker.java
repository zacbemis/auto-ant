package com.gei.autoant.vscode;

@FunctionalInterface
public interface VsCodeExtensionChecker {
    String FILE_WATCHER_EXTENSION_ID = "appulate.filewatcher";

    boolean isInstalled(String extensionId);
}