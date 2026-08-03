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

public class MainActivity extends Activity {
    private static final String BASE_URL = "https://parisfashionshops.com/es/women/wholesalers";
    private static final String PREFS = "maldita_riviera_curator";
    private static final String PRODUCTS_KEY = "saved_products";

    private static final int INK = Color.rgb(15, 15, 18);
    private static final int IVORY = Color.rgb(247, 242, 233);
    private static final int GOLD = Color.rgb(183, 149, 100);
    private static final int MUTED = Color.rgb(105, 101, 96);
    private static final int GREEN = Color.rgb(48, 113, 78);
    private static final int RED = Color.rgb(142, 54, 62);

    private SharedPreferences preferences;
    private JSONArray products = new JSONArray();
    private final ExecutorService imagePool = Executors.newFixedThreadPool(3);

    private FrameLayout content;
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
        buildInterface();
        setupBrowser();
        showExplore();

        if (savedInstanceState == null) {
            browser.loadUrl(BASE_URL);
        } else {
            browser.restoreState(savedInstanceState);
        }

        if (!preferences.getBoolean("privacy_notice_seen", false)) {
            new AlertDialog.Builder(this)
                .setTitle("Curador privado")
                .setMessage("Inicia sesión tú misma dentro del navegador. La app no lee ni exporta tu contraseña: solo guarda localmente la información de las fichas que decidas analizar.")
                .setPositiveButton("Entendido", (dialog, which) ->
                    preferences.edit().putBoolean("privacy_notice_seen", true).apply())
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
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(8), dp(7), dp(8), dp(7));
        navigation.setBackgroundColor(Color.rgb(28, 27, 30));
        Button explore = navButton("Explorar", this::showExplore);
        Button saved = navButton("Guardados", this::showSaved);
        Button looks = navButton("Looks", this::showLooks);
        navigation.addView(explore, weightedButtonParams());
        navigation.addView(saved, weightedButtonParams());
        navigation.addView(looks, weightedButtonParams());
        root.addView(navigation);

        content = new FrameLayout(this);
        exploreView = buildExploreView();
        savedView = buildSavedView();
        looksView = buildLooksView();
        content.addView(exploreView, matchParams());
        content.addView(savedView, matchParams());
        content.addView(looksView, matchParams());
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private View buildExploreView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        browser = new WebView(this);
        page.addView(browser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(9), dp(12), dp(11));
        controls.setBackgroundColor(INK);

