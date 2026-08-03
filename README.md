# Maldita Riviera Curator

Aplicación Android privada para revisar productos de Paris Fashion Shops y construir la primera colección de Maldita Riviera sin realizar compras automáticas.

## Funciones del MVP

- Navegador integrado para iniciar sesión personalmente en Paris Fashion Shops.
- Análisis de la ficha que la usuaria tenga abierta.
- Guardado local de título, proveedor, precio visible, imagen, URL, color y categoría.
- Puntuación heurística de afinidad con el universo Maldita Riviera.
- Estados manuales: **Encaja**, **Revisar** y **Descartar**.
- Generación de combinaciones entre corsés/tops y partes inferiores.
- Exportación de la selección como JSON.
- Ninguna función de compra automática.
- Las credenciales y cookies del navegador no se exportan al repositorio.

## APK

Cada cambio en `main` ejecuta el workflow **Build Android APK**. Cuando finalice:

1. Abre la pestaña **Actions** del repositorio.
2. Entra en la ejecución más reciente de **Build Android APK**.
3. Descarga el artefacto `maldita-riviera-curator-apk`.
4. Descomprime el ZIP e instala `app-debug.apk` en Android.

## Privacidad

Los productos guardados se almacenan únicamente en `SharedPreferences` dentro del teléfono. La app analiza solo la página que la usuaria abre y decide guardar. No realiza rastreo masivo ni automatiza compras.

## Estado

MVP 0.1.0. La extracción depende de la estructura actual del sitio; si Paris Fashion Shops cambia su HTML, habrá que ajustar los selectores.
