package com.malditariviera.curator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://parisfashionshops.com/es/women/wholesalers";
    private static final String PREFS = "maldita_riviera_curator";
    private static final String PRODUCTS_KEY = "saved_products";
    private static final String SEARCH_INDEX_KEY = "taste_search_index";

    private static final int INK = Color.rgb(15, 15, 18);
    private static final int IVORY = Color.rgb(247, 242, 233);
    private static final int GOLD = Color.rgb(183, 149, 100);
    private static final int MUTED = Color.rgb(105, 101, 96);
    private static final int GREEN = Color.rgb(48, 113, 78);
    private static final int RED = Color.rgb(142, 54, 62);
    private static final int SOFT_BLACK = Color.rgb(40, 39, 43);

    private static final String[] TASTE_SEARCHES = {
        "conjunto corsé encaje",
        "conjunto satén falda",
        "vestido corsé",
        "top corsé falda",
        "conjunto negro encaje",
        "conjunto marfil satén"
    };

    private SharedPreferences preferences;
    private JSONArray products = new JSONArray();
    private final ExecutorService imagePool = Executors.newFixedThreadPool(3);

    private View exploreView;
    private View savedView;
    private View looksView;
    private WebView browser;
    private TextView browserStatus;
    private TextView savedTitle;
    private TextView looksTitle;
    private LinearLayout savedList;
    private LinearLayout looksList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadProducts();
        migrateSavedProducts();
        buildInterface();
        setupBrowser();
        showExplore();

        if (savedInstanceState == null) {
            browser.loadUrl(BASE_URL);
        } else {
            browser.restoreState(savedInstanceState);
        }

        if (!preferences.getBoolean("privacy_notice_v2_seen", false)) {
            new AlertDialog.Builder(this)
                .setTitle("Selección inteligente")
                .setMessage(
                    "Inicia sesión tú misma. La app analiza únicamente la ficha o el listado que tengas abierto, " +
                    "selecciona prendas según el perfil Maldita Riviera y guarda los resultados en este teléfono. " +
                    "No compra, no rastrea en segundo plano y no exporta tu contraseña."
                )
                .setPositiveButton("Entendido", (dialog, which) ->
                    preferences.edit().putBoolean("privacy_notice_v2_seen", true).apply())
                .show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        browser.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (exploreView.getVisibility() == View.VISIBLE && browser.canGoBack()) {
            browser.goBack();
        } else if (exploreView.getVisibility() != View.VISIBLE) {
            showExplore();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        imagePool.shutdownNow();
        if (browser != null) browser.destroy();
        super.onDestroy();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(IVORY);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setBackgroundColor(INK);

        TextView title = label("MALDITA RIVIERA", 21, Color.WHITE, true);
        title.setLetterSpacing(.13f);
        header.addView(title);

        TextView subtitle = label("CURADOR DE COLECCIÓN · MEDITERRANEAN AFTER DARK", 10, Color.rgb(198, 180, 155), false);
        subtitle.setLetterSpacing(.08f);
        header.addView(subtitle);

        root.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(8), dp(7), dp(8), dp(7));
        navigation.setBackgroundColor(Color.rgb(28, 27, 30));

        navigation.addView(navButton("Explorar", this::showExplore), weightedButtonParams());
        navigation.addView(navButton("Guardados", this::showSaved), weightedButtonParams());
        navigation.addView(navButton("Looks", this::showLooks), weightedButtonParams());
        root.addView(navigation);

        FrameLayout content = new FrameLayout(this);
        exploreView = buildExploreView();
        savedView = buildSavedView();
        looksView = buildLooksView();
        content.addView(exploreView, matchParams());
        content.addView(savedView, matchParams());
        content.addView(looksView, matchParams());
        root.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        setContentView(root);
    }

    private View buildExploreView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        browser = new WebView(this);
        page.addView(browser, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(10), dp(8), dp(10), dp(10));
        controls.setBackgroundColor(INK);

        browserStatus = label("Abriendo Paris Fashion Shops…", 11, Color.rgb(218, 209, 197), false);
        browserStatus.setSingleLine(true);
        controls.addView(browserStatus, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(23)
        ));

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.addView(
            actionButton("Inicio", () -> browser.loadUrl(BASE_URL), Color.rgb(51, 49, 53), Color.WHITE),
            buttonParams(1f, dp(4))
        );
        firstRow.addView(
            actionButton("Buscar universo", this::launchTasteSearch, Color.rgb(83, 67, 52), Color.WHITE),
            buttonParams(2f, dp(4))
        );
        controls.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setPadding(0, dp(7), 0, 0);
        secondRow.addView(
            actionButton("Curar página", this::curateVisiblePage, GOLD, INK),
            buttonParams(2f, dp(4))
        );
        secondRow.addView(
            actionButton("Guardar ficha", this::captureCurrentProduct, SOFT_BLACK, Color.WHITE),
            buttonParams(1f, dp(4))
        );
        controls.addView(secondRow);

        page.addView(controls);
        return page;
    }

    private View buildSavedView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(12), dp(12), 0);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);

        savedTitle = label("Selección inteligente", 20, INK, true);
        heading.addView(savedTitle, new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ));

        Button export = actionButton("Exportar", this::shareSelection, Color.TRANSPARENT, INK);
        export.setBackground(panelDrawable(Color.TRANSPARENT, GOLD, 1, 12));
        heading.addView(export, new LinearLayout.LayoutParams(dp(100), dp(42)));
        page.addView(heading);

        TextView help = label(
            "Encaja se asigna automáticamente a las coincidencias fuertes; Revisar conserva propuestas prometedoras.",
            12, MUTED, false
        );
        help.setPadding(0, dp(5), 0, dp(10));
        page.addView(help);

        ScrollView scroll = new ScrollView(this);
        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);
        savedList.setPadding(0, 0, 0, dp(20));
        scroll.addView(savedList);
        page.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        return page;
    }

    private View buildLooksView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(12), dp(12), 0);

        looksTitle = label("Combinaciones sugeridas", 20, INK, true);
        page.addView(looksTitle);

        TextView help = label(
            "Primero muestra conjuntos completos y después combina corsés/tops con partes inferiores compatibles.",
            12, MUTED, false
        );
        help.setPadding(0, dp(5), 0, dp(10));
        page.addView(help);

        ScrollView scroll = new ScrollView(this);
        looksList = new LinearLayout(this);
        looksList.setOrientation(LinearLayout.VERTICAL);
        looksList.setPadding(0, 0, 0, dp(20));
        scroll.addView(looksList);
        page.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        return page;
    }

    private void setupBrowser() {
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(browser, true);

        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    toast("No se puede abrir este enlace.");
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String shortUrl = url == null
                    ? "Página cargada"
                    : url.replace("https://", "").replace("http://", "");
                browserStatus.setText(shortUrl);
            }
        });
    }

    private void launchTasteSearch() {
        int index = preferences.getInt(SEARCH_INDEX_KEY, 0);
        String query = TASTE_SEARCHES[index % TASTE_SEARCHES.length];
        preferences.edit().putInt(SEARCH_INDEX_KEY, (index + 1) % TASTE_SEARCHES.length).apply();

        browserStatus.setText("Buscando: " + query);
        String escaped = JSONObject.quote(query);
        String script = String.format(Locale.ROOT, """
            (() => {
              const query = %s;
              const inputs = [...document.querySelectorAll('input')];
              const input = inputs.find(el => {
                const hint = ((el.placeholder || '') + ' ' + (el.getAttribute('aria-label') || '')).toLowerCase();
                return hint.includes('buscar') || hint.includes('search');
              });
              if (!input) return JSON.stringify({ok:false, reason:'input'});
              const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
              if (setter) setter.call(input, query); else input.value = query;
              input.focus();
              input.dispatchEvent(new Event('input', {bubbles:true}));
              input.dispatchEvent(new Event('change', {bubbles:true}));
              const form = input.closest('form');
              const button = form?.querySelector('button[type="submit"], input[type="submit"]')
                || input.parentElement?.querySelector('button')
                || input.parentElement?.parentElement?.querySelector('button');
              if (button) {
                button.click();
                return JSON.stringify({ok:true, submitted:true});
              }
              input.dispatchEvent(new KeyboardEvent('keydown', {
                key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true
              }));
              input.dispatchEvent(new KeyboardEvent('keyup', {
                key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true
              }));
              if (form && typeof form.requestSubmit === 'function') form.requestSubmit();
              return JSON.stringify({ok:true, submitted:false});
            })()
            """, escaped);

        browser.evaluateJavascript(script, raw -> {
            try {
                JSONObject result = decodeObject(raw);
                if (!result.optBoolean("ok")) {
                    toast("No encontré el buscador en esta página. Pulsa Inicio y prueba de nuevo.");
                } else if (!result.optBoolean("submitted")) {
                    toast("Consulta escrita. Pulsa la lupa si la búsqueda no comienza sola.");
                }
            } catch (Exception error) {
                toast("No se pudo preparar la búsqueda.");
            }
        });
    }

    private void curateVisiblePage() {
        String currentUrl = browser.getUrl();
        if (currentUrl == null || !currentUrl.contains("parisfashionshops.com")) {
            toast("Abre primero un listado de Paris Fashion Shops.");
            return;
        }

        browserStatus.setText("Seleccionando prendas visibles…");
        String script = """
            (() => {
              const clean = value => (value || '').replace(/\s+/g, ' ').trim();
              const absolute = value => {
                try { return new URL(value, location.href).href; } catch (_) { return ''; }
              };
              const productWords = /(conjunto|ensemble|two[ -]?piece|2 piezas|set |co-ord|cors[eé]|bustier|falda|skirt|vestido|dress|top|body|short|pantal[oó]n|trouser|combinaison)/i;
              const ignoreWords = /(pol[ií]tica|privacidad|contacto|iniciar sesi[oó]n|crear una cuenta|todas nuestras marcas)/i;
              const seen = new Set();
              const results = [];
              const anchors = [...document.querySelectorAll('a[href]')];

              for (const anchor of anchors) {
                const href = absolute(anchor.getAttribute('href') || anchor.href);
                if (!href || !href.includes('parisfashionshops.com') || href === location.href || seen.has(href)) continue;

                const card = anchor.closest(
                  'article, li, [class*="product"], [class*="Product"], [class*="card"], [class*="Card"], [class*="item"], [class*="Item"]'
                ) || anchor;
                const text = clean(card.innerText || anchor.innerText || '');
                if (text.length < 3 || text.length > 1800 || ignoreWords.test(text) || !productWords.test(text)) continue;

                const img = card.querySelector('img') || anchor.querySelector('img');
                const image = absolute(
                  img?.currentSrc || img?.src || img?.getAttribute('data-src') || img?.getAttribute('data-lazy-src') || ''
                );
                const alt = clean(img?.alt || anchor.getAttribute('aria-label') || '');
                const lines = (card.innerText || '').split(/\n+/).map(clean).filter(Boolean);
                const title = alt || lines.find(line => productWords.test(line) && line.length < 180) || lines[0] || text.slice(0, 160);

                seen.add(href);
                results.push({title, body:text, image, url:href});
                if (results.length >= 120) break;
              }
              return JSON.stringify(results);
            })()
            """;

        browser.evaluateJavascript(script, raw -> {
            try {
                JSONArray candidates = decodeArray(raw);
                int strong = 0;
                int review = 0;
                int ignored = 0;

                for (int i = 0; i < candidates.length(); i++) {
                    JSONObject candidate = candidates.optJSONObject(i);
                    if (candidate == null || isNonProductCandidate(candidate)) {
                        ignored++;
                        continue;
                    }

                    String title = cleanTitle(candidate.optString("title", ""));
                    String body = candidate.optString("body", "");
                    String url = candidate.optString("url", "");
                    String text = normalize(title + " " + body);
                    String category = detectCategory(text);
                    int score = scoreProduct(title, body);

                    if ("OTRA".equals(category) || score < 66) {
                        ignored++;
                        continue;
                    }

                    JSONObject item = makeItem(
                        title,
                        "",
                        extractPrice(body),
                        candidate.optString("image", ""),
                        url,
                        body,
                        "auto"
                    );
                    item.put("score", score);
                    item.put("category", category);
                    item.put("status", score >= 82 ? "encaja" : "revisar");
                    item.put("reason", selectionReason(title, body));

                    upsertProduct(item);
                    if (score >= 82) strong++; else review++;
                }

                browserStatus.setText("Curación terminada · " + strong + " encajan · " + review + " revisar");
                if (strong + review == 0) {
                    toast("No vi productos suficientes. Entra en una marca, búsqueda o categoría y vuelve a pulsar Curar página.");
                } else {
                    toast("He seleccionado " + (strong + review) + " propuestas según nuestro universo.");
                    showSaved();
                }
            } catch (Exception error) {
                browserStatus.setText("No se pudo analizar este listado");
                toast("La página todavía no está lista o no contiene tarjetas de producto visibles.");
            }
        });
    }

    private void captureCurrentProduct() {
        String url = browser.getUrl();
        if (url == null || !url.contains("parisfashionshops.com")) {
            toast("Abre primero una ficha de Paris Fashion Shops.");
            return;
        }

        browserStatus.setText("Analizando la ficha abierta…");
        String script = """
            (() => {
              const clean = value => (value || '').replace(/\s+/g, ' ').trim();
              const value = el => clean(el && (el.getAttribute('content') || el.innerText || el.textContent) || '');
              const first = (...selectors) => {
                for (const selector of selectors) {
                  const el = document.querySelector(selector);
                  const text = value(el);
                  if (text) return text;
                }
                return '';
              };
              const firstImage = (...selectors) => {
                for (const selector of selectors) {
                  const el = document.querySelector(selector);
                  if (!el) continue;
                  const src = el.getAttribute('content') || el.currentSrc || el.src || el.getAttribute('data-src') || '';
                  if (src) {
                    try { return new URL(src, location.href).href; } catch (_) { return src; }
                  }
                }
                return '';
              };

              let schema = null;
              for (const node of document.querySelectorAll('script[type="application/ld+json"]')) {
                try {
                  const parsed = JSON.parse(node.textContent);
                  const queue = Array.isArray(parsed) ? [...parsed] : [parsed];
                  while (queue.length) {
                    const current = queue.shift();
                    if (!current || typeof current !== 'object') continue;
                    const type = current['@type'];
                    if (type === 'Product' || (Array.isArray(type) && type.includes('Product'))) {
                      schema = current;
                      queue.length = 0;
                      break;
                    }
                    if (Array.isArray(current['@graph'])) queue.push(...current['@graph']);
                  }
                } catch (_) {}
                if (schema) break;
              }

              const schemaImage = Array.isArray(schema?.image) ? schema.image[0] : schema?.image;
              const schemaBrand = typeof schema?.brand === 'object' ? schema.brand?.name : schema?.brand;
              const schemaOffer = Array.isArray(schema?.offers) ? schema.offers[0] : schema?.offers;
              const detailRoot = document.querySelector(
                'main, [itemtype*="Product"], [class*="product-detail"], [class*="ProductDetail"], [class*="product_page"]'
              ) || document.body;

              const title = clean(schema?.name)
                || first('h1', '[itemprop="name"]', 'meta[property="og:title"]')
                || document.title;
              const brand = clean(schemaBrand)
                || first('[itemprop="brand"]', '[class*="brand-name"]', '[class*="seller-name"]', 'meta[property="product:brand"]');
              const price = clean(schemaOffer?.price)
                || first('meta[property="product:price:amount"]', '[itemprop="price"]', '[class*="product-price"]');
              const image = schemaImage
                ? (() => { try { return new URL(schemaImage, location.href).href; } catch (_) { return schemaImage; } })()
                : firstImage('meta[property="og:image"]', 'meta[name="twitter:image"]', '[itemprop="image"]', 'main img');
              const body = clean(detailRoot?.innerText || '').slice(0, 9000);

              return JSON.stringify({
                title, brand, price, image, body, url:location.href, hasProductSchema:!!schema
              });
            })()
            """;

        browser.evaluateJavascript(script, raw -> {
            try {
                JSONObject captured = decodeObject(raw);
                if (!isLikelyProductPage(captured)) {
                    browserStatus.setText("Esto parece un listado, no una ficha");
                    toast("En esta página usa Curar página. Guardar ficha es solo para un producto concreto.");
                    return;
                }

                JSONObject item = makeItem(
                    cleanTitle(captured.optString("title", "Producto sin título")),
                    captured.optString("brand", ""),
                    captured.optString("price", ""),
                    captured.optString("image", ""),
                    captured.optString("url", url),
                    captured.optString("body", ""),
                    "manual"
                );

                int score = item.optInt("score");
                item.put("status", score >= 82 ? "encaja" : "revisar");
                upsertProduct(item);

                browserStatus.setText(
                    "Guardado · " + item.optString("status").toUpperCase(Locale.ROOT) +
                    " · " + score + "/100"
                );
                toast(score >= 82
                    ? "Este producto encaja con nuestro universo."
                    : "Producto guardado para revisión.");
            } catch (Exception error) {
                browserStatus.setText("No se pudo leer esta ficha");
                toast("Abre la página concreta del producto y prueba de nuevo.");
            }
        });
    }

    private JSONObject makeItem(
        String title,
        String rawBrand,
        String rawPrice,
        String image,
        String url,
        String body,
        String source
    ) throws JSONException {
        String safeBody = body == null ? "" : body;
        String text = normalize(title + " " + safeBody);
        JSONObject item = new JSONObject();
        item.put("id", String.valueOf(url.hashCode()));
        item.put("title", title.isEmpty() ? "Producto sin título" : title);
        item.put("brand", normalizeBrand(rawBrand, text));
        item.put("price", cleanPrice(rawPrice));
        item.put("image", image == null ? "" : image);
        item.put("url", url);
        item.put("score", scoreProduct(title, safeBody));
        item.put("category", detectCategory(text));
        item.put("color", detectColor(text));
        item.put("status", "revisar");
        item.put("reason", selectionReason(title, safeBody));
        item.put("source", source);
        item.put("excerpt", safeBody.substring(0, Math.min(1000, safeBody.length())));
        item.put("savedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
        return item;
    }

    private boolean isLikelyProductPage(JSONObject captured) {
        String url = captured.optString("url", "").toLowerCase(Locale.ROOT);
        String title = normalize(captured.optString("title", ""));
        String body = normalize(captured.optString("body", ""));
        boolean schema = captured.optBoolean("hasProductSchema", false);

        if (schema) return true;
        if (url.endsWith("/wholesalers") || url.contains("/women/wholesalers") || url.contains("/brand/")) return false;
        if (containsAny(title, "ropa al por mayor", "todas nuestras marcas", "mayoristas de moda")) return false;
        if (containsAny(body, "todas nuestras marcas") && body.contains("pedido mínimo")) return false;
        return !"OTRA".equals(detectCategory(title + " " + body)) && title.length() >= 4;
    }

    private boolean isNonProductCandidate(JSONObject candidate) {
        String url = candidate.optString("url", "").toLowerCase(Locale.ROOT);
        String title = normalize(candidate.optString("title", ""));
        String body = normalize(candidate.optString("body", ""));

        if (url.isEmpty() || url.endsWith("/wholesalers") || url.contains("/women/wholesalers") || url.contains("/brand/")) {
            return true;
        }
        if (containsAny(title,
            "ropa al por mayor",
            "todas nuestras marcas",
            "crear una cuenta",
            "iniciar sesión",
            "nuevos productos"
        )) {
            return true;
        }
        return body.contains("pedido mínimo") &&
            !containsAny(body, "conjunto", "ensemble", "corsé", "corset", "bustier", "vestido", "dress", "falda", "skirt");
    }

    private int scoreProduct(String title, String body) {
        String normalizedTitle = normalize(title);
        String text = normalize(title + " " + body);
        int score = 8;

        score += countMatches(normalizedTitle,
            new String[]{"conjunto", "ensemble", "two-piece", "two piece", "2 piezas", "co-ord", "set top", "top y falda"},
            20
        );
        score += countMatches(normalizedTitle,
            new String[]{"corsé", "corset", "bustier", "balconette", "bodycon", "cut-out", "cut out", "backless", "lace-up", "lace up"},
            16
        );
        score += countMatches(normalizedTitle,
            new String[]{"encaje", "lace", "satén", "satin", "mesh", "tul", "tulle", "strapless", "drapeado", "draped", "lunares", "polka"},
            9
        );

        score += countMatches(text,
            new String[]{"conjunto", "ensemble", "two-piece", "two piece", "2 piezas", "co-ord", "top y falda", "top + falda"},
            10
        );
        score += countMatches(text,
            new String[]{"corsé", "corset", "bustier", "balconette", "bodycon", "cut-out", "cut out", "espalda descubierta", "backless", "lace-up", "lace up"},
            8
        );
        score += countMatches(text,
            new String[]{"encaje", "lace", "satén", "satin", "mesh", "tul", "tulle", "palabra de honor", "strapless", "abertura", "slit", "drapeado", "draped", "ajustado", "fitted", "volantes", "ruffle"},
            4
        );
        score += countMatches(text,
            new String[]{"mini", "falda", "skirt", "vestido", "dress", "body", "top", "fiesta", "party", "noche", "evening", "lentejuelas", "sequin"},
            2
        );
        score += countMatches(text,
            new String[]{"negro", "black", "marfil", "ivory", "crema", "cream", "chocolate", "burdeos", "burgundy", "vino", "wine", "blanco", "white", "lunares", "polka"},
            3
        );
        score += countMatches(text,
            new String[]{"viscosa", "viscose", "elastano", "elastane", "forro", "lined"},
            2
        );
        score += countMatches(text,
            new String[]{"giorgia", "mochy", "frime paris", "f&p", "fp & co", "copperose", "soy & co", "unika paris", "jolio & co"},
            3
        );

        score -= countMatches(text,
            new String[]{"sudadera", "sweatshirt", "chándal", "tracksuit", "deportivo", "sportswear", "oversized", "camiseta básica", "basic t-shirt", "pijama", "pyjama", "legging", "hoodie"},
            12
        );
        score -= countMatches(text,
            new String[]{"neón", "neon", "fluorescente", "cargo", "vaquero", "denim"},
            4
        );

        String category = detectCategory(text);
        if ("CONJUNTO".equals(category)) score += 12;
        else if ("TOP / CORSÉ".equals(category) || "PARTE INFERIOR".equals(category) || "VESTIDO".equals(category)) score += 5;

        if ("CONJUNTO".equals(category) && containsAny(text, "corsé", "corset", "bustier")) score += 10;
        if (containsAny(text, "encaje", "lace") && containsAny(text, "satén", "satin")) score += 5;

        return Math.max(0, Math.min(100, score));
    }

    private String selectionReason(String title, String body) {
        String text = normalize(title + " " + body);
        List<String> reasons = new ArrayList<>();

        if ("CONJUNTO".equals(detectCategory(text))) reasons.add("conjunto completo");
        if (containsAny(text, "corsé", "corset", "bustier", "balconette")) reasons.add("corsetería");
        if (containsAny(text, "encaje", "lace")) reasons.add("encaje");
        if (containsAny(text, "satén", "satin")) reasons.add("satén");
        if (containsAny(text, "lunares", "polka")) reasons.add("lunares");
        if (containsAny(text, "drapeado", "draped", "volantes", "ruffle")) reasons.add("silueta editorial");

        String color = detectColor(text);
        if (!"SIN DEFINIR".equals(color)) reasons.add(color.toLowerCase(Locale.ROOT));

        if (reasons.isEmpty()) reasons.add("silueta femenina");
        return joinReasons(reasons, 4);
    }

    private String joinReasons(List<String> reasons, int maximum) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(maximum, reasons.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) builder.append(" · ");
            builder.append(reasons.get(i));
        }
        return builder.toString();
    }

    private int countMatches(String text, String[] terms, int weight) {
        int total = 0;
        for (String term : terms) {
            if (text.contains(term)) total += weight;
        }
        return total;
    }

    private String detectCategory(String text) {
        if (containsAny(text,
            "conjunto", "ensemble", "two-piece", "two piece", "2 piezas", "co-ord",
            "set top", "top y falda", "top + falda", "top y pantalón", "top y pantalon"
        )) return "CONJUNTO";

        if (containsAny(text, "vestido", "dress", "mono ", "jumpsuit", "combinaison")) return "VESTIDO";
        if (containsAny(text, "falda", "skirt", "short", "pantalón", "pantalon", "trouser", "pants")) return "PARTE INFERIOR";
        if (containsAny(text, "corsé", "corset", "bustier", "top", "body", "bodysuit")) return "TOP / CORSÉ";
        return "OTRA";
    }

    private String detectColor(String text) {
        if (containsAny(text, "negro", "black", "noir")) return "NEGRO";
        if (containsAny(text, "marfil", "ivory", "crema", "cream", "off-white", "off white", "écru", "ecru")) return "MARFIL";
        if (containsAny(text, "blanco", "white", "blanc")) return "BLANCO";
        if (containsAny(text, "chocolate", "marrón", "marron", "brown", "chocolat")) return "CHOCOLATE";
        if (containsAny(text, "burdeos", "burgundy", "wine", "bordeaux")) return "BURDEOS";
        if (containsAny(text, "rojo", "red", "rouge")) return "ROJO";
        if (containsAny(text, "rosa", "pink", "rose")) return "ROSA";
        if (containsAny(text, "amarillo", "yellow", "jaune")) return "AMARILLO";
        return "SIN DEFINIR";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return value == null
            ? ""
            : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String cleanTitle(String raw) {
        if (raw == null) return "";
        String title = raw.replaceAll("\\s+", " ").trim();
        title = title.replaceAll("(?i)\\s*[|–-]\\s*paris fashion shops.*$", "").trim();
        if (title.length() > 180) title = title.substring(0, 180).trim();
        return title;
    }

    private String normalizeBrand(String raw, String pageText) {
        String brand = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (!brand.isEmpty() && brand.length() < 80 && !brand.toLowerCase(Locale.ROOT).contains("paris fashion shops")) {
            return brand;
        }

        String[] known = {
            "Giorgia", "Mochy", "Frime Paris", "FP & CO", "F&P",
            "Copperose", "Soy & Co", "Unika Paris", "Jolio & Co",
            "Golden Live", "Nacrelia", "KELIE", "Eight Paris"
        };
        for (String candidate : known) {
            if (pageText.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        }
        return "Proveedor por confirmar";
    }

    private String extractPrice(String text) {
        if (text == null || text.toLowerCase(Locale.ROOT).contains("pedido mínimo")) {
            return "Precio profesional oculto";
        }
        Matcher matcher = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)\\s*€").matcher(text);
        return matcher.find() ? matcher.group(1) + " €" : "Precio profesional oculto";
    }

    private String cleanPrice(String price) {
        if (price == null) return "Precio profesional oculto";
        String clean = price.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty() || clean.length() > 100) return "Precio profesional oculto";
        if (clean.matches("\\d+(?:[.,]\\d{1,2})?")) return clean + " €";
        return clean;
    }

    private JSONObject decodeObject(String raw) throws JSONException {
        if (raw == null || "null".equals(raw)) throw new JSONException("Sin datos");
        Object outer = new JSONTokener(raw).nextValue();
        String decoded = outer instanceof String ? (String) outer : raw;
        return new JSONObject(decoded);
    }

    private JSONArray decodeArray(String raw) throws JSONException {
        if (raw == null || "null".equals(raw)) throw new JSONException("Sin datos");
        Object outer = new JSONTokener(raw).nextValue();
        String decoded = outer instanceof String ? (String) outer : raw;
        return new JSONArray(decoded);
    }

    private void upsertProduct(JSONObject item) throws JSONException {
        JSONArray updated = new JSONArray();
        updated.put(item);
        String newUrl = item.optString("url");

        for (int i = 0; i < products.length(); i++) {
            JSONObject current = products.optJSONObject(i);
            if (current != null && !newUrl.equals(current.optString("url"))) {
                updated.put(current);
            }
        }
        products = updated;
        persistProducts();
    }

    private void loadProducts() {
        try {
            products = new JSONArray(preferences.getString(PRODUCTS_KEY, "[]"));
        } catch (JSONException ignored) {
            products = new JSONArray();
        }
    }

    private void migrateSavedProducts() {
        JSONArray cleaned = new JSONArray();
        boolean changed = false;

        for (int i = 0; i < products.length(); i++) {
            JSONObject item = products.optJSONObject(i);
            if (item == null) {
                changed = true;
                continue;
            }

            String url = item.optString("url", "").toLowerCase(Locale.ROOT);
            String title = normalize(item.optString("title", ""));
            boolean genericLanding = url.endsWith("/wholesalers")
                || url.contains("/women/wholesalers")
                || containsAny(title, "ropa al por mayor", "todas nuestras marcas");

            if (genericLanding) {
                changed = true;
            } else {
                cleaned.put(item);
            }
        }

        if (changed) {
            products = cleaned;
            persistProducts();
        }
    }

    private void persistProducts() {
        preferences.edit().putString(PRODUCTS_KEY, products.toString()).apply();
    }

    private void showExplore() {
        exploreView.setVisibility(View.VISIBLE);
        savedView.setVisibility(View.GONE);
        looksView.setVisibility(View.GONE);
    }

    private void showSaved() {
        exploreView.setVisibility(View.GONE);
        savedView.setVisibility(View.VISIBLE);
        looksView.setVisibility(View.GONE);
        renderSaved();
    }

    private void showLooks() {
        exploreView.setVisibility(View.GONE);
        savedView.setVisibility(View.GONE);
        looksView.setVisibility(View.VISIBLE);
        renderLooks();
    }

    private void renderSaved() {
        savedList.removeAllViews();
        List<JSONObject> list = productList(true);
        int strong = 0;
        for (JSONObject item : list) {
            if ("encaja".equals(item.optString("status"))) strong++;
        }

        savedTitle.setText("Selección · " + list.size() + " · Encajan " + strong);
        if (list.isEmpty()) {
            savedList.addView(emptyState(
                "En Explorar, abre una búsqueda o marca y pulsa “Curar página”. La app conservará solo las propuestas afines."
            ));
            return;
        }

        for (JSONObject item : list) {
            savedList.addView(productCard(item));
        }
    }

    private View productCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(panelDrawable(Color.WHITE, Color.rgb(222, 212, 198), 1, 16));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(cardParams);

        String imageUrl = item.optString("image");
        if (!imageUrl.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(232, 227, 219));
            card.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190)
            ));
            loadImage(imageUrl, image);
        }

        TextView title = label(item.optString("title", "Producto"), 17, INK, true);
        title.setPadding(0, dp(9), 0, dp(2));
        card.addView(title);

        String meta = item.optString("brand", "Proveedor") + " · " +
            item.optString("category", "OTRA") + " · " +
            item.optString("color", "SIN DEFINIR");
        card.addView(label(meta, 12, MUTED, false));
        card.addView(label(item.optString("price", "Precio profesional oculto"), 13, INK, true));

        String reasonText = item.optString("reason", "");
        if (!reasonText.isEmpty()) {
            TextView reason = label("Por qué: " + reasonText, 12, MUTED, false);
            reason.setPadding(0, dp(4), 0, 0);
            card.addView(reason);
        }

        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        scoreRow.setPadding(0, dp(7), 0, 0);

        scoreRow.addView(pill("Afinidad " + item.optInt("score") + "/100", GOLD, INK));

        String status = item.optString("status", "revisar");
        int statusColor = "encaja".equals(status)
            ? GREEN
            : ("descartar".equals(status) ? RED : MUTED);
        TextView statusPill = pill(status.toUpperCase(Locale.ROOT), statusColor, Color.WHITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(dp(7), 0, 0, 0);
        scoreRow.addView(statusPill, statusParams);

        if ("auto".equals(item.optString("source"))) {
            TextView auto = pill("AUTO", Color.rgb(74, 61, 90), Color.WHITE);
            LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            autoParams.setMargins(dp(7), 0, 0, 0);
            scoreRow.addView(auto, autoParams);
        }
        card.addView(scoreRow);

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        primaryActions.setPadding(0, dp(10), 0, 0);
        primaryActions.addView(
            actionButton("Abrir", () -> openProduct(item), SOFT_BLACK, Color.WHITE),
            buttonParams(1f, dp(4))
        );
        primaryActions.addView(
            actionButton("✓ Encaja", () -> setStatus(item, "encaja"), GREEN, Color.WHITE),
            buttonParams(1f, dp(4))
        );
        primaryActions.addView(
            actionButton("Revisar", () -> setStatus(item, "revisar"), GOLD, INK),
            buttonParams(1f, dp(4))
        );
        card.addView(primaryActions);

        LinearLayout secondaryActions = new LinearLayout(this);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        secondaryActions.setPadding(0, dp(7), 0, 0);
        secondaryActions.addView(
            actionButton("Descartar", () -> setStatus(item, "descartar"), RED, Color.WHITE),
            buttonParams(1f, dp(4))
        );
        secondaryActions.addView(
            actionButton("Eliminar", () -> confirmDelete(item), Color.rgb(224, 220, 213), INK),
            buttonParams(1f, dp(4))
        );
        card.addView(secondaryActions);

        return card;
    }

    private void renderLooks() {
        looksList.removeAllViews();
        List<JSONObject> available = productList(false);
        List<Look> looks = new ArrayList<>();
        List<JSONObject> tops = new ArrayList<>();
        List<JSONObject> bottoms = new ArrayList<>();

        for (JSONObject item : available) {
            if ("descartar".equals(item.optString("status"))) continue;
            String category = item.optString("category");

            if ("CONJUNTO".equals(category)) {
                int score = Math.min(100, item.optInt("score") + ("encaja".equals(item.optString("status")) ? 5 : 0));
                looks.add(new Look(
                    item,
                    null,
                    score,
                    "Conjunto completo · " + item.optString("reason", "selección afín")
                ));
            } else if ("TOP / CORSÉ".equals(category)) {
                tops.add(item);
            } else if ("PARTE INFERIOR".equals(category)) {
                bottoms.add(item);
            }
        }

        for (JSONObject top : tops) {
            for (JSONObject bottom : bottoms) {
                int score = compatibilityScore(top, bottom);
                if (score >= 65) {
                    looks.add(new Look(top, bottom, score, compatibilityReason(top, bottom)));
                }
            }
        }

        Collections.sort(looks, (a, b) -> Integer.compare(b.score, a.score));
        int maximum = Math.min(24, looks.size());
        looksTitle.setText("Combinaciones sugeridas · " + maximum);

        if (maximum == 0) {
            looksList.addView(emptyState(
                "Guarda un conjunto completo o al menos un corsé/top y una falda o pantalón. Curar página puede encontrarlos automáticamente."
            ));
            return;
        }

        for (int i = 0; i < maximum; i++) {
            looksList.addView(lookCard(looks.get(i), i + 1));
        }
    }

    private int compatibilityScore(JSONObject top, JSONObject bottom) {
        int score = (top.optInt("score") + bottom.optInt("score")) / 2;
        String topColor = top.optString("color");
        String bottomColor = bottom.optString("color");
        String topBrand = top.optString("brand");
        String bottomBrand = bottom.optString("brand");

        if (topColor.equals(bottomColor) && !"SIN DEFINIR".equals(topColor)) score += 10;
        if (isBlackIvory(topColor, bottomColor)) score += 14;
        if (isPair(topColor, bottomColor, "NEGRO", "BURDEOS")) score += 9;
        if (isPair(topColor, bottomColor, "NEGRO", "CHOCOLATE")) score += 7;
        if (isPair(topColor, bottomColor, "MARFIL", "CHOCOLATE")) score += 8;
        if ("NEGRO".equals(topColor) || "NEGRO".equals(bottomColor)) score += 4;

        if (topBrand.equals(bottomBrand) && !"Proveedor por confirmar".equals(topBrand)) score += 9;
        if ("encaja".equals(top.optString("status"))) score += 4;
        if ("encaja".equals(bottom.optString("status"))) score += 4;

        String combined = normalize(
            top.optString("excerpt") + " " + bottom.optString("excerpt") + " " +
            top.optString("reason") + " " + bottom.optString("reason")
        );
        if (combined.contains("encaje") && combined.contains("satén")) score += 5;

        return Math.max(0, Math.min(100, score));
    }

    private String compatibilityReason(JSONObject top, JSONObject bottom) {
        List<String> reasons = new ArrayList<>();
        String topColor = top.optString("color");
        String bottomColor = bottom.optString("color");

        if (isBlackIvory(topColor, bottomColor)) reasons.add("contraste negro–marfil");
        else if (topColor.equals(bottomColor) && !"SIN DEFINIR".equals(topColor)) reasons.add("look monocromático");
        else if ("NEGRO".equals(topColor) || "NEGRO".equals(bottomColor)) reasons.add("base nocturna");

        if (top.optString("brand").equals(bottom.optString("brand")) &&
            !"Proveedor por confirmar".equals(top.optString("brand"))) {
            reasons.add("mismo proveedor");
        }

        String combined = normalize(top.optString("reason") + " " + bottom.optString("reason"));
        if (combined.contains("corsetería")) reasons.add("corsetería");
        if (combined.contains("encaje")) reasons.add("encaje");
        if (combined.contains("satén")) reasons.add("satén");

        if (reasons.isEmpty()) reasons.add("siluetas compatibles");
        return joinReasons(reasons, 3);
    }

    private boolean isBlackIvory(String first, String second) {
        return isPair(first, second, "NEGRO", "MARFIL") ||
            isPair(first, second, "NEGRO", "BLANCO");
    }

    private boolean isPair(String first, String second, String a, String b) {
        return (a.equals(first) && b.equals(second)) || (b.equals(first) && a.equals(second));
    }

    private View lookCard(Look look, int position) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(panelDrawable(Color.WHITE, Color.rgb(222, 212, 198), 1, 16));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(params);

        String heading = look.bottom == null
            ? "LOOK " + position + " · CONJUNTO COMPLETO"
            : "LOOK " + position;
        card.addView(label(heading, 11, GOLD, true));

        TextView title = label(look.top.optString("title", "Producto"), 17, INK, true);
        title.setPadding(0, dp(5), 0, dp(2));
        card.addView(title);

        if (look.bottom != null) {
            card.addView(label("+ " + look.bottom.optString("title", "Parte inferior"), 16, INK, true));
        }

        String suppliers = look.top.optString("brand", "Proveedor");
        if (look.bottom != null && !suppliers.equals(look.bottom.optString("brand"))) {
            suppliers += " + " + look.bottom.optString("brand", "Proveedor");
        }
        card.addView(label(suppliers, 12, MUTED, false));

        TextView reason = label("Por qué funciona: " + look.reason, 12, MUTED, false);
        reason.setPadding(0, dp(4), 0, dp(7));
        card.addView(reason);
        card.addView(pill("Compatibilidad " + look.score + "/100", GOLD, INK));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, 0);
        actions.addView(
            actionButton("Abrir primera", () -> openProduct(look.top), SOFT_BLACK, Color.WHITE),
            buttonParams(1f, dp(4))
        );
        if (look.bottom != null) {
            actions.addView(
                actionButton("Abrir segunda", () -> openProduct(look.bottom), Color.rgb(83, 67, 52), Color.WHITE),
                buttonParams(1f, dp(4))
            );
        }
        card.addView(actions);

        return card;
    }

    private List<JSONObject> productList(boolean includeDiscarded) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < products.length(); i++) {
            JSONObject item = products.optJSONObject(i);
            if (item == null) continue;
            if (!includeDiscarded && "descartar".equals(item.optString("status"))) continue;
            list.add(item);
        }

        Collections.sort(list, (a, b) -> {
            int statusA = statusRank(a.optString("status"));
            int statusB = statusRank(b.optString("status"));
            if (statusA != statusB) return Integer.compare(statusB, statusA);
            return Integer.compare(b.optInt("score"), a.optInt("score"));
        });
        return list;
    }

    private int statusRank(String status) {
        if ("encaja".equals(status)) return 3;
        if ("revisar".equals(status)) return 2;
        return 1;
    }

    private void setStatus(JSONObject item, String status) {
        try {
            item.put("status", status);
            persistProducts();
            renderSaved();
        } catch (JSONException ignored) {
            toast("No se pudo actualizar.");
        }
    }

    private void confirmDelete(JSONObject item) {
        new AlertDialog.Builder(this)
            .setTitle("Eliminar de la selección")
            .setMessage(item.optString("title", "¿Eliminar este producto?"))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (dialog, which) -> deleteProduct(item))
            .show();
    }

    private void deleteProduct(JSONObject item) {
        JSONArray updated = new JSONArray();
        String url = item.optString("url");

        for (int i = 0; i < products.length(); i++) {
            JSONObject current = products.optJSONObject(i);
            if (current != null && !url.equals(current.optString("url"))) {
                updated.put(current);
            }
        }

        products = updated;
        persistProducts();
        renderSaved();
    }

    private void openProduct(JSONObject item) {
        String url = item.optString("url");
        if (url.isEmpty()) {
            toast("Este producto no tiene enlace.");
            return;
        }
        showExplore();
        browser.loadUrl(url);
    }

    private void shareSelection() {
        if (products.length() == 0) {
            toast("No hay productos que exportar.");
            return;
        }

        JSONObject export = new JSONObject();
        try {
            export.put("brand", "Maldita Riviera");
            export.put("profile", "Mediterranean After Dark");
            export.put("exportedAt", new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss",
                Locale.getDefault()
            ).format(new Date()));
            export.put("products", products);
        } catch (JSONException ignored) {
            return;
        }

        String payload;
        try {
            payload = export.toString(2);
        } catch (JSONException error) {
            payload = export.toString();
        }

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_SUBJECT, "Selección Maldita Riviera");
        share.putExtra(Intent.EXTRA_TEXT, payload);
        startActivity(Intent.createChooser(share, "Exportar selección"));
    }

    private void loadImage(String url, ImageView target) {
        imagePool.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android MalditaRivieraCurator/0.2");
                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (!isFinishing()) target.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
                // La ficha sigue siendo útil aunque la imagen remota no permita carga directa.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private TextView emptyState(String message) {
        TextView view = label(message, 15, MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(26), dp(42), dp(26), dp(42));
        view.setBackground(panelDrawable(Color.WHITE, Color.rgb(222, 212, 198), 1, 16));
        return view;
    }

    private Button navButton(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private Button actionButton(String text, Runnable action, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(panelDrawable(background, background, 0, 12));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private TextView pill(String text, int background, int foreground) {
        TextView view = label(text, 11, foreground, true);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setGravity(Gravity.CENTER);
        view.setBackground(panelDrawable(background, background, 0, 999));
        return view;
    }

    private GradientDrawable panelDrawable(
        int background,
        int strokeColor,
        int strokeWidthDp,
        int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(background);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        return new LinearLayout.LayoutParams(0, dp(52), 1f);
    }

    private LinearLayout.LayoutParams buttonParams(float weight, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), weight);
        params.setMargins(margin, 0, margin, 0);
        return params;
    }

    private FrameLayout.LayoutParams matchParams() {
        return new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static final class Look {
        final JSONObject top;
        final JSONObject bottom;
        final int score;
        final String reason;

        Look(JSONObject top, JSONObject bottom, int score, String reason) {
            this.top = top;
            this.bottom = bottom;
            this.score = score;
            this.reason = reason;
        }
    }
}
