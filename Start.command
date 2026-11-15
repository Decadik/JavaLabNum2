#!/bin/bash
cd "$(dirname "$0")" || exit 1
if ! javac -encoding UTF-8 Main.java Window.java; then
    echo "Не удалось собрать программу. Проверьте, что установлен JDK."
    read -r -p "Нажмите Enter для выхода..."
    exit 1
fi
java Window
