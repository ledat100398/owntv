package io.github.ledat100398.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
    private static final int LISTENING = 0, THINKING = 1, MISSED = 2, LEAVING = 3;
    private static final android.view.animation.Interpolator EASE_OUT = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private View scrim, panel;
    private TextView line;
    private VoiceWave wave;
    private android.animation.ValueAnimator breathe, fade;
    private Chime chime;
    private String shown = "";
    private int phase = LISTENING;
    private boolean asking, blur;
    private long startedAt;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        chime = new Chime(this);
        buildView();
        enter();
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // The microphone pressed again reaches us here when the system routes it as "assist".
        toggle();
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

    /** Microphone pressed while the bar is up: listening stops it, a miss listens again. */
    private void toggle() {
        if (SystemClock.uptimeMillis() - startedAt < 600) return;   // the same press that opened us
        if (phase == MISSED) start();
        else if (phase != LEAVING) {
            if (recognizer != null) recognizer.cancel();
            chime.end();
            close();
        }
    }

    // ---- view ------------------------------------------------------------------------------------

    private void buildView() {
        FrameLayout root = new FrameLayout(this);
        scrim = new View(this);
        scrim.setBackgroundColor(0x66000000);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        // One capsule that never changes size: microphone, one line of words, the wave.
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.px(20), 0, Ui.px(28), 0);
        blur = android.os.Build.VERSION.SDK_INT >= 31 && getWindowManager().isCrossWindowBlurEnabled();
        box.setBackground(capsule(blur ? 0xC71C1E25 : 0xF01C1E25));
        box.setElevation(Ui.f(18));
        box.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);

        FrameLayout micWrap = new FrameLayout(this);
        View drop = new View(this);
        drop.setBackground(new Droplet(Ui.f(36)));
        micWrap.addView(drop, new FrameLayout.LayoutParams(-1, -1));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mic);
        icon.setColorFilter(Ui.DARK);
        micWrap.addView(icon, new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        box.addView(micWrap, new LinearLayout.LayoutParams(Ui.px(72), Ui.px(72)));

        line = Ui.text(this, 34, false, Ui.INK);
        line.setSingleLine(true);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Ui.px(592), -2);
        ll.leftMargin = Ui.px(24);
        ll.rightMargin = Ui.px(24);
        box.addView(line, ll);

        wave = new VoiceWave(this);
        box.addView(wave, new LinearLayout.LayoutParams(Ui.px(80), Ui.px(56)));

        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(Ui.px(840), Ui.px(112),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Ui.px(96);
        root.addView(box, bl);
        panel = box;
        setContentView(root);
    }

    /** Dark frosted glass: a tint, light pooling softly at the top, and a hairline rim. */
    private static android.graphics.drawable.Drawable capsule(int tint) {
        float rad = Ui.f(56);
        GradientDrawable base = new GradientDrawable();
        base.setColor(tint);
        base.setCornerRadius(rad);
        GradientDrawable light = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x14FFFFFF, 0x05FFFFFF, 0x00FFFFFF});
        light.setCornerRadius(rad);
        GradientDrawable rim = new GradientDrawable();
        rim.setColor(0);
        rim.setCornerRadius(rad);
        rim.setStroke(Math.max(1, Ui.px(1.5f)), 0x1AFFFFFF);
        return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{base, light, rim});
    }

    private void enter() {
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(240).start();
        panel.setAlpha(0f);
        panel.setTranslationY(Ui.f(28));
        panel.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(EASE_OUT).start();
        blurTo(Ui.px(28), 240);
    }

    /** The screen behind softens while the bar is up (Android 12 TVs that allow it). */
    private void blurTo(int radius, long ms) {
        if (!blur) return;
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        if ((lp.flags & android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND) == 0) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }
        int from = lp.getBlurBehindRadius();
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofInt(from, radius);
        a.setDuration(ms);
        a.addUpdateListener(v -> {
            android.view.WindowManager.LayoutParams p = getWindow().getAttributes();
            p.setBlurBehindRadius((int) v.getAnimatedValue());
            getWindow().setAttributes(p);
        });
        a.start();
    }

    /** "Listening…" breathes gently until the first word arrives. */
    private void waiting(int textRes) {
        shown = "";
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setText(textRes);
        line.setTextColor(Ui.INK);
        if (breathe == null) {
            breathe = android.animation.ValueAnimator.ofFloat(0.48f, 0.7f);
            breathe.setDuration(1400);
            breathe.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            breathe.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            breathe.addUpdateListener(v -> line.setAlpha((float) v.getAnimatedValue()));
        }
        if (!breathe.isStarted()) breathe.start();
    }

    private void still(float alpha) {
        if (breathe != null) breathe.cancel();
        line.setAlpha(alpha);
    }

    /** Shows what has been heard so far; words that are new fade in rather than pop. */
    private void transcript(String s) {
        if (TextUtils.isEmpty(s) || s.equals(shown)) return;
        still(1f);
        line.setEllipsize(TextUtils.TruncateAt.START);      // a long sentence keeps its newest words
        final int keep = s.startsWith(shown) ? shown.length() : 0;
        shown = s;
        if (fade != null) fade.cancel();
        final android.text.SpannableString sp = new android.text.SpannableString(s);
        fade = android.animation.ValueAnimator.ofInt(0, 255);
        fade.setDuration(keep == 0 ? 120 : 180);
        fade.addUpdateListener(v -> {
            int a = (int) v.getAnimatedValue();
            for (Object o : sp.getSpans(0, sp.length(), android.text.style.ForegroundColorSpan.class)) sp.removeSpan(o);
            sp.setSpan(new android.text.style.ForegroundColorSpan((a << 24) | (Ui.INK & 0xFFFFFF)),
                    keep, sp.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            line.setText(sp);
        });
        fade.start();
    }

    private void message(int textRes) {
        if (fade != null) fade.cancel();
        shown = "";
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setText(textRes);
        still(0.72f);
    }

    // ---- listening -------------------------------------------------------------------------------

    private void start() {
        phase = LISTENING;
        startedAt = SystemClock.uptimeMillis();
        handler.removeCallbacksAndMessages(null);
        wave.setState(VoiceWave.IDLE);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            message(R.string.voice_need_mic);
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
        waiting(R.string.voice_listening);
        chime.start();
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
        }

        @Override
        public void onBeginningOfSpeech() {
            if (phase == LISTENING) wave.setState(VoiceWave.SPEECH);
        }

        @Override
        public void onRmsChanged(float db) {
            wave.setLevel(db);
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            if (phase != LISTENING) return;
            phase = THINKING;
            wave.setState(VoiceWave.THINK);
            if (shown.isEmpty()) waiting(R.string.voice_thinking);
        }

        @Override
        public void onError(int error) {
            if (phase == LEAVING || phase == MISSED) return;
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
            if (r == null || r.isEmpty() || TextUtils.isEmpty(r.get(0))) fail(R.string.voice_not_heard);
            else act(r);
        }

        @Override
        public void onPartialResults(Bundle b) {
            ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (r != null && !r.isEmpty()) {
                if (phase == LISTENING) wave.setState(VoiceWave.SPEECH);
                transcript(r.get(0));
            }
        }

        @Override
        public void onEvent(int type, Bundle b) {
        }
    };

    private void fail(int msg) {
        phase = MISSED;
        startedAt = SystemClock.uptimeMillis();
        wave.setState(VoiceWave.REST);
        message(msg);
        handler.postDelayed(() -> {
            if (phase == MISSED) close();
        }, 4000);
    }

    // ---- acting on what was said -----------------------------------------------------------------

    private void act(List<String> said) {
        phase = THINKING;
        String best = said.get(0);
        transcript(best);
        wave.setState(VoiceWave.THINK);
        Intent app = matchApp(said);
        if (app != null) {
            chime.end();
            launch(app);
            return;
        }
        if (searchYouTube(withoutVerb(best))) chime.end();
        else fail(R.string.voice_no_youtube);
    }

    /** Words people put before an app's name: "mở YouTube", "open Netflix", "打开 YouTube". */
    private static final String[] VERBS = {"mo ung dung ", "mo app ", "mo ", "bat ", "vao ", "chay ", "xem ",
            "open ", "launch ", "start ", "打开", "启动"};

    /** "Mở bóng đá" searches YouTube for "bóng đá": the command word is not part of what to look for. */
    static String withoutVerb(String said) {
        String n = norm(said);
        for (String v : VERBS) {
            if (!n.startsWith(v) || n.length() == v.length()) continue;
            int words = v.trim().split(" ").length;
            String[] parts = said.trim().split("\\s+", words + 1);
            if (parts.length > words) return parts[words];
        }
        return said;
    }

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
            handler.postDelayed(this::close, 250);
        } catch (ActivityNotFoundException | SecurityException e) {
            fail(R.string.voice_unavailable);
        }
    }

    // ---- keys and lifetime -----------------------------------------------------------------------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_SEARCH || k == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (e.getAction() == KeyEvent.ACTION_UP) toggle();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            if (e.getAction() == KeyEvent.ACTION_UP && phase == MISSED) start();
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (recognizer != null) recognizer.cancel();
                close();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** The bar sinks and fades, the screen behind comes back into focus, then the screen goes. */
    private void close() {
        if (phase == LEAVING && isFinishing()) return;
        if (phase == LEAVING) return;
        phase = LEAVING;
        handler.removeCallbacksAndMessages(null);
        scrim.animate().alpha(0f).setDuration(200).start();
        blurTo(0, 200);
        panel.animate().alpha(0f).translationY(Ui.f(24)).setDuration(200).setInterpolator(EASE_OUT)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    /** Two soft notes up when listening starts, two down when it ends; follows the TV's sound setting. */
    private static final class Chime {
        private final android.media.SoundPool pool;
        private final int up, down;
        private final boolean on;
        private boolean upPending, ready;

        Chime(Context c) {
            on = android.provider.Settings.System.getInt(c.getContentResolver(),
                    android.provider.Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
            pool = new android.media.SoundPool.Builder().setMaxStreams(2)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .build();
            up = pool.load(c, R.raw.voice_start, 1);
            down = pool.load(c, R.raw.voice_end, 1);
            pool.setOnLoadCompleteListener((p, id, status) -> {
                if (id == up) {
                    ready = true;
                    if (upPending) start();
                }
            });
        }

        void start() {
            if (!on) return;
            if (!ready) {
                upPending = true;
                return;
            }
            upPending = false;
            pool.play(up, 0.5f, 0.5f, 1, 0, 1f);
        }

        void end() {
            if (on) pool.play(down, 0.45f, 0.45f, 1, 0, 1f);
        }

        void release() {
            pool.release();
        }
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
        if (breathe != null) breathe.cancel();
        if (fade != null) fade.cancel();
        // Let the closing notes finish before the sound pool goes.
        final Chime c = chime;
        new Handler(Looper.getMainLooper()).postDelayed(c::release, 600);
        super.onDestroy();
    }
}
