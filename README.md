# Jew
Navegador Android hecho con WebView, Java y Gradle.

Paquete: `com.CraftPlay777.jew`.

Incluye barra inteligente (URL o Google), pestañas, atrás/adelante, recarga, inicio, marcadores, historial, compartir, indicador de progreso, selector de archivos y descargas mediante DownloadManager.

## APK por GitHub Actions
Subir este proyecto a la rama `main`, abrir **Actions > Build Jew APK > Run workflow** o hacer push. Descargar el artefacto **Jew-debug-apk**.

Este APK es debug e instalable para pruebas. Para publicación es necesario firmar una versión release. Las pestañas comparten cookies y almacenamiento WebView; esta versión aún no incluye modo incógnito.

Nota: `targetSdk 28` es provisional para maximizar compatibilidad de la primera versión; hay que modernizarlo para publicar en tiendas y actualizar las políticas de permisos/almacenamiento.
