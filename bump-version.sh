#!/bin/bash
# Script para incrementar la versión de la app
# Uso: ./bump-version.sh [major|minor|patch]
# Por defecto incrementa el patch (ej: 1.0.4 -> 1.0.5)

BUILD_GRADLE="app/build.gradle.kts"

# Obtener versión actual
CURRENT_VERSION=$(grep 'versionName = ' $BUILD_GRADLE | sed 's/.*"\(.*\)".*/\1/')
CURRENT_CODE=$(grep 'versionCode = ' $BUILD_GRADLE | sed 's/.*= \([0-9]*\).*/\1/')

echo "Versión actual: $CURRENT_VERSION (code: $CURRENT_CODE)"

# Separar major.minor.patch
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

# Determinar qué incrementar
case "${1:-patch}" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch|*)
        PATCH=$((PATCH + 1))
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
NEW_CODE=$((CURRENT_CODE + 1))

echo "Nueva versión: $NEW_VERSION (code: $NEW_CODE)"

# Actualizar build.gradle.kts
sed -i '' "s/versionCode = $CURRENT_CODE/versionCode = $NEW_CODE/" $BUILD_GRADLE
sed -i '' "s/versionName = \"$CURRENT_VERSION\"/versionName = \"$NEW_VERSION\"/" $BUILD_GRADLE

echo "✅ Versión actualizada en $BUILD_GRADLE"
echo ""
echo "Para generar el APK:"
echo "  ./gradlew assembleRelease"
