package io.github.ledat100398.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Voice search for the remote's microphone button. On Google TV the microphone belongs to the stock home
 * screen, which OwnTV has to remove to keep the Home button. This screen takes its place: say an app's
 * name and it opens; say anything else and YouTube searches for it.
 *
 * It is reached three ways: the Search key while OwnTV is in front, the system's assist action when the
 * install script made OwnTV the assistant (only if the TV had none), and a plain web-search intent.
 */
public class VoiceActivity extends Activity {

    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final int ASK_MIC = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private View panel, mic;
    private TextView title, heard;
    private boolean done, asking;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        buildView();
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        // Another app asked for a web search with the words already typed: no need to listen.
        String typed = intent != null ? intent.getStringExtra(SearchManager.QUERY) : null;
        if (!TextUtils.isEmpty(typed)) {
            act(java.util.Collections.singletonList(typed));
            return;
        }
        start();
    }

    // ---- view ------------------------------------------------------------------------------------

    private void buildView() {
        FrameLayout root = new FrameLayout(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.px(28), Ui.px(24), Ui.px(48), Ui.px(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEB1C1E25);
        bg.setCornerRadius(Ui.f(64));
        box.setBackground(bg);
        box.setElevation(Ui.f(24));

        FrameLayout micWrap = new FrameLayout(this);
        mic = new View(this);
        mic.setBackground(new Droplet(Ui.f(40)));
        micWrap.addView(mic, new FrameLayout.LayoutParams(Ui.px(80), Ui.px(80), Gravity.CENTER));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mic);
        icon.setColorFilter(Ui.DARK);
        micWrap.addView(icon, new FrameLayout.LayoutParams(Ui.px(40), Ui.px(40), Gravity.CENTER));
        box.addView(micWrap, new LinearLayout.LayoutParams(Ui.px(80), Ui.px(80)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(this, 34, true, Ui.INK);
        heard = Ui.text(this, 26, false, Ui.INK_DIM);
        heard.setMaxLines(2);
        heard.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(title);
        words.addView(heard);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Ui.px(620), -2);
        wl.leftMargin = Ui.px(28);
        box.addView(words, wl);

        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.px(96);
        root.addView(box, bl);
        panel = box;
        setContentView(root);

        panel.setAlpha(0f);
        panel.setTranslationY(Ui.f(60));
        panel.animate().alpha(1f).translationY(0).setDuration(320)
                .setInterpolator(new PathInterpolator(0.3f, 1.32f, 0.5f, 1f)).start();
    }

    private void show(int titleRes, String detail) {
        title.setText(titleRes);
        heard.setText(detail != null ? detail : "");
    }

    // ---- listening -------------------------------------------------------------------------------

    private void start() {
        done = false;
        handler.removeCallbacksAndMessages(null);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            show(R.string.voice_need_mic, null);
            asking = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ASK_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail(R.string.voice_unavailable);
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(listener);
        } else {
            recognizer.cancel();
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        show(R.string.voice_listening, getString(R.string.voice_hint));
        try {
            recognizer.startListening(i);
        } catch (RuntimeException e) {
            fail(R.string.voice_unavailable);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != ASK_MIC) return;
        asking = false;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) start();
        else fail(R.string.voice_no_permission);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle b) {
            show(R.string.voice_listening, getString(R.string.voice_hint));
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float db) {
            // The droplet swells a little with the voice, so it is clear the TV is hearing something.
            float s = 1f + Math.max(0f, Math.min(1f, (db + 2f) / 12f)) * 0.22f;
            mic.animate().scaleX(s).scaleY(s).setDuration(90).start();
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            mic.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            title.setText(R.string.voice_thinking);
        }

        @Override
        public void onError(int error) {
            if (done) return;
            switch (error) {
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    fail(R.string.voice_no_permission);
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                case SpeechRecognizer.ERROR_SERVER:
                    fail(R.string.voice_no_network);
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    fail(R.string.voice_unavailable);
                    break;
                default:      // nothing heard, or nothing understood
                    fail(R.string.voice_not_heard);
            }
        }

        @Override
        public void onResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r == null || r.isEmpty()) fail(R.string.voice_not_heard);
            else act(r);
        }

        @Override
        public void onPartialResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty() && !TextUtils.isEmpty(r.get(0))) heard.setText(r.get(0));
        }

        @Override
        public void onEvent(int type, Bundle b) {
        }
    };

    private void fail(int msg) {
        done = true;
        mic.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        show(msg, getString(R.string.voice_retry));
        handler.postDelayed(this::close, 3500);
    }

    // ---- acting on what was said -----------------------------------------------------------------

    private void act(List<String> said) {
        done = true;
        String best = said.get(0);
        heard.setText(best);
        Intent app = matchApp(said);
        if (app != null) {
            launch(app);
            return;
        }
        title.setText(R.string.voice_searching);
        if (!searchYouTube(best)) fail(R.string.voice_no_youtube);
    }

    /** Words people put before an app's name: "mở YouTube", "open Netflix", "打开 YouTube". */
    private static final String[] VERBS = {"mo ung dung ", "mo app ", "mo ", "bat ", "vao ", "chay ", "xem ",
            "open ", "launch ", "start ", "打开", "启动"};

    private Intent matchApp(List<String> said) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        apps.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        int bestScore = 0;
        String bestPkg = null;
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : apps) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            String label = norm(String.valueOf(ri.loadLabel(pm)));
            if (label.isEmpty()) continue;
            for (String s : said) {
                int sc = score(norm(s), label);
                if (sc > bestScore) {
                    bestScore = sc;
                    bestPkg = pkg;
                }
            }
        }
        if (bestPkg == null || bestScore < 70) return null;
        Intent i = pm.getLeanbackLaunchIntentForPackage(bestPkg);
        if (i == null) i = pm.getLaunchIntentForPackage(bestPkg);
        return i;
    }

    private static int score(String said, String label) {
        if (said.isEmpty()) return 0;
        for (String v : VERBS) {
            if (said.startsWith(v)) {
                said = said.substring(v.length()).trim();
                break;
            }
        }
        String a = said.replace(" ", ""), b = label.replace(" ", "");
        if (said.equals(label)) return 100;
        if (a.equals(b)) return 95;                                  // "you tube" -> "YouTube"
        if (b.length() >= 3 && a.startsWith(b) && a.length() - b.length() <= 6) return 85;   // "netflix app"
        if (said.length() >= 3 && label.startsWith(said + " ")) return 80;   // "fpt" -> "FPT Play"
        if (a.length() >= 4 && b.startsWith(a)) return 78;           // "netfl" -> "Netflix"
        if (b.length() >= 4 && (" " + said + " ").contains(" " + label + " ")) return 75;
        return 0;
    }

    /** Lower case, no accents (đ -> d), only letters, digits and single spaces. */
    static String norm(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        boolean space = false;
        for (int k = 0; k < n.length(); k++) {
            char c = n.charAt(k);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (Character.isLetterOrDigit(c)) {
                b.append(c);
                space = false;
            } else if (!space && b.length() > 0) {
                b.append(' ');
                space = true;
            }
        }
        return b.toString().trim();
    }

    private boolean searchYouTube(String q) {
        PackageManager pm = getPackageManager();
        Intent[] tries = {
                new Intent(Intent.ACTION_SEARCH).setPackage(YOUTUBE).putExtra(SearchManager.QUERY, q),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))
                        .setPackage(YOUTUBE),
        };
        for (Intent i : tries) {
            if (i.resolveActivity(pm) == null) continue;
            launch(i);
            return true;
        }
        return false;
    }

    private void launch(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            handler.postDelayed(this::close, 300);
        } catch (ActivityNotFoundException | SecurityException e) {
            fail(R.string.voice_unavailable);
        }
    }

    // ---- keys and lifetime -----------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean again = k == KeyEvent.KEYCODE_SEARCH || k == KeyEvent.KEYCODE_VOICE_ASSIST
                || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
        if (again) {
            // The microphone (or OK) after a miss listens again; while listening it does nothing.
            if (e.getAction() == KeyEvent.ACTION_UP && done) start();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void close() {
        if (isFinishing()) return;
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Out of sight (an app opened, Home pressed): stop listening and go away.
        if (recognizer != null) recognizer.cancel();
        // The permission screen covers this one on a TV; do not close underneath it.
        if (!isFinishing() && !asking) finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }
}
