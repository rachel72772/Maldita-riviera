# Maldita Riviera Curator

Aplicación Android privada para revisar productos de Paris Fashion Shops y construir la primera colección de Maldita Riviera sin realizar compras automáticas.

## Versión 0.2.1

- Navegador integrado con sesión gestionada personalmente por la usuaria.
- **Buscar universo** rota búsquedas afines: conjuntos con corsé, encaje, satén, negro, marfil y otras líneas del perfil.
- **Curar página** analiza las tarjetas visibles de una búsqueda, marca o categoría.
- Solo guarda automáticamente candidatos con afinidad suficiente.
- Coincidencias fuertes pasan a **Encaja**; propuestas prometedoras quedan en **Revisar**.
- Distingue una ficha de producto de una página general y elimina guardados erróneos de listados de mayoristas.
- Perfil estético: corsetería, encaje, satén, tul, drapeados, lunares, minifaldas, vestidos ajustados y paleta negro, marfil, blanco, chocolate y burdeos.
- Prioriza proveedores ya identificados como Giorgia, Mochy, Frime Paris, F&P, Copperose, Soy & Co, Unika Paris y Jolio & Co.
- La pestaña **Looks** muestra primero conjuntos completos y luego combina corsés/tops con partes inferiores compatibles.
- La compatibilidad considera afinidad, color, materiales y proveedor común.
- Firma de desarrollo estable para que las versiones posteriores puedan instalarse como actualización.
- Exportación de la selección como JSON.
- Ninguna función de compra automática.

## Uso

1. Abre Paris Fashion Shops desde **Explorar** e inicia sesión tú misma.
2. Pulsa **Buscar universo** o navega hasta una marca/categoría.
3. Cuando veas tarjetas de productos, pulsa **Curar página**.
4. Revisa el resultado en **Guardados**.
5. Consulta conjuntos completos y combinaciones en **Looks**.
6. Usa **Guardar ficha** únicamente dentro de la página concreta de un producto.

## Instalación de 0.2.1

La versión 0.1.0 instalada anteriormente utilizó una firma temporal de GitHub Actions. Desinstálala una sola vez antes de instalar 0.2.1. Desde 0.2.1, las futuras APK generadas por este repositorio usarán la misma firma de desarrollo y podrán actualizar la aplicación directamente.

## APK

Cada cambio en `main` ejecuta el workflow **Build Android APK**. Cuando finalice:

1. Abre la pestaña **Actions** del repositorio.
2. Entra en la ejecución más reciente de **Build Android APK**.
3. Descarga el artefacto `maldita-riviera-curator-apk`.
4. Descomprime el ZIP e instala `app-debug.apk` en Android.

## Privacidad

Los productos guardados se almacenan únicamente en `SharedPreferences` dentro del teléfono. La app analiza solo la página abierta cuando se pulsa un botón. No realiza rastreo masivo, no funciona en segundo plano, no automatiza compras y no exporta credenciales ni cookies.

## Limitaciones

La extracción depende de la estructura actual del sitio. Si Paris Fashion Shops modifica su HTML, los selectores deberán actualizarse. La app propone productos por afinidad textual y visual disponible en las fichas; la decisión final y la comprobación de calidad, tallaje y costes siguen siendo manuales.

La clave incluida es exclusivamente de desarrollo y no debe utilizarse para publicar una versión comercial en Google Play.
