package br.com.wr.tvprecos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Painel de precos.
 *
 * Fonte de tudo = repositorio no GitHub (Kevintorcato/tv-precos):
 *   tv/<loja>/<setor>/<tvN>/  { config.json, index.html, version.txt }
 *
 * Fluxo:
 *  - 1a vez: abre o NAVEGADOR DE PASTAS (lista via GitHub Contents API).
 *    Voce navega loja > setor > tv e seleciona a pasta.
 *  - A pasta escolhida fica salva. O app baixa config.json + index.html + version.txt,
 *    gira a tela conforme a orientacao e mostra o painel.
 *  - A cada X min confere o version.txt daquela pasta; mudou -> rebaixa e recarrega (OTA).
 *  - Toque longo = menu (trocar TV / atualizar agora).
 *
 * Quiosque em 2 camadas (sem ADB): tela cheia imersiva + app como HOME/launcher + boot.
 */
public class MainActivity extends Activity {

    // ---- repo (publico) ----
    private static final String OWNER  = "Kevintorcato";
    private static final String REPO   = "tv-precos";
    private static final String BRANCH = "main";
    private static final String ROOT   = "tv";   // pasta raiz do navegador

    private static final String PREFS = "tvprecos";
    private int checkMinutes = 10;

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean rodando = true;
    private String path;  // pasta selecionada, ex: tv/loja2/acougue/tv1

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        telaCheia();

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        web.setWebViewClient(new WebViewClient());
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { menu(); return true; }
        });

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        path = p.getString("path", null);

        if (path == null) {
            navegar(ROOT);                 // 1a vez: escolher a pasta
        } else {
            renderDoCache();               // mostra o que tem em cache (resiliente a offline)
            ui.postDelayed(new Runnable(){ public void run(){ baixar(false); } }, 1500);
        }
        ui.postDelayed(loopOta, 5000);
    }

    // ===================== navegador de pastas =====================

    private void navegar(final String p) {
        new Thread(new Runnable(){ public void run(){
            try {
                String json = http(apiContents(p), true);
                JSONArray arr = new JSONArray(json);
                final java.util.ArrayList<String> dirs = new java.util.ArrayList<String>();
                final java.util.ArrayList<String> dirPaths = new java.util.ArrayList<String>();
                boolean temConfig = false;
                for (int i=0;i<arr.length();i++){
                    JSONObject o = arr.getJSONObject(i);
                    if ("dir".equals(o.optString("type"))) {
                        dirs.add(o.optString("name"));
                        dirPaths.add(o.optString("path"));
                    } else if ("config.json".equals(o.optString("name"))) {
                        temConfig = true;
                    }
                }
                final boolean selecionavel = temConfig;
                ui.post(new Runnable(){ public void run(){
                    mostrarLista(p, dirs, dirPaths, selecionavel);
                }});
            } catch (final Exception e) {
                ui.post(new Runnable(){ public void run(){ erroRede(p); }});
            }
        }}).start();
    }

    private void mostrarLista(final String p, final java.util.ArrayList<String> dirs,
                              final java.util.ArrayList<String> dirPaths, boolean selecionavel) {
        final java.util.ArrayList<String> rotulos = new java.util.ArrayList<String>();
        final java.util.ArrayList<String> acoes = new java.util.ArrayList<String>(); // "sel" | caminho | "voltar"
        if (selecionavel) { rotulos.add("✅ USAR ESTA TV"); acoes.add("sel"); }
        for (int i=0;i<dirs.size();i++){ rotulos.add("📁 " + dirs.get(i)); acoes.add(dirPaths.get(i)); }
        if (!p.equals(ROOT)) { rotulos.add("⬅ Voltar"); acoes.add("voltar"); }

        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setTitle("Selecione a pasta  (" + p + ")");
        bld.setItems(rotulos.toArray(new String[0]), new android.content.DialogInterface.OnClickListener(){
            public void onClick(android.content.DialogInterface d, int which){
                String a = acoes.get(which);
                if (a.equals("sel")) { selecionar(p); }
                else if (a.equals("voltar")) { navegar(pai(p)); }
                else { navegar(a); }
            }
        });
        bld.setCancelable(path != null); // 1a vez nao deixa cancelar sem escolher
        bld.show();
    }

    private void erroRede(final String p) {
        new AlertDialog.Builder(this)
            .setTitle("Sem conexao")
            .setMessage("Nao consegui listar as pastas do GitHub.\nConfira a internet da box.")
            .setPositiveButton("Tentar de novo", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d,int w){ navegar(p); }})
            .setNegativeButton("Fechar", null)
            .setCancelable(true).show();
    }

    private void selecionar(String p) {
        path = p;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("path", p).remove("ver").apply();
        baixar(true);
    }

    private void menu() {
        new AlertDialog.Builder(this)
            .setTitle("Painel")
            .setItems(new String[]{"Trocar TV / pasta", "Atualizar agora"}, new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int which){
                    if (which==0) navegar(ROOT);
                    else new Thread(new Runnable(){ public void run(){ baixar(true); }}).start();
                }
            })
            .setNegativeButton("Fechar", null).show();
    }

    // ===================== baixar + render =====================

    /** Baixa config.json + index.html + version.txt da pasta e mostra. */
    private void baixar(final boolean forcar) {
        if (path == null) return;
        new Thread(new Runnable(){ public void run(){
            try {
                String sha = getSha();                 // SHA do commit = versao (URL imutavel, sem cache velho)
                if (sha == null) { semRede(); return; }
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                if (!forcar && sha.equals(p.getString("ver","")) && cacheOk()) return;

                String base = rawAt(sha, path);
                String cfg  = http(base + "/config.json", false);
                String html = http(base + "/index.html", false);
                if (cfg == null || html == null) { semRede(); return; }

                salvar("config.json", cfg);
                salvar("index.html", html);
                p.edit().putString("ver", sha).apply();
                ui.post(new Runnable(){ public void run(){ renderDoCache(); }});
            } catch (Exception e) { semRede(); }
        }}).start();
    }

    private void semRede() {
        ui.post(new Runnable(){ public void run(){
            if (!cacheOk() && path != null) {
                // nada em cache ainda: volta pro navegador pra tentar de novo
                erroRede(path);
            }
            // se tem cache, so mantem o que esta na tela
        }});
    }

    private void renderDoCache() {
        try {
            JSONObject cfg = new JSONObject(ler("config.json"));
            String server = cfg.optString("server", "");
            int codTv = cfg.optInt("cod_tv", 1);
            String titulo = cfg.optString("titulo", "Ofertas do Dia");
            String orient = cfg.optString("orientacao", "paisagem");

            setRequestedOrientation(
                ehRetrato(orient) ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                  : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

            String html = ler("index.html");
            JSONObject tv = new JSONObject();
            tv.put("server", server); tv.put("cod_tv", codTv); tv.put("titulo", titulo);
            String inj = "<script>window.TV_CONFIG=" + tv.toString() + ";</script>";
            if (html.contains("</head>")) html = html.replace("</head>", inj + "</head>");
            else html = inj + html;

            String bu = server.endsWith("/") ? server : server + "/";
            web.loadDataWithBaseURL(bu, html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            web.loadDataWithBaseURL(null,
                "<body style='background:#0e1116;color:#fff;font-family:sans-serif'>"
                + "<h2 style='padding:20px'>Selecione a pasta da TV (toque longo)</h2></body>",
                "text/html", "UTF-8", null);
        }
    }

    private boolean ehRetrato(String o){
        if (o == null) return false;
        o = o.toLowerCase();
        return o.startsWith("retr") || o.startsWith("port") || o.startsWith("vert") || o.equals("pe");
    }

    // ===================== OTA =====================

    private final Runnable loopOta = new Runnable() {
        @Override public void run() {
            if (path != null) new Thread(new Runnable(){ public void run(){ checarVersao(); }}).start();
            if (rodando) ui.postDelayed(this, (long) checkMinutes * 60L * 1000L);
        }
    };

    private void checarVersao() {
        try {
            String sha = getSha();
            if (sha == null) return;
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            if (!sha.equals(p.getString("ver", "")) || !cacheOk()) baixar(true);
        } catch (Exception ignore) {}
    }

    /** SHA do ultimo commit do branch (via API). URL imutavel = nunca pega cache velho. */
    private String getSha() {
        try {
            String j = http("https://api.github.com/repos/" + OWNER + "/" + REPO + "/commits/" + BRANCH, true);
            if (j == null) return null;
            return new JSONObject(j).optString("sha", null);
        } catch (Exception e) { return null; }
    }
    private String rawAt(String sha, String p){
        return "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/" + sha + "/" + p;
    }

    // ===================== http =====================

    private String apiContents(String p){
        return "https://api.github.com/repos/" + OWNER + "/" + REPO + "/contents/" + p + "?ref=" + BRANCH;
    }
    private String raw(String p){
        return "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/" + BRANCH + "/" + p;
    }

    private String http(String urlStr, boolean api) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(9000);
        c.setReadTimeout(9000);
        c.setRequestProperty("User-Agent", "WR-TVPrecos");          // GitHub exige UA
        c.setRequestProperty("Cache-Control", "no-cache");
        if (api) c.setRequestProperty("Accept", "application/vnd.github+json");
        c.connect();
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) { c.disconnect(); return null; }
        InputStream in = c.getInputStream();
        String out = lerStream(in);
        in.close(); c.disconnect();
        return out;
    }

    // ===================== util =====================

    private String pai(String p){
        int i = p.lastIndexOf('/');
        return (i <= 0) ? ROOT : p.substring(0, i);
    }

    private boolean cacheOk(){
        return new File(getFilesDir(), "index.html").exists()
            && new File(getFilesDir(), "config.json").exists();
    }
    private void salvar(String nome, String conteudo) throws Exception {
        FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), nome));
        fo.write(conteudo.getBytes("UTF-8")); fo.close();
    }
    private String ler(String nome) throws Exception {
        return lerStream(new FileInputStream(new File(getFilesDir(), nome)));
    }
    private String lerStream(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
        return new String(bo.toByteArray(), "UTF-8");
    }

    private void telaCheia() {
        View d = getWindow().getDecorView();
        d.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean f){ super.onWindowFocusChanged(f); if (f) telaCheia(); }
    @Override protected void onDestroy(){ rodando = false; super.onDestroy(); }
}
