package com.malditariviera.curator;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Locale;

public class PublicScoutActivity extends MainActivity {
    private static final String BASE_URL =
        "https://parisfashionshops.com/es/women/wholesalers";
    private static final String PREFS = "maldita_riviera_curator";
    private static final String PUBLIC_SEARCH_INDEX = "public_search_index_v3";

    private static final String[] PUBLIC_SEARCHES = {
        "conjunto corsé encaje",
        "corset set satin",
        "top corsé falda mini",
        "conjunto negro encaje",
        "conjunto marfil satén",
        "vestido corsé ajustado",
        "falda satinada mini",
        "bustier lace set",
        "conjunto burdeos corsé",
        "polka dot set",
        "Giorgia corset set",
        "Mochy corset",
        "Frime Paris satin skirt",
        "F&P lace set",
        "Copperose cut out dress"
    };

    private WebView publicBrowser;
    private TextView publicModeStatus;
    private String pendingQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean("privacy_notice_v2_seen", true)
            .apply();

        super.onCreate(savedInstanceState);

        View content = findViewById(android.R.id.content);
        publicBrowser = findWebView(content);
        adaptExistingInterface(content);
        installPublicNavigation();

        if (!getSharedPreferences(PREFS, MODE_PRIVATE)
            .getBoolean("public_mode_v3_seen", false)) {
            new AlertDialog.Builder(this)
                .setTitle("Scouting sin cuenta")
                .setMessage(
                    "Esta versión evita los formularios profesionales y trabaja únicamente " +
                    "con el catálogo público. Los precios, packs, tallas y existencias que no " +
                    "sean visibles quedarán pendientes para una fase posterior. La app no " +
                    "compra ni rellena datos de empresa."
                )
                .setPositiveButton("Empezar", (dialog, which) ->
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit()
                        .putBoolean("public_mode_v3_seen", true)
                        .apply())
                .show();
        }
    }

    private void adaptExistingInterface(View content) {
        replaceText(content,
            "CURADOR DE COLECCIÓN · MEDITERRANEAN AFTER DARK",
            "SCOUTING PÚBLICO · MEDITERRANEAN AFTER DARK");
        replaceText(content,
            "Selección inteligente",
            "Preselección pública");
        replaceText(content,
            "Combinaciones sugeridas",
            "Looks provisionales");
        replaceText(content,
            "Encaja se asigna automáticamente a las coincidencias fuertes; Revisar conserva propuestas prometedoras.",
            "Guardamos candidatas visuales. Precio, pack, stock y tallaje pueden seguir pendientes.");
        replaceText(content,
            "Primero muestra conjuntos completos y después combina corsés/tops con partes inferiores compatibles.",
            "Primero muestra conjuntos completos y después crea looks provisionales con las prendas públicas.");

        Button home = findButton(content, "Inicio");
        if (home != null) {
            home.setText("Catálogo público");
            home.setOnClickListener(view -> openPublicCatalogue());
        }

        Button search = findButton(content, "Buscar universo");
        if (search != null) {
            search.setOnClickListener(view -> launchPublicSearch());
        }

        Button curate = findButton(content, "Curar página");
        if (curate != null) curate.setText("Curar visibles");

        addPublicModeBanner(content);
    }

    private void addPublicModeBanner(View content) {
        if (!(content instanceof FrameLayout)) return;
        FrameLayout systemContent = (FrameLayout) content;
        if (systemContent.getChildCount() == 0) return;
        View appRoot = systemContent.getChildAt(0);
        if (!(appRoot instanceof LinearLayout)) return;

        LinearLayout root = (LinearLayout) appRoot;
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(12), dp(7), dp(12), dp(7));
        banner.setBackgroundColor(Color.rgb(38, 76, 112));

        TextView mode = new TextView(this);
        mode.setText("MODO PÚBLICO · NO NECESITA CUENTA");
        mode.setTextColor(Color.WHITE);
        mode.setTextSize(11);
        mode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        banner.addView(mode, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ));

        publicModeStatus = new TextView(this);
        publicModeStatus.setText("Catálogo");
        publicModeStatus.setTextColor(Color.rgb(224, 234, 244));
        publicModeStatus.setTextSize(10);
        publicModeStatus.setGravity(Gravity.END);
        banner.addView(publicModeStatus, new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ));

        int insertionIndex = Math.min(2, root.getChildCount());
        root.addView(
            banner,
            insertionIndex,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        );
    }

    private void installPublicNavigation() {
        if (publicBrowser == null) return;

        publicBrowser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                WebView view,
                WebResourceRequest request
            ) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                if (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme)) {
                    return false;
                }

                String url = uri.toString();
                if (isAccountOrRegistrationUrl(url)) {
                    setPublicStatus("Registro bloqueado");
                    toast("No necesitas completar ese formulario para hacer scouting.");
                    view.loadUrl(BASE_URL);
                    return true;
                }

                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (isAccountOrRegistrationUrl(url)) {
                    view.loadUrl(BASE_URL);
                    return;
                }

                setPublicStatus(shortPageName(url));
                sanitizePublicPage();

                if (pendingQuery != null) {
                    String query = pendingQuery;
                    pendingQuery = null;
                    view.postDelayed(() -> submitSearch(query), 650);
                }
            }
        });
    }

    private boolean isAccountOrRegistrationUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/register")
            || lower.contains("/registration")
            || lower.contains("/signup")
            || lower.contains("/sign-up")
            || lower.contains("/create-account")
            || lower.contains("/professional-registration")
            || lower.contains("/login")
            || lower.contains("/sign-in")
            || lower.contains("/signin")
            || lower.contains("/account/create");
    }

    private void sanitizePublicPage() {
        if (publicBrowser == null) return;

        String script = """
            (() => {
              const clean = value => (value || '')
                .replace(/\\s+/g, ' ')
                .trim()
                .toLowerCase();
              const body = clean(document.body?.innerText || '');
              const registration = body.includes('número de iva')
                && body.includes('justificante de empresa');

              const closeWords = [
                'cerrar', 'close', 'continuar sin cuenta',
                'seguir navegando', 'más tarde', 'not now'
              ];

              for (const button of document.querySelectorAll(
                'button, [role="button"], a'
              )) {
                const text = clean(
                  button.innerText || button.getAttribute('aria-label') || ''
                );
                if (!closeWords.some(word =>
                    text === word || text.includes(word))) continue;

                const box = button.closest(
                  '[role="dialog"], [class*="modal"], [class*="popup"], ' +
                  '[class*="drawer"], [class*="overlay"]'
                );
                if (box) {
                  try { button.click(); } catch (_) {}
                }
              }

              for (const node of document.querySelectorAll(
                '[role="dialog"], [class*="modal"], [class*="popup"], ' +
                '[class*="drawer"], [class*="overlay"]'
              )) {
                const text = clean(node.innerText || '');
                const style = getComputedStyle(node);
                const blocking = style.position === 'fixed'
                  || style.position === 'sticky';

                if (blocking && text.length < 1000
                    && text.includes('crear una cuenta')
                    && (text.includes('acceso')
                      || text.includes('iniciar sesión'))) {
                  node.style.display = 'none';
                }
              }

              return JSON.stringify({registration});
            })()
            """;

        publicBrowser.evaluateJavascript(script, raw -> {
            if (raw == null) return;
            if (raw.contains("\\\"registration\\\":true")
                || raw.contains("\"registration\":true")) {
                setPublicStatus("Saliendo del formulario");
                publicBrowser.loadUrl(BASE_URL);
            }
        });
    }

    private void openPublicCatalogue() {
        pendingQuery = null;
        setPublicStatus("Abriendo catálogo");
        if (publicBrowser != null) publicBrowser.loadUrl(BASE_URL);
    }

    private void launchPublicSearch() {
        int index = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getInt(PUBLIC_SEARCH_INDEX, 0);
        String query = PUBLIC_SEARCHES[index % PUBLIC_SEARCHES.length];

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putInt(PUBLIC_SEARCH_INDEX, (index + 1) % PUBLIC_SEARCHES.length)
            .apply();

        pendingQuery = query;
        setPublicStatus("Buscando: " + query);
        if (publicBrowser != null) publicBrowser.loadUrl(BASE_URL);
    }

    private void submitSearch(String query) {
        if (publicBrowser == null) return;
        String escaped = JSONObject.quote(query);

        String script = String.format(Locale.ROOT, """
            (() => {
              const query = %s;
              const inputs = [...document.querySelectorAll('input')];
              const input = inputs.find(el => {
                const hint = (
                  (el.placeholder || '') + ' ' +
                  (el.getAttribute('aria-label') || '') + ' ' +
                  (el.name || '')
                ).toLowerCase();
                return hint.includes('buscar') || hint.includes('search');
              });

              if (!input) return JSON.stringify({ok:false});

              const setter = Object.getOwnPropertyDescriptor(
                HTMLInputElement.prototype,
                'value'
              )?.set;
              if (setter) setter.call(input, query);
              else input.value = query;

              input.focus();
              input.dispatchEvent(new Event('input', {bubbles:true}));
              input.dispatchEvent(new Event('change', {bubbles:true}));

              const form = input.closest('form');
              const button = form?.querySelector(
                'button[type="submit"], input[type="submit"]'
              ) || input.parentElement?.querySelector('button')
                || input.parentElement?.parentElement?.querySelector('button');

              if (button) {
                button.click();
                return JSON.stringify({ok:true, submitted:true});
              }

              input.dispatchEvent(new KeyboardEvent('keydown', {
                key:'Enter', code:'Enter', keyCode:13,
                which:13, bubbles:true
              }));
              input.dispatchEvent(new KeyboardEvent('keyup', {
                key:'Enter', code:'Enter', keyCode:13,
                which:13, bubbles:true
              }));
              if (form && typeof form.requestSubmit === 'function') {
                form.requestSubmit();
              }
              return JSON.stringify({ok:true, submitted:false});
            })()
            """, escaped);

        publicBrowser.evaluateJavascript(script, raw -> {
            if (raw == null || raw.contains("\\\"ok\\\":false")
                || raw.contains("\"ok\":false")) {
                toast("No encontré el buscador. Vuelve al catálogo y pulsa Buscar universo otra vez.");
                return;
            }
            setPublicStatus("Resultados: " + query);
        });
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (!(view instanceof ViewGroup)) return null;

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            WebView found = findWebView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private Button findButton(View view, String text) {
        if (view instanceof Button) {
            Button button = (Button) view;
            if (text.contentEquals(button.getText())) return button;
        }
        if (!(view instanceof ViewGroup)) return null;

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findButton(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private void replaceText(View view, String oldText, String newText) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (oldText.contentEquals(textView.getText())) {
                textView.setText(newText);
            }
        }

        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            replaceText(group.getChildAt(i), oldText, newText);
        }
    }

    private String shortPageName(String url) {
        if (url == null || url.isEmpty()) return "Catálogo";
        String clean = url
            .replace("https://", "")
            .replace("http://", "");
        if (clean.length() > 34) clean = clean.substring(0, 34) + "…";
        return clean;
    }

    private void setPublicStatus(String text) {
        if (publicModeStatus != null) publicModeStatus.setText(text);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(
            value * getResources().getDisplayMetrics().density
        );
    }
}
