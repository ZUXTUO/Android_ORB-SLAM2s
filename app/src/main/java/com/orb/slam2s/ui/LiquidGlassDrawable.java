package com.orb.slam2s.ui;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.View;

/**
 * 液态玻璃背景（参考 Prismal 渲染元素：高度场穹顶 + Fresnel 边缘折射 + 流体光泽）
 *
 * 按钮版（neutral/red/green/blue/gray）：接近纯透明 —— 背景完全透出，
 * 玻璃感来自：穹顶中心微光（水滴剖面光学）、边缘折射光（顶亮→底暗，Prismal rim）、
 * 流动光泽、按压泛白。不发蓝、不遮背景。
 *
 * 容器版（dark）：对话框等需要可读性的场景，保留深色半透明底。
 *
 * 纯 Canvas 绘制，minSdk 23 兼容。流动光泽由 setVisible() 启停。
 */
public class LiquidGlassDrawable extends Drawable {

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint domePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();

    private final int tintTop;      // 玻璃色相（亮端）
    private final int tintBottom;   // 玻璃色相（暗端）
    private final boolean opaque;   // true=容器深色版，false=按钮透明版
    private boolean frameEnabled;   // 按钮版默认用相机帧做背景
    private boolean panelMode;      // true=毛玻璃面板（tint 更实保可读）
    private float cornerRadiusPx;
    private float flowPhase = 0f;
    private ValueAnimator flowAnimator;

    private final Paint framePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect frameSrc = new Rect();
    private boolean frameListenerRegistered = false;
    private final CameraFrameHolder.Listener frameListener = new CameraFrameHolder.Listener() {
        @Override
        public void onNewFrame(android.graphics.Bitmap frame) {
            invalidateSelf();
        }
    };

    private boolean pressed = false;

    /** 透明玻璃（按钮默认，中性无色，不偏蓝） */
    public static LiquidGlassDrawable neutral() {
        return new LiquidGlassDrawable(0xFFFFFFFF, 0xFFFAFAFC, false);
    }

    /** 透明微橙红玻璃（强调按钮） */
    public static LiquidGlassDrawable red() {
        return new LiquidGlassDrawable(0xFFFFE0D6, 0xFFFFB09A, false);
    }

    /** 透明微绿玻璃（强调按钮） */
    public static LiquidGlassDrawable green() {
        return new LiquidGlassDrawable(0xFFDCF3E0, 0xFFB2DFC0, false);
    }

    /** 透明微蓝玻璃（强调按钮） */
    public static LiquidGlassDrawable blue() {
        return new LiquidGlassDrawable(0xFFDCEBFA, 0xFFB4D6F5, false);
    }

    /** 透明灰玻璃（禁用/低优先级） */
    public static LiquidGlassDrawable gray() {
        return new LiquidGlassDrawable(0xFFE8E8E8, 0xFFCFCFCF, false);
    }

    /** 中性深灰玻璃（对话框/悬浮窗容器，不偏蓝） */
    public static LiquidGlassDrawable dark() {
        return new LiquidGlassDrawable(0xFF2B2F35, 0xFF1A1D22, true);
    }

    /** 毛玻璃面板（对话框容器）：透明 + 相机帧背景模糊 + 适中 tint 保可读 */
    public static LiquidGlassDrawable panel() {
        LiquidGlassDrawable d = new LiquidGlassDrawable(0xFFF0F4F8, 0xFFC8D0DA, false);
        d.panelMode = true;
        return d;
    }

    public LiquidGlassDrawable(int tintTop, int tintBottom, boolean opaque) {
        this.tintTop = tintTop;
        this.tintBottom = tintBottom;
        this.opaque = opaque;
        this.frameEnabled = !opaque; // 按钮透明版默认透出相机帧
        basePaint.setStyle(Paint.Style.FILL);
        rimPaint.setStyle(Paint.Style.STROKE);
        domePaint.setStyle(Paint.Style.FILL);
        flowPaint.setStyle(Paint.Style.FILL);
        pressPaint.setStyle(Paint.Style.FILL);
        pressPaint.setColor(Color.argb(0x1F, 255, 255, 255));
    }