        browserStatus = label("Abriendo Paris Fashion Shops…", 12, Color.rgb(218, 209, 197), false);
        browserStatus.setSingleLine(true);
        controls.addView(browserStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button home = actionButton("Inicio", () -> browser.loadUrl(BASE_URL), Color.rgb(51, 49, 53), Color.WHITE);
        Button save = actionButton("Analizar y guardar", this::captureCurrentProduct, GOLD, INK);
        buttons.addView(home, buttonParams(1f, dp(5)));
        buttons.addView(save, buttonParams(2f, dp(5)));
        controls.addView(buttons);
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
        savedTitle = label("Selección guardada", 20, INK, true);
        heading.addView(savedTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button export = actionButton("Exportar", this::shareSelection, Color.TRANSPARENT, INK);
        export.setBackground(panelDrawable(Color.TRANSPARENT, GOLD, 1, 12));
        heading.addView(export, new LinearLayout.LayoutParams(dp(100), dp(42)));
        page.addView(heading);

        TextView help = label("Marca cada prenda como Encaja, Revisar o Descartar. Nada se compra desde la app.", 12, MUTED, false);
        help.setPadding(0, dp(5), 0, dp(10));
        page.addView(help);

        ScrollView scroll = new ScrollView(this);
        savedList = new LinearLayout(this);
        savedList.setOrientation(LinearLayout.VERTICAL);
        savedList.setPadding(0, 0, 0, dp(20));
        scroll.addView(savedList);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private View buildLooksView() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(12), dp(12), 0);
        looksTitle = label("Combinaciones sugeridas", 20, INK, true);
        page.addView(looksTitle);
        TextView help = label("La app combina corsés, tops y partes inferiores según color, categoría y afinidad estética.", 12, MUTED, false);
        help.setPadding(0, dp(5), 0, dp(10));
        page.addView(help);

        ScrollView scroll = new ScrollView(this);
        looksList = new LinearLayout(this);
        looksList.setOrientation(LinearLayout.VERTICAL);
        looksList.setPadding(0, 0, 0, dp(20));
        scroll.addView(looksList);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
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
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    toast("No se puede abrir este enlace.");
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                String shortUrl = url == null ? "Página cargada" : url.replace("https://", "").replace("http://", "");
                browserStatus.setText(shortUrl);
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
              const value = el => (el && (el.getAttribute('content') || el.innerText || el.textContent) || '').trim();
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
                  if (src) return src;
                }
                return '';
              };
              const title = first('h1', '[itemprop="name"]', 'meta[property="og:title"]') || document.title;
              const brand = first('[itemprop="brand"]', '[class*="brand-name"]', '[class*="seller-name"]', 'meta[property="product:brand"]');
              const price = first('meta[property="product:price:amount"]', '[itemprop="price"]', '[class*="product-price"]', '[class*="price"]');
              const image = firstImage('meta[property="og:image"]', 'meta[name="twitter:image"]', '[itemprop="image"]', 'main img', 'img');
              const body = (document.body && document.body.innerText || '').replace(/\s+/g, ' ').trim().slice(0, 14000);
              return JSON.stringify({title, brand, price, image, body, url: location.href});
            })()
            """;

        browser.evaluateJavascript(script, raw -> {
            try {
                if (raw == null || "null".equals(raw)) throw new JSONException("Sin datos");
                Object outer = new JSONTokener(raw).nextValue();
                String decoded = outer instanceof String ? (String) outer : raw;
                JSONObject captured = new JSONObject(decoded);

                String title = captured.optString("title", "Producto sin título").trim();
                String pageText = (title + " " + captured.optString("body", "")).toLowerCase(Locale.ROOT);
                String productUrl = captured.optString("url", url);

                JSONObject item = new JSONObject();
                item.put("id", String.valueOf(productUrl.hashCode()));
                item.put("title", title);
                item.put("brand", normalizeBrand(captured.optString("brand", ""), pageText));
                item.put("price", cleanPrice(captured.optString("price", "")));
                item.put("image", captured.optString("image", ""));
                item.put("url", productUrl);
                item.put("score", scoreProduct(pageText));
                item.put("category", detectCategory(pageText));
                item.put("color", detectColor(pageText));
                item.put("status", "revisar");
                item.put("excerpt", captured.optString("body", "").substring(0, Math.min(900, captured.optString("body", "").length())));
                item.put("savedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));

                upsertProduct(item);
                browserStatus.setText("Guardado · afinidad " + item.optInt("score") + "/100");
                toast("Prenda guardada para revisar.");
            } catch (Exception error) {
                browserStatus.setText("No se pudo leer esta ficha");
                toast("No pude extraer la ficha. Abre la página concreta del producto y prueba de nuevo.");
            }
        });
    }

    private int scoreProduct(String text) {
        int score = 15;
        String[] strong = {"corsé", "corset", "bustier", "balconette", "bodycon", "cut-out", "cut out", "espalda descubierta", "backless", "lace-up", "lace up"};
        String[] medium = {"encaje", "lace", "satén", "satin", "mesh", "tul", "tulle", "palabra de honor", "strapless", "abertura", "slit", "drapeado", "draped", "ajustado", "fitted", "volantes", "ruffle", "co-ord", "conjunto"};
        String[] soft = {"mini", "falda", "skirt", "vestido", "dress", "body", "top", "lunares", "polka", "fiesta", "party", "noche", "evening", "sequin", "lentejuelas"};
        String[] palette = {"negro", "black", "marfil", "ivory", "crema", "cream", "chocolate", "burdeos", "burgundy", "rojo", "red", "blanco", "white"};
        String[] negative = {"sudadera", "sweatshirt", "chándal", "tracksuit", "deportivo", "sportswear", "oversized", "camiseta básica", "basic t-shirt", "pijama", "pyjama"};

        score += countMatches(text, strong, 10);
        score += countMatches(text, medium, 5);
        score += countMatches(text, soft, 3);
        score += countMatches(text, palette, 2);
        score -= countMatches(text, negative, 8);
        if (!"OTRA".equals(detectCategory(text))) score += 6;
        return Math.max(0, Math.min(100, score));
    }

    private int countMatches(String text, String[] terms, int weight) {
        int total = 0;
        for (String term : terms) if (text.contains(term)) total += weight;
        return total;
    }

    private String detectCategory(String text) {
        if (containsAny(text, "conjunto", "set de", "two-piece", "two piece", "co-ord")) return "CONJUNTO";
        if (containsAny(text, "vestido", "dress", "mono ", "jumpsuit")) return "VESTIDO";
        if (containsAny(text, "falda", "skirt", "short", "pantalón", "pantalon", "trouser", "pants")) return "PARTE INFERIOR";
        if (containsAny(text, "corsé", "corset", "bustier", "top", "body", "bodysuit")) return "TOP / CORSÉ";
        return "OTRA";
    }

    private String detectColor(String text) {
        if (containsAny(text, "negro", "black")) return "NEGRO";
        if (containsAny(text, "marfil", "ivory", "crema", "cream", "off-white", "off white")) return "MARFIL";
        if (containsAny(text, "blanco", "white")) return "BLANCO";
        if (containsAny(text, "chocolate", "marrón", "marron", "brown")) return "CHOCOLATE";
        if (containsAny(text, "burdeos", "burgundy", "wine")) return "BURDEOS";
        if (containsAny(text, "rojo", "red")) return "ROJO";
        if (containsAny(text, "rosa", "pink")) return "ROSA";
        if (containsAny(text, "amarillo", "yellow")) return "AMARILLO";
        return "SIN DEFINIR";
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private String normalizeBrand(String raw, String pageText) {
        String brand = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        if (!brand.isEmpty() && brand.length() < 80) return brand;
        String[] known = {"Giorgia", "Mochy", "Frime Paris", "FP & CO", "F&P", "Copperose", "Soy & Co", "Unika Paris", "Jolio & Co"};
        for (String candidate : known) if (pageText.contains(candidate.toLowerCase(Locale.ROOT))) return candidate;
        return "Proveedor por confirmar";
    }

    private String cleanPrice(String price) {
        if (price == null) return "Precio profesional oculto";
        String clean = price.replaceAll("\\s+", " ").trim();
        if (clean.isEmpty() || clean.length() > 100) return "Precio profesional oculto";
        return clean;
    }

    private void upsertProduct(JSONObject item) throws JSONException {
        JSONArray updated = new JSONArray();
        updated.put(item);
        String newUrl = item.optString("url");
        for (int i = 0; i < products.length(); i++) {
            JSONObject current = products.optJSONObject(i);
            if (current != null && !newUrl.equals(current.optString("url"))) updated.put(current);
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
        savedTitle.setText("Selección guardada · " + list.size());
        if (list.isEmpty()) {
            savedList.addView(emptyState("Todavía no has guardado prendas. Abre una ficha y pulsa “Analizar y guardar”."));
            return;
        }
        for (JSONObject item : list) savedList.addView(productCard(item));
    }

    private View productCard(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(panelDrawable(Color.WHITE, Color.rgb(222, 212, 198), 1, 16));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(cardParams);

        String imageUrl = item.optString("image");
        if (!imageUrl.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(232, 227, 219));
            card.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)));
            loadImage(imageUrl, image);
        }

        TextView title = label(item.optString("title", "Producto"), 17, INK, true);
        title.setPadding(0, dp(9), 0, dp(2));
        card.addView(title);

        String meta = item.optString("brand", "Proveedor") + " · " + item.optString("category", "OTRA") + " · " + item.optString("color", "SIN DEFINIR");
        card.addView(label(meta, 12, MUTED, false));
        card.addView(label(item.optString("price", "Precio profesional oculto"), 13, INK, true));

        LinearLayout scoreRow = new LinearLayout(this);
        scoreRow.setOrientation(LinearLayout.HORIZONTAL);
        scoreRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView score = pill("Afinidad " + item.optInt("score") + "/100", GOLD, INK);
        scoreRow.addView(score);
        String status = item.optString("status", "revisar");
        int statusColor = "encaja".equals(status) ? GREEN : ("descartar".equals(status) ? RED : MUTED);
        TextView statusPill = pill(status.toUpperCase(Locale.ROOT), statusColor, Color.WHITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(dp(7), 0, 0, 0);
        scoreRow.addView(statusPill, statusParams);
        card.addView(scoreRow);

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        primaryActions.setPadding(0, dp(10), 0, 0);
        primaryActions.addView(actionButton("Abrir", () -> openProduct(item), Color.rgb(40, 39, 43), Color.WHITE), buttonParams(1f, dp(4)));
        primaryActions.addView(actionButton("✓ Encaja", () -> setStatus(item, "encaja"), GREEN, Color.WHITE), buttonParams(1f, dp(4)));
        primaryActions.addView(actionButton("Revisar", () -> setStatus(item, "revisar"), GOLD, INK), buttonParams(1f, dp(4)));
        card.addView(primaryActions);

        LinearLayout secondaryActions = new LinearLayout(this);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        secondaryActions.setPadding(0, dp(7), 0, 0);
        secondaryActions.addView(actionButton("Descartar", () -> setStatus(item, "descartar"), RED, Color.WHITE), buttonParams(1f, dp(4)));
        secondaryActions.addView(actionButton("Eliminar", () -> confirmDelete(item), Color.rgb(224, 220, 213), INK), buttonParams(1f, dp(4)));
        card.addView(secondaryActions);
        return card;
    }

    private void setStatus(JSONObject item, String status) {
        try {
            String id = item.optString("id");
            for (int i = 0; i < products.length(); i++) {
                JSONObject current = products.optJSONObject(i);
                if (current != null && id.equals(current.optString("id"))) current.put("status", status);
            }
            persistProducts();
            renderSaved();
        } catch (JSONException ignored) {
            toast("No se pudo actualizar el estado.");
        }
    }

    private void confirmDelete(JSONObject item) {
        new AlertDialog.Builder(this)
            .setTitle("Eliminar selección")
            .setMessage(item.optString("title", "¿Eliminar esta prenda?"))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (dialog, which) -> deleteProduct(item.optString("id")))
            .show();
    }

    private void deleteProduct(String id) {
        JSONArray updated = new JSONArray();
        for (int i = 0; i < products.length(); i++) {
            JSONObject current = products.optJSONObject(i);
            if (current != null && !id.equals(current.optString("id"))) updated.put(current);
        }
        products = updated;
        persistProducts();
        renderSaved();
    }

    private void openProduct(JSONObject item) {
        String url = item.optString("url");
        if (!url.isEmpty()) {
            browser.loadUrl(url);
            showExplore();
        }
    }

    private void renderLooks() {
        looksList.removeAllViews();
        List<JSONObject> all = productList(false);
        List<JSONObject> tops = new ArrayList<>();
        List<JSONObject> bottoms = new ArrayList<>();
        List<JSONObject> complete = new ArrayList<>();

        for (JSONObject item : all) {
            String category = item.optString("category");
            if ("TOP / CORSÉ".equals(category)) tops.add(item);
            else if ("PARTE INFERIOR".equals(category)) bottoms.add(item);
            else if ("CONJUNTO".equals(category) || "VESTIDO".equals(category)) complete.add(item);
        }

        List<Look> suggestions = new ArrayList<>();
        for (JSONObject set : complete) suggestions.add(new Look(set, null, Math.min(100, set.optInt("score") + 5)));
        for (JSONObject top : tops) {
            for (JSONObject bottom : bottoms) suggestions.add(new Look(top, bottom, compatibility(top, bottom)));
        }
        Collections.sort(suggestions, (a, b) -> Integer.compare(b.score, a.score));
        looksTitle.setText("Combinaciones sugeridas · " + suggestions.size());

        if (suggestions.isEmpty()) {
            looksList.addView(emptyState("Guarda al menos un top o corsé y una falda o pantalón para generar combinaciones."));
            return;
        }

        int limit = Math.min(16, suggestions.size());
        for (int i = 0; i < limit; i++) looksList.addView(lookCard(suggestions.get(i), i + 1));
    }

    private int compatibility(JSONObject top, JSONObject bottom) {
        int score = (top.optInt("score") + bottom.optInt("score")) / 2;
        String a = top.optString("color");
        String b = bottom.optString("color");
        if (a.equals(b) && !"SIN DEFINIR".equals(a)) score += 8;
        if (isPair(a, b, "NEGRO", "MARFIL")) score += 15;
        else if (isPair(a, b, "NEGRO", "BLANCO")) score += 13;
        else if (isPair(a, b, "NEGRO", "BURDEOS")) score += 10;
        else if (isPair(a, b, "NEGRO", "ROJO")) score += 9;
        else if (!a.equals(b)) score += 4;
        if ("encaja".equals(top.optString("status"))) score += 4;
        if ("encaja".equals(bottom.optString("status"))) score += 4;
        return Math.min(100, score);
    }

    private boolean isPair(String a, String b, String x, String y) {
        return (x.equals(a) && y.equals(b)) || (y.equals(a) && x.equals(b));
    }

    private View lookCard(Look look, int number) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(panelDrawable(Color.WHITE, Color.rgb(222, 212, 198), 1, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(11));
        card.setLayoutParams(params);

        TextView heading = label("LOOK " + String.format(Locale.ROOT, "%02d", number) + " · " + look.score + "/100", 13, GOLD, true);
        heading.setLetterSpacing(.08f);
        card.addView(heading);
        card.addView(label(look.first.optString("title", "Prenda principal"), 17, INK, true));
        if (look.second != null) {
            TextView plus = label("+  " + look.second.optString("title", "Segunda pieza"), 16, INK, true);
            plus.setPadding(0, dp(4), 0, 0);
            card.addView(plus);
            String logic = colorStory(look.first.optString("color"), look.second.optString("color"));
            TextView reason = label(logic, 12, MUTED, false);
            reason.setPadding(0, dp(7), 0, dp(9));
            card.addView(reason);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(actionButton("Abrir pieza 1", () -> openProduct(look.first), INK, Color.WHITE), buttonParams(1f, dp(4)));
            actions.addView(actionButton("Abrir pieza 2", () -> openProduct(look.second), GOLD, INK), buttonParams(1f, dp(4)));
            card.addView(actions);
        } else {
            TextView reason = label("Look completo: útil como pieza protagonista de una cápsula o microcampaña.", 12, MUTED, false);
            reason.setPadding(0, dp(7), 0, dp(9));
            card.addView(reason);
            card.addView(actionButton("Abrir producto", () -> openProduct(look.first), INK, Color.WHITE));
        }
        return card;
    }

    private String colorStory(String first, String second) {
        if (first.equals(second) && "NEGRO".equals(first)) return "Total black: la opción más nocturna, fuerte y comercial.";
        if (first.equals(second) && ("MARFIL".equals(first) || "BLANCO".equals(first))) return "Monocromía luminosa: mediterránea, editorial y delicada.";
        if (isPair(first, second, "NEGRO", "MARFIL") || isPair(first, second, "NEGRO", "BLANCO")) return "Contraste marfil y negro: inocencia visual con una lectura peligrosa.";
        if (isPair(first, second, "NEGRO", "BURDEOS") || isPair(first, second, "NEGRO", "ROJO")) return "Paleta femme fatale: intensa, nocturna y adecuada para campaña.";
        return "Combinación evaluada por afinidad estética, categoría y versatilidad.";
    }

    private List<JSONObject> productList(boolean includeDiscarded) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < products.length(); i++) {
            JSONObject item = products.optJSONObject(i);
            if (item == null) continue;
            if (!includeDiscarded && "descartar".equals(item.optString("status"))) continue;
            list.add(item);
        }
        Collections.sort(list, (a, b) -> Integer.compare(b.optInt("score"), a.optInt("score")));
        return list;
    }

    private void shareSelection() {
        if (products.length() == 0) {
            toast("No hay prendas para exportar.");
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_SUBJECT, "Selección Maldita Riviera");
        try {
            share.putExtra(Intent.EXTRA_TEXT, products.toString(2));
        } catch (JSONException ignored) {
            share.putExtra(Intent.EXTRA_TEXT, products.toString());
        }
        startActivity(Intent.createChooser(share, "Exportar selección"));
    }

    private void loadImage(String source, ImageView target) {
        imagePool.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(source).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android MalditaRivieraCurator/0.1");
                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) runOnUiThread(() -> target.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private View emptyState(String message) {
        TextView empty = label(message, 14, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(28), dp(70), dp(28), dp(70));
        empty.setBackground(panelDrawable(Color.WHITE, Color.rgb(225, 216, 204), 1, 16));
        return empty;
    }

    private TextView pill(String text, int background, int foreground) {
        TextView view = label(text, 11, foreground, true);
        view.setPadding(dp(9), dp(5), dp(9), dp(5));
        view.setBackground(panelDrawable(background, background, 0, 30));
        return view;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private Button navButton(String text, Runnable action) {
        Button button = actionButton(text, action, Color.TRANSPARENT, Color.WHITE);
        button.setTextSize(12);
        return button;
    }

    private Button actionButton(String text, Runnable action, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(panelDrawable(background, background, 0, 12));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private GradientDrawable panelDrawable(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams buttonParams(float weight, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), weight);
        params.setMargins(margin, 0, margin, 0);
        return params;
    }

    private FrameLayout.LayoutParams matchParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static class Look {
        final JSONObject first;
        final JSONObject second;
        final int score;

        Look(JSONObject first, JSONObject second, int score) {
            this.first = first;
            this.second = second;
            this.score = score;
        }
    }
}
