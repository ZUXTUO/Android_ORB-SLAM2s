package com.orb.slam2s.ui;

import com.orb.slam2s.R;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 液态玻璃对话框（参考 Prismal 玻璃语言：深色半透明玻璃 + 弧线高光 + Fresnel 边缘）
 *
 * - 背景：LiquidGlassDrawable 深蓝玻璃，圆角大
 * - API 31+：window.setBackgroundBlurRadius() 在系统层模糊窗口背后的整个屏幕
 *   （含 SurfaceView 相机画面）→ 真正的液态玻璃模糊；低版本降级为静态深玻璃
 * - 按钮/列表项均为液态玻璃样式，按压有受压泛光反馈
 *
 * 用法与 AlertDialog.Builder 子集一致：new GlassDialog.Builder(ctx).setTitle(..).setItems(..).show()
 */
public class GlassDialog {

    public static final int BUTTON_POSITIVE = -1;
    public static final int BUTTON_NEGATIVE = -2;
    public static final int BUTTON_NEUTRAL = -3;

    public static class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View customView;
        private CharSequence[] items;
        private boolean[] checkedItems;
        private DialogInterface.OnClickListener itemsListener;
        private DialogInterface.OnMultiChoiceClickListener multiChoiceListener;
        private CharSequence positiveText, negativeText, neutralText;
        private DialogInterface.OnClickListener positiveListener, negativeListener, neutralListener;
        private boolean cancelable = true;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setTitle(CharSequence t) { this.title = t; return this; }
        public Builder setTitle(int resId) { this.title = context.getText(resId); return this; }
        public Builder setMessage(CharSequence m) { this.message = m; return this; }
        public Builder setMessage(int resId) { this.message = context.getText(resId); return this; }
        public Builder setView(View v) { this.customView = v; return this; }
        public Builder setCancelable(boolean c) { this.cancelable = c; return this; }

        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
            this.items = items; this.itemsListener = l; return this;
        }

        public Builder setItems(int resId, DialogInterface.OnClickListener l) {
            return setItems(context.getResources().getTextArray(resId), l);
        }

        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checked,
                DialogInterface.OnMultiChoiceClickListener l) {
            this.items = items; this.checkedItems = checked; this.multiChoiceListener = l;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
            this.positiveText = text; this.positiveListener = l; return this;
        }
        public Builder setPositiveButton(int resId, DialogInterface.OnClickListener l) {
            return setPositiveButton(context.getText(resId), l);
        }
        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) {
            this.negativeText = text; this.negativeListener = l; return this;
        }
        public Builder setNegativeButton(int resId, DialogInterface.OnClickListener l) {
            return setNegativeButton(context.getText(resId), l);
        }
        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) {
            this.neutralText = text; this.neutralListener = l; return this;
        }
        public Builder setNeutralButton(int resId, DialogInterface.OnClickListener l) {
            return setNeutralButton(context.getText(resId), l);
        }

        public Dialog show() {
            Dialog d = build();
            d.show();
            return d;
        }

        /** 兼容 AlertDialog.Builder.create() */
        public Dialog create() {
            return build();
        }

        public Dialog build() {            final Dialog dialog = new Dialog(context, R.style.LiquidGlassDialogTheme);
            dialog.setCancelable(cancelable);
            Window window = dialog.getWindow();
            if (window != null) {
                window.requestFeature(Window.FEATURE_NO_TITLE);
            }

            final float density = context.getResources().getDisplayMetrics().density;
            int dp8 = (int) (8 * density);
            int dp16 = (int) (16 * density);
            int dp18 = (int) (18 * density);

            // ---------- 根容器：深蓝液态玻璃背景 ----------
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp18, dp18, dp18, dp16);
            LiquidGlassDrawable glass = LiquidGlassDrawable.panel(); // 透明毛玻璃（相机帧背景模糊）
            glass.setCornerRadiusPx(24 * density);
            root.setBackground(glass);

            // ---------- 标题 ----------
            if (title != null) {
                TextView tvTitle = new TextView(context);
                tvTitle.setText(title);
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTextSize(17);
                tvTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                tvTitle.setShadowLayer(1, 0, 1, 0x66000000);
                tvTitle.setPadding(0, 0, 0, dp8);
                root.addView(tvTitle, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            // ---------- 消息 ----------
            if (message != null) {
                TextView tvMsg = new TextView(context);
                tvMsg.setText(message);
                tvMsg.setTextColor(0xFFE0E6EE);
                tvMsg.setTextSize(14);
                tvMsg.setLineSpacing(0, 1.15f);
                tvMsg.setShadowLayer(1, 0, 1, 0x66000000);
                tvMsg.setPadding(0, dp8, 0, dp8);
                root.addView(tvMsg, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            // ---------- 内容区 ----------
            FrameLayout content = new FrameLayout(context);
            if (customView != null) {
                content.addView(customView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else if (items != null) {
                buildList(content, density, dialog);
            }
            root.addView(content, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // ---------- 按钮行 ----------
            if (positiveText != null || negativeText != null || neutralText != null) {
                LinearLayout btnRow = new LinearLayout(context);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.END);
                btnRow.setPadding(0, dp16, 0, 0);

                if (neutralText != null) {
                    btnRow.addView(makeButton(neutralText, BUTTON_NEUTRAL, dp8,
                            new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    if (neutralListener != null) {
                                        neutralListener.onClick(dialog, BUTTON_NEUTRAL);
                                    } else {
                                        dialog.dismiss();
                                    }
                                }
                            }));
                }
                if (negativeText != null) {
                    btnRow.addView(makeButton(negativeText, BUTTON_NEGATIVE, dp8,
                            new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    if (negativeListener != null) {
                                        negativeListener.onClick(dialog, BUTTON_NEGATIVE);
                                    } else {
                                        dialog.dismiss();
                                    }
                                }
                            }));
                }
                if (positiveText != null) {
                    btnRow.addView(makeButton(positiveText, BUTTON_POSITIVE, dp8,
                            new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    if (positiveListener != null) {
                                        positiveListener.onClick(dialog, BUTTON_POSITIVE);
                                    } else {
                                        dialog.dismiss();
                                    }
                                }
                            }));
                }
                root.addView(btnRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            dialog.setContentView(root);
            if (window != null) {
                // 窗口背景透明 + API 31+ 真模糊背后（含相机 SurfaceView）
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        window.setBackgroundBlurRadius(28);
                    } catch (Throwable ignored) {
                        // 部分厂商实现异常时静默降级为静态玻璃
                    }
                }
                // 对话框宽度 = 屏幕宽 70%，上限 460dp（列表可读且不过宽）
                android.view.WindowManager.LayoutParams lp = window.getAttributes();
                lp.width = Math.min(
                        (int) (context.getResources().getDisplayMetrics().widthPixels * 0.7f),
                        (int) (460 * context.getResources().getDisplayMetrics().density));
                window.setAttributes(lp);
            }
            return dialog;
        }

        /** 液态玻璃列表（单选/多选） */
        private void buildList(FrameLayout container, float density, final Dialog dialog) {
            float dp6 = 6 * density;
            float dp10 = 10 * density;
            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < items.length; i++) {
                final int index = i;
                final TextView row = new TextView(context);
                row.setText(items[i]);
                row.setTextColor(Color.WHITE);
                row.setTextSize(14);
                row.setShadowLayer(1, 0, 1, 0x66000000);
                row.setPadding((int) dp10, (int) dp10, (int) dp10, (int) dp10);
                row.setGravity(Gravity.CENTER_VERTICAL);

                LiquidGlassDrawable rowGlass = new LiquidGlassDrawable(0x33FFFFFF, 0x1AFFFFFF, false);
                rowGlass.setCornerRadiusPx(12 * density);
                row.setBackground(rowGlass);

                if (checkedItems != null) {
                    // 多选模式：点击切换勾选
                    final boolean[] checked = checkedItems;
                    updateMultiRow(row, checked[i], density);
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            checked[index] = !checked[index];
                            updateMultiRow(row, checked[index], density);
                            if (multiChoiceListener != null) {
                                multiChoiceListener.onClick(dialog, index, checked[index]);
                            }
                        }
                    });
                } else {
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            if (itemsListener != null) {
                                itemsListener.onClick(dialog, index);
                            }
                            dialog.dismiss();
                        }
                    });
                }
                list.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                if (i < items.length - 1) {
                    View divider = new View(context);
                    divider.setBackgroundColor(0x1FFFFFFF);
                    list.addView(divider, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) dp6));
                }
            }
            // 列表用 ScrollView 包裹：地图多时可上下滑动，高度上限为屏幕 50%
            final android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.addView(list, new android.widget.ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int maxListH = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.5f);
            int estListH = (int) (items.length * 52 * density);
            container.addView(scroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.min(Math.max(estListH, 1), maxListH)));
        }

        private void updateMultiRow(TextView row, boolean checked, float density) {
            String text = row.getText().toString().replace("✓  ", "");
            if (checked) {
                LiquidGlassDrawable g = LiquidGlassDrawable.blue();
                g.setCornerRadiusPx(12 * density);
                row.setBackground(g);
                row.setText("✓  " + text);
            } else {
                LiquidGlassDrawable g = new LiquidGlassDrawable(0x33FFFFFF, 0x1AFFFFFF, false);
                g.setCornerRadiusPx(12 * density);
                row.setBackground(g);
                row.setText(text);
            }
        }

        /** 液态玻璃按钮 */
        private TextView makeButton(CharSequence text, int which, int marginLeft,
                View.OnClickListener listener) {
            TextView tv = new TextView(context);
            tv.setText(text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(14);
            tv.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            tv.setShadowLayer(1, 0, 1, 0x66000000);
            tv.setGravity(Gravity.CENTER);
            int padH = (int) (16 * context.getResources().getDisplayMetrics().density);
            int padV = (int) (8 * context.getResources().getDisplayMetrics().density);
            tv.setPadding(padH, padV, padH, padV);

            LiquidGlassDrawable glass = (which == BUTTON_POSITIVE)
                    ? LiquidGlassDrawable.blue() : LiquidGlassDrawable.neutral();
            glass.setCornerRadiusPx(14 * context.getResources().getDisplayMetrics().density);
            tv.setBackground(glass);
            tv.setOnClickListener(listener);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = marginLeft;
            tv.setLayoutParams(lp);
            return tv;
        }
    }
}
