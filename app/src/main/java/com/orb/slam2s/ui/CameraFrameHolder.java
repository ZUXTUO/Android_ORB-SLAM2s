package com.orb.slam2s.ui;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.orb.slam2s.slamar.OpenCVBridge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 相机帧桥：把 OpenCV 相机回调里的 native RGBA Mat 转成低分辨率 Bitmap 快照，
 * 供液态玻璃按钮/对话框绘制真实"背后画面"（低分辨率放大 = 天然柔和模糊）。
 *
 * - updateFrame() 在相机/GL 线程调用（每帧），native resize 到小尺寸（成本 ~0.2ms）
 * - 快照为 volatile 引用，UI 线程只读
 * - 监听回调通过主线程 Handler 派发，避免 UI 线程安全冲突
 */
public final class CameraFrameHolder {

    public interface Listener {
        /** 主线程回调：新帧可用（frame 为共享快照，只读勿改） */
        void onNewFrame(Bitmap frame);
    }

    /** 快照宽度（1280x720 的 2/5：对话框 1200px 放大 2.3 倍仍细腻；按钮 1.3 倍） */
    public static final int SNAP_WIDTH = 512;
    public static final int SNAP_HEIGHT = 288;

    private static final List<Listener> sListeners = new CopyOnWriteArrayList<>();
    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static volatile Bitmap sFrame;
    private static Bitmap sBuffer; // 相机线程复用缓冲

    private CameraFrameHolder() {}

    /** 相机/GL 线程调用：更新快照并通知 UI（快照始终更新，不依赖监听器） */
    public static void updateFrame(long matAddr) {
        if (matAddr == 0) return;
        try {
            if (sBuffer == null) {
                sBuffer = Bitmap.createBitmap(SNAP_WIDTH, SNAP_HEIGHT, Bitmap.Config.ARGB_8888);
            }
            OpenCVBridge.nativeMatToBitmapScaled(matAddr, sBuffer);
            final Bitmap snap = sBuffer;
            sFrame = snap;
            if (!sListeners.isEmpty()) {
                sMain.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : sListeners) {
                            l.onNewFrame(snap);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
            // 相机未就绪/尺寸异常时静默跳过
        }
    }

    /** 最近帧快照（可能为 null，UI 线程只读） */
    public static Bitmap getFrame() {
        return sFrame;
    }

    public static void addListener(Listener l) {
        if (l != null && !sListeners.contains(l)) {
            sListeners.add(l);
        }
    }

    public static void removeListener(Listener l) {
        sListeners.remove(l);
    }
}
