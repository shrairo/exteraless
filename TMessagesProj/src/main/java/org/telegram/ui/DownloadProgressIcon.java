package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;
import java.util.HashMap;

import xyz.nextalone.nagram.NaConfig;

public class DownloadProgressIcon extends View implements NotificationCenter.NotificationCenterDelegate {

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int currentAccount;
    ArrayList<ProgressObserver> currentListeners = new ArrayList<>();
    float progress;
    float currentProgress;
    float progressDt;

    ImageReceiver downloadImageReceiver = new ImageReceiver(this);
    ImageReceiver downloadCompleteImageReceiver = new ImageReceiver(this);
    RLottieDrawable downloadDrawable;
    RLottieDrawable downloadCompleteDrawable;
    boolean showCompletedIcon;
    boolean hasUnviewedDownloads;
    int currentColor;
    private boolean wasDrawn;

    public DownloadProgressIcon(int currentAccount, Context context) {
        super(context);
        this.currentAccount = currentAccount;
        downloadImageReceiver.ignoreNotifications = true;
        downloadCompleteImageReceiver.ignoreNotifications = true;

        downloadDrawable = new RLottieDrawable(R.raw.download_progress, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
        downloadCompleteDrawable = new RLottieDrawable(R.raw.download_finish, AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);

        downloadImageReceiver.setImageBitmap(downloadDrawable);
        downloadCompleteImageReceiver.setImageBitmap(downloadCompleteDrawable);

        downloadImageReceiver.setAutoRepeat(1);
        downloadDrawable.setAutoRepeat(1);

        // setImageBitmap() parks these drawables in ImageReceiver.staticThumbDrawable, and
        // ImageReceiver.getLottieAnimation() only inspects currentImageDrawable /
        // currentMediaDrawable. So addParentView() is never called and the drawables'
        // invalidateInternal() has no target: the frame callbacks keep firing but nothing
        // is redrawn. updateCurrentFrame() only advances while drawing, and its swap
        // permit is consumed per frame, so the animation freezes on its first frame
        // (which is empty for both download_progress and download_finish). Registering
        // this view as master parent restores that notification path.
        downloadDrawable.setMasterParent(this);
        downloadCompleteDrawable.setMasterParent(this);
        // setImageBitmap() above starts both drawables internally, and they loop
        // forever (autoRepeat == 1), so park them right away. onDraw() restarts the
        // arrow once the icon is actually visible; onAttachedToWindow() re-checks as
        // well, because a GONE parent does not dispatch visibility changes while the
        // view is still detached (mAttachInfo == null).
        // NB: setImageBitmap() above starts both drawables - that is intentional and
        // must be kept: parking them right here would drop the animation before its
        // first frame is decoded (renderingBitmap == null), which made the arrow/
        // finish icon render as nothing. They are parked in onAttachedToWindow()
        // instead, where isShown() reflects the real visibility, and restarted by
        // onDraw() once the icon becomes visible.
    }

    public void updateColors() {
        downloadDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
        downloadCompleteDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        int padding = AndroidUtilities.dp(15);
        downloadImageReceiver.setImageCoords(padding, padding, getMeasuredWidth() - padding * 2, getMeasuredHeight() - padding * 2);
        downloadCompleteImageReceiver.setImageCoords(padding, padding, getMeasuredWidth() - padding * 2, getMeasuredHeight() - padding * 2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getAlpha() == 0) {
            return;
        }

        if (currentColor != Theme.getColor(Theme.key_actionBarDefaultIcon)) {
            currentColor = Theme.getColor(Theme.key_actionBarDefaultIcon);
            paint.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
            paint2.setColor(Theme.getColor(Theme.key_actionBarDefaultIcon));
            downloadImageReceiver.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
            downloadCompleteImageReceiver.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
            paint2.setAlpha(100);
        }

        if (currentProgress != progress) {
            currentProgress += progressDt;
            if (progressDt > 0 && currentProgress > progress) {
                currentProgress = progress;
            } else if (progressDt < 0 && currentProgress < progress) {
                currentProgress = progress;
            } else {
                invalidate();
            }
        }

        int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(8);
        float r = AndroidUtilities.dp(1f);

        float startPadding = AndroidUtilities.dp(16);
        float width = getMeasuredWidth() - startPadding * 2;
        AndroidUtilities.rectTmp.set(startPadding, cy - r, getMeasuredWidth() - startPadding, cy + r);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint2);

        AndroidUtilities.rectTmp.set(startPadding, cy - r, startPadding + width * currentProgress, cy + r);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);

        canvas.save();
        canvas.clipRect(0, 0, getMeasuredWidth(), cy - r);
        if (progress != 1f) {
            showCompletedIcon = false;
        }
        if (showCompletedIcon) {
            if (!downloadCompleteDrawable.isRunning()) {
                downloadCompleteDrawable.start();
            }
            downloadCompleteImageReceiver.draw(canvas);
        } else {
            // A non-running RLottieDrawable renders nothing, so the arrow would
            // disappear; make sure it runs while this view is being drawn. onDraw()
            // only runs while the icon is actually visible, so this cannot restart
            // the animation while the icon is hidden.
            if (!downloadDrawable.isRunning()) {
                downloadDrawable.start();
            }
            downloadImageReceiver.draw(canvas);
        }

