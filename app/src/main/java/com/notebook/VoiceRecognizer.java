package com.notebook;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

/** 封装安卓原生 SpeechRecognizer，把识别结果回调给 WebView(JS)。 */
public class VoiceRecognizer {

    private static final String TAG = "NotebookVoice";

    public interface Callback {
        void onReadyForSpeech();
        void onPartial(String text);
        void onResult(String text);
        void onError(String msg);
    }

    private final Activity activity;
    private final Callback cb;
    private SpeechRecognizer recognizer;
    private boolean listening;

    public VoiceRecognizer(Activity activity, Callback cb) {
        this.activity = activity;
        this.cb = cb;
        if (SpeechRecognizer.isRecognitionAvailable(activity)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(new Listener());
        }
    }

    public boolean isAvailable() {
        return recognizer != null;
    }

    public void start() {
        if (recognizer == null) { cb.onError("当前设备不支持语音识别，请确认已安装语音输入服务"); return; }
        if (listening) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            recognizer.startListening(intent);
            listening = true;
        } catch (Exception e) {
            cb.onError("启动语音失败：" + e.getMessage());
            listening = false;
        }
    }

    public void stop() {
        if (recognizer != null && listening) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
    }

    public void destroy() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private class Listener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { cb.onReadyForSpeech(); }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() {}

        @Override
        public void onPartialResults(Bundle bundle) {
            ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty()) cb.onPartial(list.get(0));
        }

        @Override
        public void onResults(Bundle bundle) {
            listening = false;
            ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = (list != null && !list.isEmpty()) ? list.get(0) : "";
            cb.onResult(text);
        }

        @Override
        public void onError(int error) {
            listening = false;
            cb.onError(codeToMsg(error));
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
}
