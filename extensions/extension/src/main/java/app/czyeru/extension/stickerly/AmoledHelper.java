package app.czyeru.extension.stickerly;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

public final class AmoledHelper {
    private static final int TAG_THEMED = 0x7f0b7777;
    private static boolean initialized = false;

    private AmoledHelper() {}

    public static void init(Application app) {
        if (initialized || app == null) return;
        initialized = true;

        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                styleWindow(activity.getWindow());
            }

            @Override
            public void onActivityStarted(Activity activity) {
                styleWindow(activity.getWindow());
            }

            @Override
            public void onActivityResumed(Activity activity) {
                Window window = activity.getWindow();
                styleWindow(window);
                if (window != null && window.getDecorView() != null) {
                    themeHierarchy(window.getDecorView());
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    public static void onDialogStart(Dialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        styleWindow(window);
        if (window != null && window.getDecorView() != null) {
            themeHierarchy(window.getDecorView());
        }
    }

    public static void styleWindow(Window window) {
        if (window == null) return;
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.BLACK);
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        View decor = window.getDecorView();
        if (decor != null) {
            int flags = decor.getSystemUiVisibility();
            // Clear SYSTEM_UI_FLAG_LIGHT_STATUS_BAR (0x2000) and SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR (0x10)
            flags &= ~0x2010;
            decor.setSystemUiVisibility(flags);
        }
    }

    public static int mapBackgroundColor(int color) {
        int a = (color >>> 24) & 0xFF;
        if (a == 0) return color;

        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        // Preserve vibrant accent/brand colors (Stickerly blue, yellow, etc.)
        if (Math.abs(r - g) > 18 || Math.abs(g - b) > 18 || Math.abs(r - b) > 18) {
            return color;
        }

        int lum = (r + g + g + b) >> 2;
        if (lum >= 245) {
            return (a << 24); // Pure black (#000000) with original alpha
        } else if (lum >= 220) {
            return (a << 24) | 0x00121212; // Elevated surface (#121212)
        } else if (lum >= 195) {
            return (a << 24) | 0x001E1E1E; // Secondary surface / pill (#1E1E1E)
        }
        return color;
    }

    public static int mapTextColor(int color) {
        int a = (color >>> 24) & 0xFF;
        if (a == 0) return color;

        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        if (Math.abs(r - g) > 18 || Math.abs(g - b) > 18 || Math.abs(r - b) > 18) {
            return color;
        }

        int lum = (r + g + g + b) >> 2;
        if (lum <= 45) {
            return (a << 24) | 0x00FFFFFF; // Primary dark text -> White
        } else if (lum <= 95) {
            return (a << 24) | 0x00CCCCCC; // Secondary dark gray text -> Light gray
        }
        return color;
    }

    public static void themeSingleView(View view) {
        if (view == null) return;

        Drawable bg = view.getBackground();
        if (bg instanceof ColorDrawable) {
            ColorDrawable cd = (ColorDrawable) bg;
            int orig = cd.getColor();
            int mapped = mapBackgroundColor(orig);
            if (orig != mapped) {
                cd.mutate();
                cd.setColor(mapped);
            }
        } else if (bg instanceof GradientDrawable) {
            GradientDrawable gd = (GradientDrawable) bg;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ColorStateList csl = gd.getColor();
                if (csl != null) {
                    int orig = csl.getDefaultColor();
                    int mapped = mapBackgroundColor(orig);
                    if (orig != mapped) {
                        gd.mutate();
                        gd.setColor(mapped);
                    }
                }
            }
        }

        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            int orig = tv.getCurrentTextColor();
            int mapped = mapTextColor(orig);
            if (orig != mapped) {
                tv.setTextColor(mapped);
            }
            int hintOrig = tv.getCurrentHintTextColor();
            int hintMapped = mapTextColor(hintOrig);
            if (hintOrig != hintMapped) {
                tv.setHintTextColor(hintMapped);
            }
        } else if (view instanceof ImageView) {
            ImageView iv = (ImageView) view;
            ColorStateList tintList = iv.getImageTintList();
            if (tintList != null) {
                int orig = tintList.getDefaultColor();
                int mapped = mapTextColor(orig);
                if (orig != mapped) {
                    iv.setImageTintList(ColorStateList.valueOf(mapped));
                }
            }
        }
    }

    public static void themeHierarchy(View root) {
        if (root == null) return;
        themeSingleView(root);
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0, count = vg.getChildCount(); i < count; i++) {
                themeHierarchy(vg.getChildAt(i));
            }
        }
    }
}