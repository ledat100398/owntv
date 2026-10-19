package io.github.ledat100398.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.provider.Settings;

/** A soft tick on every focus move and a duller knock at an edge, played with low latency. */
final class Sounds {

    private final Context ctx;
    private final SoundPool pool;
    private final int tick, knock;

    Sounds(Context c) {
        ctx = c.getApplicationContext();
        pool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tick = pool.load(ctx, R.raw.tick, 1);
        knock = pool.load(ctx, R.raw.knock, 1);
    }

    /** Follows the TV's own "sound effects" switch. */
    private boolean on() {
        return Settings.System.getInt(ctx.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) != 0;
    }

    void tick() {
        if (on()) pool.play(tick, 0.45f, 0.45f, 1, 0, 1f);
    }

    void knock() {
        if (on()) pool.play(knock, 0.55f, 0.55f, 1, 0, 1f);
    }

    void release() {
        pool.release();
    }
}