        if (progress == 1f && !showCompletedIcon) {
            if (downloadDrawable.getCurrentFrame() == 0 || getAlpha() != 0) {
                downloadCompleteDrawable.setCurrentFrame(0, false);
                downloadCompleteDrawable.start();
                showCompletedIcon = true;
            }
        }
        canvas.restore();
        if (getAlpha() != 0) {
            wasDrawn = true;
            updateProgress();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateDownloadingListeners();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.onDownloadingFilesChanged);
        downloadImageReceiver.onAttachedToWindow();
        downloadCompleteImageReceiver.onAttachedToWindow();
        // ImageReceiver.onAttachedToWindow() may start the drawables again; if this
        // icon is hidden (its parent downloadsItem starts as GONE), park them.
        if (!isShown()) {
            stopDrawables();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        detachCurrentListeners();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.onDownloadingFilesChanged);
        downloadImageReceiver.onDetachedFromWindow();
        downloadCompleteImageReceiver.onDetachedFromWindow();
        stopDrawables();
    }


    private void updateDownloadingListeners() {
        DownloadController downloadController = DownloadController.getInstance(currentAccount);
        HashMap<String, ProgressObserver> observerHashMap = new HashMap<>();
        for (int i = 0; i < currentListeners.size(); i++) {
            observerHashMap.put(currentListeners.get(i).fileName, currentListeners.get(i));
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(currentListeners.get(i));
        }
        currentListeners.clear();
        for (int i = 0; i < downloadController.downloadingFiles.size(); i++) {
            String filename = downloadController.downloadingFiles.get(i).getFileName();
            if (FileLoader.getInstance(currentAccount).isLoadingFile(filename)) {
                ProgressObserver progressObserver = observerHashMap.get(filename);
                if (progressObserver == null) {
                    progressObserver = new ProgressObserver(filename);
                }
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(filename, progressObserver);
                currentListeners.add(progressObserver);
            }
        }
        if (currentListeners.size() == 0 && !wasDrawn) {
            if (DownloadController.getInstance(currentAccount).hasUnviewedDownloads()) {
                progress = 1f;
                currentProgress = 1f;
                showCompletedIcon = true;
            } else {
                progress = 0;
                currentProgress = 0;
                showCompletedIcon = false;
            }
        }
    }

    public void updateProgress() {
        MessagesStorage messagesStorage = MessagesStorage.getInstance(currentAccount);
        long total = 0;
        long downloaded = 0;
        for (int i = 0; i < currentListeners.size(); i++) {
            total += currentListeners.get(i).total;
            downloaded += currentListeners.get(i).downloaded;
        }
        if (total == 0) {
            progress = 1f;
        } else {
            progress = downloaded / (float) total;
        }
        if (progress > 1f) {
            progress = 1f;
        } else if (progress < 0) {
            progress = 0;
        }

        progressDt = (progress - currentProgress) * 16f / 150f;
        // onDraw() calls this at the end of EVERY draw, so an unconditional invalidate()
        // here makes the view redraw itself forever. Only keep redrawing while the
        // animated value is still catching up; snap once it has effectively converged
        // (float underflow would otherwise keep currentProgress != progress forever).
        if (Math.abs(progress - currentProgress) > 1e-4f) {
            invalidate();
        } else if (currentProgress != progress) {
            currentProgress = progress;
        }
    }

    private void detachCurrentListeners() {
        for (int i = 0; i < currentListeners.size(); i++) {
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(currentListeners.get(i));
        }
        currentListeners.clear();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.onDownloadingFilesChanged) {
            updateDownloadingListeners();
            updateProgress();
        }
    }

    private class ProgressObserver implements DownloadController.FileDownloadProgressListener {

        long total;
        long downloaded;
        private final String fileName;

        private ProgressObserver(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {

        }

        @Override
        public void onSuccessDownload(String fileName) {

        }

        @Override
        public void onProgressDownload(String fileName, long downloadSize, long totalSize) {
            downloaded = downloadSize;
            total = totalSize;
            updateProgress();
        }

        @Override
        public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {

        }

        @Override
        public int getObserverTag() {
            return 0;
        }
    }

    /**
     * Fired when this view or any ancestor becomes (in)visible. The parent
     * (downloadsItem) is toggled via FragmentFloatingButton.setAnimatedVisibility(),
     * which flips both alpha and visibility, so this reliably fires when the icon
     * gets hidden and lets us park the animations instead of letting them keep
     * driving redraws on every frame.
     */
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != View.VISIBLE || !isShown()) {
            stopDrawables();
        }
    }

    private void stopDrawables() {
        if (downloadDrawable != null && downloadDrawable.isRunning()) {
            downloadDrawable.stop();
        }
        if (downloadCompleteDrawable != null) {
            downloadCompleteDrawable.stop();
        }
    }

    @Override
    public void setAlpha(float alpha) {
        if (alpha == 0) {
            wasDrawn = false;
            stopDrawables();
        }
        super.setAlpha(alpha);
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility != View.VISIBLE) {
            wasDrawn = false;
        }
        super.setVisibility(visibility);
    }
}
