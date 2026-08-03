# Maldita Riviera Curator

Aplicación Android para realizar scouting y curación estética en el catálogo público de Paris Fashion Shops, sin compras automáticas.

## Versión 0.3.0 — modo público

- Funciona sin crear una cuenta profesional.
- Bloquea y abandona las rutas de registro, inicio de sesión y alta empresarial.
- Detecta páginas que solicitan número de IVA y justificante de empresa y vuelve al catálogo público.
- Intenta cerrar ventanas y avisos que bloquean la navegación pública.
- **Buscar universo** rota búsquedas afines: corsetería, encaje, satén, conjuntos, minifaldas, negro, marfil, burdeos y proveedores ya identificados.
- **Curar visibles** analiza las tarjetas que aparecen en una búsqueda, marca o categoría.
- **Guardar ficha** conserva una prenda concreta cuando su página individual es pública.
- Los productos se clasifican como **Encaja**, **Revisar** o **Descartar**.
- La pestaña **Looks** muestra conjuntos completos y combina corsés/tops con partes inferiores compatibles.
- Mantiene los guardados existentes de versiones anteriores.
- La firma de desarrollo permanece estable, de modo que 0.3.0 puede instalarse como actualización sobre 0.2.1.

## Datos pendientes en modo público

Sin acceso profesional pueden permanecer ocultos:

- precio mayorista;
- unidades del pack;
- stock;
- tallas y colores completos;
- transporte y condiciones finales.

La aplicación trata estos artículos como una preselección visual. La decisión de compra y el cálculo comercial se realizan más adelante.

## Uso

1. Abre la app y permanece en **Explorar**.
2. Pulsa **Buscar universo** para lanzar la siguiente búsqueda de nuestro perfil.
3. Desplázate por el listado para cargar tarjetas.
4. Pulsa **Curar visibles**.
5. Revisa el resultado en **Guardados**.
6. Marca manualmente **Encaja**, **Revisar** o **Descartar**.
7. Consulta **Looks** para ver conjuntos y combinaciones provisionales.
8. Pulsa **Exportar** para compartir la selección en formato JSON.

El botón **Catálogo público** vuelve a la portada. Si la web intenta enviarte a un formulario profesional, la app lo bloquea y regresa automáticamente al catálogo.

## APK

Cada cambio en `main` ejecuta el workflow **Build Android APK**. Cuando finalice:

1. Abre la pestaña **Actions** del repositorio.
2. Entra en la ejecución más reciente de **Build Android APK**.
3. Descarga el artefacto `maldita-riviera-curator-apk`.
4. Descomprime el ZIP e instala `app-debug.apk`.

## Privacidad

Los productos guardados se almacenan únicamente en `SharedPreferences` dentro del teléfono. La app analiza la página abierta cuando se pulsa un botón. No funciona en segundo plano, no automatiza compras y no rellena ni exporta credenciales o datos empresariales.

## Limitaciones

La extracción depende de la estructura pública del sitio. Si Paris Fashion Shops modifica su HTML o restringe nuevas secciones, los selectores deberán actualizarse. La clave incluida en el repositorio es exclusivamente de desarrollo y no debe utilizarse para publicar una versión comercial en Google Play.
