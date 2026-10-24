package com.lidesheng.hyperlyric.root.mediacard.progress;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import com.lidesheng.hyperlyric.root.mediacard.progress.view.SquigglySeekBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33, 36}, manifest = Config.NONE)
public class MediaProgressStyleHookerTest {
    private static final Object HOOK = MediaProgressStyleHooker.INSTANCE;

    public static class Holder {
        public SeekBar seekBar;
    }

    @Test
    public void reusedCardRetainsTimeConstraints() throws Exception {
        exerciseReuse(false, false);
    }

    @Test
    public void reusedCustomLayoutRetainsTimeConstraints() throws Exception {
        exerciseReuse(true, false);
    }

    @Test
    public void reusedWaveCardRetainsTimeConstraints() throws Exception {
        exerciseReuse(false, true);
    }

    @Test
    public void reusedWaveCustomLayoutRetainsTimeConstraints() throws Exception {
        exerciseReuse(true, true);
    }

    private void exerciseReuse(boolean customLayout, boolean wave) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ConstraintLayout parent = new ConstraintLayout(context);
        Holder holder = new Holder();
        holder.seekBar = new SeekBar(context);
        holder.seekBar.setId(View.generateViewId());
        SeekBar original = holder.seekBar;
        parent.addView(original, new ConstraintLayout.LayoutParams(0, 32));
        TextView elapsed = timeLabel(context);
        TextView total = timeLabel(context);
        parent.addView(elapsed, new ConstraintLayout.LayoutParams(40, 20));
        parent.addView(total, new ConstraintLayout.LayoutParams(40, 20));

        // The native first attach precedes ConstraintSet.applyTo. On subsequent
        // playback sessions SystemUI reuses the holder without applying it again.
        for (int session = 0; session < 20; session++) {
            SeekBar replacement = wave ? new SquigglySeekBar(context) : new SeekBar(context);
            replacement.setId(original.getId());
            replacement.setLayoutParams((ViewGroup.LayoutParams) call(
                    "copyLayoutParams", new Class<?>[]{View.class}, original));
            int index = parent.indexOfChild(original);
            call("replaceSeekBarView", new Class<?>[]{SeekBar.class, SeekBar.class,
                    ViewGroup.class, int.class}, original, replacement, parent, index);
            registerReplacement(holder, original, replacement, parent, index, wave);
            boolean activeCustomLayout = session < 10 ? customLayout : !customLayout;
            if (session == 0 || session == 10) {
                applyLayout(parent, replacement, elapsed, total, activeCustomLayout);
            }
            int width = session % 2 == 0 ? 400 : 600;
            measure(parent, width);
            assertLayout(parent, replacement, elapsed, total, activeCustomLayout);
            int expectedTop = replacement.getTop();
            int expectedWidth = replacement.getWidth();

            // Also retain runtime AOD/presentation changes when handing the view back.
            replacement.setAlpha(0.65f);
            int visibility = session % 2 == 0 ? View.INVISIBLE : View.GONE;
            replacement.setVisibility(visibility);
            if (wave) {
                call("restoreWaveSeekBar", new Class<?>[]{Object.class}, holder);
            } else {
                call("restoreNativeGlow", new Class<?>[]{Object.class, Field.class},
                        holder, Holder.class.getDeclaredField("seekBar"));
            }
            assertSame(original, holder.seekBar);
            assertNull(replacement.getParent());
            assertNull(registry(wave ? "waveStates" : "nativeGlowStates").get(holder));
            assertNull(registry(wave ? "waveSeekBars" : "nativeGlowSeekBars").get(holder));
            int restoredVisibility = original.getVisibility();
            original.setVisibility(View.VISIBLE); // Native expansion after leaving AOD.
            measure(parent, width);
            assertLayout(parent, original, elapsed, total, activeCustomLayout);
            assertEquals(expectedTop, original.getTop());
            assertEquals(expectedWidth, original.getWidth());
            assertEquals(visibility, restoredVisibility);
            assertEquals(0.65f, original.getAlpha(), 0f);
        }
    }

    private static TextView timeLabel(Context context) {
        TextView view = new TextView(context);
        view.setId(View.generateViewId());
        view.setText("00:00");
        return view;
    }

    private static void applyLayout(ConstraintLayout parent, SeekBar progress,
                                    TextView elapsed, TextView total, boolean custom) {
        ConstraintSet set = new ConstraintSet();
        set.clone(parent);
        int p = progress.getId(), e = elapsed.getId(), t = total.getId();
        set.connect(p, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 24);
        set.connect(e, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, 12);
        set.connect(t, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, 12);
        if (custom) {
            set.connect(p, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.LEFT, 12);
            set.connect(p, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, 12);
            set.connect(e, ConstraintSet.TOP, p, ConstraintSet.BOTTOM, 8);
            set.connect(t, ConstraintSet.TOP, p, ConstraintSet.BOTTOM, 8);
        } else {
            set.connect(p, ConstraintSet.LEFT, e, ConstraintSet.RIGHT, 8);
            set.connect(p, ConstraintSet.RIGHT, t, ConstraintSet.LEFT, 8);
            set.connect(e, ConstraintSet.TOP, p, ConstraintSet.TOP, 0);
            set.connect(t, ConstraintSet.TOP, p, ConstraintSet.TOP, 0);
        }
        set.applyTo(parent);
    }

    private static void measure(ConstraintLayout parent, int width) {
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY));
        parent.layout(0, 0, width, 160);
    }

    private static void assertLayout(ConstraintLayout parent, SeekBar progress,
                                     TextView elapsed, TextView total, boolean custom) {
        assertSame(progress, parent.getViewById(progress.getId()));
        assertEquals(3, parent.getChildCount());
        assertTrue("Progress must retain a nonzero constrained width", progress.getWidth() > 0);
        assertEquals(24, progress.getTop());
        if (custom) {
            assertEquals(progress.getBottom() + 8, elapsed.getTop());
            assertEquals(progress.getBottom() + 8, total.getTop());
        } else {
            assertEquals(elapsed.getRight() + 8, progress.getLeft());
            assertEquals(total.getLeft() - 8, progress.getRight());
            assertEquals(progress.getTop(), elapsed.getTop());
            assertEquals(progress.getTop(), total.getTop());
        }
    }

    private static void registerReplacement(Holder holder, SeekBar original, SeekBar replacement,
                                            ViewGroup parent, int index, boolean wave) throws Exception {
        Class<?> state = Class.forName(MediaProgressStyleHooker.class.getName()
                + (wave ? "$WaveSeekBarState" : "$NativeGlowState"));
        Constructor<?> constructor = state.getDeclaredConstructor(SeekBar.class,
                wave ? SquigglySeekBar.class : SeekBar.class, ViewGroup.class, int.class);
        constructor.setAccessible(true);
        registry(wave ? "waveStates" : "nativeGlowStates").put(holder,
                constructor.newInstance(original, replacement, parent, index));
        registry(wave ? "waveSeekBars" : "nativeGlowSeekBars").put(holder, replacement);
        // A wave view cannot be assigned to SystemUI's concrete native seekBar field.
        holder.seekBar = wave ? original : replacement;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> registry(String name) throws Exception {
        Field field = MediaProgressStyleHooker.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(HOOK);
    }

    private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MediaProgressStyleHooker.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(HOOK, args);
    }
}