    @Override
    public void draw(Canvas canvas) {
        // 首次绘制即注册帧监听（Drawable 默认 isVisible()=true，setVisible(true) 不会触发）
        ensureFrameListener();
        final Rect b = getBounds();
        if (b.isEmpty()) return;
        rectF.set(b);
        Path clip = new Path();
        clip.addRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);

        final float w = rectF.width();
        final float h = rectF.height();

        // ---------- 0. 相机帧背景（按钮真实背后画面） ----------
        if (frameEnabled) {
            android.graphics.Bitmap frame = CameraFrameHolder.getFrame();
            if (frame != null) {
                drawFrameBackground(canvas, frame);
            } else {
                // 无帧（相机未启动等）：深色玻璃底，避免露出黑色布局背景
                drawFallbackBase(canvas);
            }
        }

        // ---------- 1. 底色 ----------
        if (opaque) {
            // 容器版：深色半透明（顶部较深保可读）
            int top = pressed ? darken(tintTop, 0.8f) : tintTop;
            int bottom = pressed ? darken(tintBottom, 0.8f) : tintBottom;
            basePaint.setShader(new LinearGradient(rectF.left, rectF.top, rectF.left, rectF.bottom,
                    Color.argb(0xB8, Color.red(top), Color.green(top), Color.blue(top)),
                    Color.argb(0x8C, Color.red(bottom), Color.green(bottom), Color.blue(bottom)),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(rectF, basePaint);
        } else {
            // 透明版：微色相（面板 35%/20%，按钮 12%/6%），背景完全透出
            int aTop = panelMode ? 0x59 : 0x1F;
            int aBottom = panelMode ? 0x33 : 0x0F;
            basePaint.setShader(new LinearGradient(rectF.left, rectF.top, rectF.left, rectF.bottom,
                    Color.argb(aTop, Color.red(tintTop), Color.green(tintTop), Color.blue(tintTop)),
                    Color.argb(aBottom, Color.red(tintBottom), Color.green(tintBottom), Color.blue(tintBottom)),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(rectF, basePaint);
        }

        // ---------- 2. 穹顶微光（水滴剖面高度场的光学：中心亮、边缘暗） ----------
        if (!opaque) {
            float cx = rectF.centerX();
            float cy = rectF.centerY() * 0.92f;
            float radius = Math.max(w, h) * 0.7f;
            domePaint.setShader(new RadialGradient(cx, cy, radius,
                    Color.argb(pressed ? 0x24 : 0x16, 255, 255, 255),
                    Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(rectF, domePaint);
            domePaint.setShader(null);
        }

        // ---------- 3. 边缘折射光（Prismal rim / Fresnel：顶亮 → 底暗） ----------
        float stroke = Math.max(2f, b.width() * 0.035f);
        rimPaint.setStrokeWidth(stroke);
        rimPaint.setShader(new LinearGradient(rectF.left, rectF.top, rectF.left, rectF.bottom,
                Color.argb(pressed ? 0x8C : 0x66, 255, 255, 255),   // 顶部边缘亮
                Color.argb(0x21, 255, 255, 255),                    // 底部边缘暗
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rectF, cornerRadiusPx, cornerRadiusPx, rimPaint);
        rimPaint.setShader(null);

        // ---------- 4. 流动光泽（液体表面光泽缓慢流动） ----------
        float flowCenterY = rectF.top + h * (0.3f + 0.4f * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * flowPhase)));
        float bandHalf = h * 0.22f;
        int flowAlpha = pressed ? 0x20 : 0x14;
        flowPaint.setShader(new LinearGradient(
                rectF.left, flowCenterY - bandHalf,
                rectF.left, flowCenterY + bandHalf,
                new int[]{ Color.TRANSPARENT, Color.argb(flowAlpha, 255, 255, 255), Color.TRANSPARENT },
                new float[]{ 0f, 0.5f, 1f },
                Shader.TileMode.CLAMP));
        canvas.drawRect(rectF, flowPaint);
        flowPaint.setShader(null);

        // ---------- 5. 按压泛白（液态受压：整体微白 + 边缘光增强） ----------
        if (pressed) {
            canvas.drawRect(rectF, pressPaint);
        }

        canvas.restoreToCount(save);
    }

    /** 绘制相机帧中按钮背后的区域（近似 FILL 映射 + BlurMaskFilter 柔和玻璃模糊） */
    private void drawFrameBackground(Canvas canvas, android.graphics.Bitmap frame) {
        View v = (View) getCallback();
        if (v == null || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        DisplayMetrics dm = v.getResources().getDisplayMetrics();
        int sw = dm.widthPixels;
        int sh = dm.heightPixels;
        if (sw <= 0 || sh <= 0) return;
        float fw = frame.getWidth();
        float fh = frame.getHeight();
        int srcLeft = clamp((int) (loc[0] * fw / sw), 0, (int) fw - 1);
        int srcTop = clamp((int) (loc[1] * fh / sh), 0, (int) fh - 1);
        int srcRight = clamp((int) ((loc[0] + v.getWidth()) * fw / sw), 0, (int) fw);
        int srcBottom = clamp((int) ((loc[1] + v.getHeight()) * fh / sh), 0, (int) fh);
        if (srcRight <= srcLeft || srcBottom <= srcTop) return;
        frameSrc.set(srcLeft, srcTop, srcRight, srcBottom);
        // 玻璃模糊：固定 8-16px（毛玻璃标准范围），避免大尺寸元素过度模糊成色块
        float blurRadius = Math.max(8f, Math.min(16f, rectF.width() * 0.04f));
        framePaint.setMaskFilter(new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL));
        canvas.drawBitmap(frame, frameSrc, rectF, framePaint);
        framePaint.setMaskFilter(null);
    }

    /** 无相机帧时的深色玻璃底（iOS 控制中心按钮观感） */
    private void drawFallbackBase(Canvas canvas) {
        basePaint.setShader(new LinearGradient(rectF.left, rectF.top, rectF.left, rectF.bottom,
                Color.argb(0xC8, 0x2B, 0x2F, 0x35),
                Color.argb(0x96, 0x1A, 0x1D, 0x22), Shader.TileMode.CLAMP));
        canvas.drawRect(rectF, basePaint);
        basePaint.setShader(null);
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static int darken(int color, float factor) {
        return Color.rgb((int) (Color.red(color) * factor),
                (int) (Color.green(color) * factor),
                (int) (Color.blue(color) * factor));
    }

    @Override
    public void setAlpha(int alpha) {
        basePaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        basePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean oldPressed = pressed;
        pressed = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                pressed = true;
                break;
            }
        }
        if (pressed != oldPressed) {
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (frameEnabled) {
            if (visible) {
                registerFrameListener();
            } else {
                unregisterFrameListener();
            }
        }
        if (visible) {
            startFlowAnimation();
        } else {
            stopFlowAnimation();
        }
        return changed;
    }

    private void registerFrameListener() {
        if (!frameListenerRegistered) {
            frameListenerRegistered = true;
            CameraFrameHolder.addListener(frameListener);
            if (CameraFrameHolder.getFrame() != null) {
                invalidateSelf();
            }
        }
    }

    /** 首次 draw 时注册（不依赖 setVisible 时序） */
    private void ensureFrameListener() {
        if (frameEnabled && !frameListenerRegistered) {
            registerFrameListener();
        }
    }

    private void unregisterFrameListener() {
        if (frameListenerRegistered) {
            frameListenerRegistered = false;
            CameraFrameHolder.removeListener(frameListener);
        }
    }

    private void startFlowAnimation() {
        if (flowAnimator == null) {
            flowAnimator = ValueAnimator.ofFloat(0f, 1f);
            flowAnimator.setDuration(4200);
            flowAnimator.setRepeatCount(ValueAnimator.INFINITE);
            flowAnimator.setRepeatMode(ValueAnimator.RESTART);
            flowAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    flowPhase = animation.getAnimatedFraction();
                    invalidateSelf();
                }
            });
        }
        if (!flowAnimator.isRunning()) {
            flowAnimator.start();
        }
    }

    private void stopFlowAnimation() {
        if (flowAnimator != null && flowAnimator.isRunning()) {
            flowAnimator.cancel();
        }
    }

    public void setCornerRadiusPx(float px) {
        cornerRadiusPx = px;
        invalidateSelf();
    }
}
