package com.notebook;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Notebook";

    private WebView web;
    private SpeechRecognizer recognizer;
    private boolean listening;

    private ActivityResultLauncher<String> importLauncher;
    private ActivityResultLauncher<String> exportLauncher;
    private String pendingExportJson = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        initSpeech();
        configureWebView();
        registerLaunchers();
        checkPermissions();

        web.loadUrl("file:///android_asset/index.html");
    }

    /* ==================== WebView ==================== */
    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);            // 关键：localStorage 可用
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        Bridge bridge = new Bridge();
        web.addJavascriptInterface(bridge, "AndroidBridge");
        web.addJavascriptInterface(bridge, "Notebook"); // JS 里 window.Notebook.receiveImport 兼容
    }

    /* ==================== 语音：原生 SpeechRecognizer ==================== */
    private void initSpeech() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new Listener());
        }
    }

    private void startVoice() {
        if (recognizer == null) {
            eval("window.onVoiceError && window.onVoiceError('当前设备不支持语音识别')");
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            recognizer.startListening(intent);
            listening = true;
            eval("window.recognizing=true; if(window.updateMicBtn)window.updateMicBtn();");
        } catch (Exception e) {
            eval("window.onVoiceError && window.onVoiceError('启动语音失败：" + esc(e.getMessage()) + "')");
        }
    }

    private void stopVoice() {
        if (recognizer != null && listening) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
    }

    private class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) {}
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onPartialResults(Bundle bundle) {
            ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty()) {
                eval("window.onVoicePartial && window.onVoicePartial(" + quote(list.get(0)) + ")");
            }
        }

        @Override
        public void onResults(Bundle bundle) {
            listening = false;
            ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = (list != null && !list.isEmpty()) ? list.get(0) : "";
            eval("window.onVoiceResult && window.onVoiceResult(" + quote(text) + ")");
            eval("window.recognizing=false; if(window.updateMicBtn)window.updateMicBtn();");
        }

        @Override
        public void onError(int error) {
            listening = false;
            eval("window.onVoiceError && window.onVoiceError(" + quote(codeToMsg(error)) + ")");
            eval("window.recognizing=false; if(window.updateMicBtn)window.updateMicBtn();");
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    }

    private String codeToMsg(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: return "音频录制错误";
            case SpeechRecognizer.ERROR_CLIENT: return "客户端错误";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "缺少录音权限";
            case SpeechRecognizer.ERROR_NETWORK: return "网络错误";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "网络超时";
            case SpeechRecognizer.ERROR_NO_MATCH: return "没听清，请重试";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "识别器繁忙";
            case SpeechRecognizer.ERROR_SERVER: return "服务端错误";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "未检测到语音";
            default: return "语音识别错误码：" + code;
        }
    }

    /* ==================== JS <-> Java 桥接 ==================== */
    public class Bridge {

        /** 语音：开始录音识别 */
        @JavascriptInterface
        public void startVoice() { runOnUiThread(() -> MainActivity.this.startVoice()); }

        /** 语音：停止 */
        @JavascriptInterface
        public void stopVoice() { runOnUiThread(() -> MainActivity.this.stopVoice()); }

        /**
         * 导出：把 JSON 保存到用户选择的路径（SAF），保存后弹出系统分享。
         * filename 形如 notebook-backup-2026-09-16.json
         */
        @JavascriptInterface
        public void saveBackup(final String filename, final String json) {
            runOnUiThread(() -> {
                pendingExportJson = json == null ? "" : json;
                exportLauncher.launch(filename);
            });
        }

        /** JS 调用：打开导入文件选择器 */
        @JavascriptInterface
        public void pickImport() {
            runOnUiThread(() -> importLauncher.launch("application/json"));
        }
    }

    private void registerLaunchers() {
        // 导入：选一个 json 文件
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    try {
                        String json = readUri(uri);
                        eval("window.Notebook && window.Notebook.receiveImport(" + quote(json) + ")");
                    } catch (Exception e) {
                        toast("读取备份文件失败：" + e.getMessage());
                    }
                });

        // 导出：让用户选保存位置（SAF），保存后分享
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri == null) return;
                    try {
                        writeUri(uri, pendingExportJson);
                        toast("已保存备份文件");
                        shareUri(uri);
                    } catch (Exception e) {
                        toast("保存失败：" + e.getMessage());
                    }
                });
    }

    private void shareUri(Uri uri) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/json");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "导出备份（微信 / 蓝牙 / 保存到手机）"));
    }

    /* ==================== 权限 ==================== */
    private void checkPermissions() {
        String[] need = { Manifest.permission.RECORD_AUDIO };
        boolean ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!ok) ActivityCompat.requestPermissions(this, need, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (int i = 0; i < permissions.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                if (grantResults[i] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    toast("未授予录音权限，语音输入将不可用");
                }
            }
        }
    }

    /* ==================== 辅助 ==================== */
    private void eval(final String js) {
        if (web == null) return;
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    private String readUri(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) >= 0) sb.append(buf, 0, n);
        reader.close();
        return sb.toString();
    }

    private void writeUri(Uri uri, String text) throws Exception {
        OutputStream out = getContentResolver().openOutputStream(uri);
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write(text == null ? "" : text);
        w.close();
    }

    /** JS 字符串安全转义（含中文） */
    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append("\"").toString();
    }

    private static String esc(String s) { return s == null ? "" : s.replace("'", ""); }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
