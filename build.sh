#!/usr/bin/env bash
set -e
command -v gradle >/dev/null || { echo "Instala Gradle 8.10+ y JDK 21 primero."; exit 1; }
gradle build
echo "Listo: build/libs/luzviva-1.0.0.jar"
