package app.formfill.test;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.ValueCallback;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONObject;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Proof of concept: open an official portal inside the app and fill its form
 * fields from the app, with no backend access. Captcha, OTP and password
 * fields are deliberately left for the human. Nothing is ever submitted.
 */
public class MainActivity extends Activity {

    private static final String[][] PORTALS = {
            {"PM-KISAN", "https://pmkisan.gov.in/"},
            {"AgriStack", "https://mhfr.agristack.gov.in/farmer-registry-mh/"},
            {"MahaDBT", "https://mahadbt.maharashtra.gov.in/"},
            {"PMFBY", "https://pmfby.gov.in/"},
            {"Aaple Sarkar", "https://aaplesarkar.mahaonline.gov.in/"},
    };

    // Fills every visible field except captcha / OTP / password, using the
    // native value setter so Angular/React/jQuery portals register the change.
    private static final String FILL_JS =
            "(function(){var r={filled:0,selects:0,skipped:[],frames:0,blockedFrames:0};"
          + "var skip=/captcha|otp|password|pwd|security|verif/i;"
          + "var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
          + "var tset=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;"
          + "function fire(e){['input','change','blur','keyup'].forEach(function(t){e.dispatchEvent(new Event(t,{bubbles:true}));});}"
          + "function visible(e){var s=e.ownerDocument.defaultView.getComputedStyle(e);return e.offsetParent!==null&&s.visibility!=='hidden'&&!e.disabled&&!e.readOnly;}"
          + "function run(d){d.querySelectorAll('input,textarea,select').forEach(function(e){"
          + " if(!visible(e))return;var key=(e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'');"
          + " var t=(e.type||'text').toLowerCase();"
          + " if(['hidden','submit','button','reset','image','file','checkbox','radio'].indexOf(t)>=0)return;"
          + " if(t==='password'||skip.test(key)){r.skipped.push((e.name||e.id||e.placeholder||t));return;}"
          + " if(e.tagName==='SELECT'){if(e.options.length>1){e.selectedIndex=1;fire(e);r.selects++;}return;}"
          + " var v=(t==='tel'||t==='number'||/mobile|phone|aadhaar|aadhar|uid|account|pin/i.test(key))?'9999999999':(t==='email'?'test@example.com':(t==='date'?'2000-01-01':'TEST'+(r.filled+1)));"
          + " e.focus();(e.tagName==='TEXTAREA'?tset:setter).call(e,v);fire(e);"
          + " e.style.outline='3px solid #16a34a';r.filled++;});"
          + " d.querySelectorAll('iframe').forEach(function(f){try{r.frames++;run(f.contentDocument);}catch(x){r.blockedFrames++;}});}"
          + "run(document);return JSON.stringify(r);})()";

    private static final String SCAN_JS =
            "(function(){var a=document.querySelectorAll('input,textarea,select');var v=0;"
          + "a.forEach(function(e){if(e.offsetParent!==null&&e.type!=='hidden')v++;});"
          + "var cap=document.querySelectorAll('img[src*=aptcha],img[id*=aptcha],[class*=aptcha],[id*=aptcha],iframe[src*=recaptcha]').length;"
          + "return JSON.stringify({title:document.title,visibleFields:v,totalFields:a.length,captchaElements:cap,iframes:document.querySelectorAll('iframe').length});})()";

    private static final int REQ_PICK_DOC = 1;
    private static final int REQ_CHOOSER = 2;

    private WebView web;
    private ValueCallback<Uri[]> chooserCallback;
    private Uri savedDoc;
    private String savedName, savedMime;
    private byte[] savedBytes;
    private TextView status;
    private EditText urlBar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout portals = new LinearLayout(this);
        for (String[] p : PORTALS) {
            Button btn = small(p[0]);
            btn.setOnClickListener(v -> load(p[1]));
            portals.addView(btn);
        }
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.addView(portals);
        root.addView(scroll);

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setTextSize(13);
        urlBar.setHint("Type any URL and press Go");
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setOnEditorActionListener((v, id, e) -> {
            String u = urlBar.getText().toString().trim();
            if (!u.startsWith("http")) u = "https://" + u;
            load(u);
            return true;
        });
        root.addView(urlBar);

        LinearLayout actions = new LinearLayout(this);
        Button fill = small("FILL TEST");
        fill.setBackgroundColor(Color.parseColor("#16a34a"));
        fill.setTextColor(Color.WHITE);
        fill.setOnClickListener(v -> runJs(FILL_JS, "Fill result"));
        Button scan = small("SCAN PAGE");
        scan.setOnClickListener(v -> runJs(SCAN_JS, "Page scan"));
        Button pick = small("PICK DOC");
        pick.setOnClickListener(v -> openPicker(REQ_PICK_DOC));
        Button attach = small("ATTACH TEST");
        attach.setBackgroundColor(Color.parseColor("#2563eb"));
        attach.setTextColor(Color.WHITE);
        attach.setOnClickListener(v -> attachSaved());
        Button back = small("BACK");
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        actions.addView(fill);
        actions.addView(scan);
        actions.addView(pick);
        actions.addView(attach);
        actions.addView(back);
        HorizontalScrollView actionScroll = new HorizontalScrollView(this);
        actionScroll.addView(actions);
        root.addView(actionScroll);

        status = new TextView(this);
        status.setPadding(16, 8, 16, 8);
        status.setTextSize(12);
        status.setBackgroundColor(Color.parseColor("#f1f5f9"));
        status.setText("Open a portal, go to a form page, then tap FILL TEST. For uploads: PICK DOC once, then ATTACH TEST. Nothing is ever submitted.");
        root.addView(status);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setWebChromeClient(new WebChromeClient() {
            // Makes the portal's own "Choose file" button work inside the app.
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (chooserCallback != null) chooserCallback.onReceiveValue(null);
                chooserCallback = cb;
                if (savedDoc == null) { openPicker(REQ_CHOOSER); return true; }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Attach a document")
                        .setItems(new String[]{"Use saved: " + savedName, "Choose another file"}, (d, which) -> {
                            if (which == 0) { deliver(new Uri[]{savedDoc}); }
                            else openPicker(REQ_CHOOSER);
                        })
                        .setOnCancelListener(d -> deliver(null))
                        .show();
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                urlBar.setText(url);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        load(PORTALS[0][1]);
    }

    private void openPicker(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/*"});
        startActivityForResult(i, req);
    }

    private void deliver(Uri[] uris) {
        if (chooserCallback != null) chooserCallback.onReceiveValue(uris);
        chooserCallback = null;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        Uri uri = (res == RESULT_OK && data != null) ? data.getData() : null;
        if (req == REQ_CHOOSER) { deliver(uri == null ? null : new Uri[]{uri}); return; }
        if (req != REQ_PICK_DOC || uri == null) return;
        try {
            savedBytes = readAll(uri);
            savedDoc = uri;
            savedMime = getContentResolver().getType(uri);
            if (savedMime == null) savedMime = "application/octet-stream";
            savedName = "document";
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) savedName = c.getString(idx);
                }
            }
            status.setText("Saved document: " + savedName + " (" + (savedBytes.length / 1024) + " KB, " + savedMime
                    + "). Open a form with an upload box and tap ATTACH TEST.");
        } catch (Exception e) {
            status.setText("Could not read document: " + e.getMessage());
        }
    }

    private byte[] readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return readStream(in);
        }
    }

    // Puts the saved document into every upload box on the page, the same way
    // the app would attach a farmer's Aadhaar or 7/12 from their profile.
    private void attachSaved() {
        if (savedBytes == null) { status.setText("Tap PICK DOC first to choose a test document (PDF or photo)."); return; }
        try {
            String js;
            try (InputStream in = getAssets().open("attach.js")) {
                js = new String(readStream(in), "UTF-8");
            }
            JSONObject doc = new JSONObject();
            doc.put("name", savedName);
            doc.put("mime", savedMime);
            doc.put("b64", Base64.encodeToString(savedBytes, Base64.NO_WRAP));
            runJs("(" + js.trim() + ")(" + doc + ")", "Attach result");
        } catch (Exception e) {
            status.setText("Attach failed: " + e.getMessage());
        }
    }

    private static byte[] readStream(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private Button small(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        return b;
    }

    private void load(String url) {
        status.setText("Loading " + url);
        web.loadUrl(url);
    }

    private void runJs(String js, String label) {
        web.evaluateJavascript(js, value -> status.setText(label + ": " + value
                .replace("\\\"", "\"").replace("\"{", "{").replace("}\"", "}")));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
