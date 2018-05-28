/*
 * HttpRequestBuilder
 * https://github.com/crust87/Android-VideoCropView
 *
 * Mabi
 * crust87@gmail.com
 * last modify 2015-05-25
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crust87.videocropview;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.VideoView;

import java.io.IOException;

public class VideoCropView extends TextureView implements MediaPlayerControl {
	// Constants
	private static final String LOG_TAG = "VideoCropView";
	private static final int STATE_ERROR = -1;
	private static final int STATE_IDLE = 0;
	private static final int STATE_PREPARING = 1;
	private static final int STATE_PREPARED = 2;
	private static final int STATE_PLAYING = 3;
	private static final int STATE_PAUSED = 4;
	private static final int STATE_PLAYBACK_COMPLETED = 5;

	// MediaPlayer Components
	protected Context mContext;
	private MediaPlayer mMediaPlayer;
	private Surface mSurface;
	private OnInfoListener mOnInfoListener;
	private OnCompletionListener mOCompletionListener;
	private OnErrorListener mOnErrorListener;
	private OnPreparedListener mOnPreparedListener;
	private OnTranslatePositionListener mOnTranslatePositionListener;

	// CropView Components
	private Matrix mMatrix;

	// MediaPlayer Attributes
	protected Uri mUri;
	private int mCurrentBufferPercentage;
	private int mSeekWhenPrepared;
	protected int mVideoWidth;
	protected int mVideoHeight;

	// CropView Attributes
	private float mRatioWidth;
	private float mRatioHeight;
	private float mPositionX;
	private float mPositionY;
	private float mBoundX;
	private float mBoundY;
	private int mRotate;
	private float mScaleX;
	private float mScaleY;
	private float mScale;

	// Working Variables
	private int mCurrentState = STATE_IDLE;
	private int mTargetState = STATE_IDLE;

	// Touch Event
	// past position x, y and move point
	float mPastX;
	float mPastY;
	float mTouchDistance;

	// Constructors
	public VideoCropView(final Context context) {
		super(context);
		mContext = context;

		initAttributes();
		initVideoView();
	}

	public VideoCropView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		mContext = context;

		initAttributes(context, attrs, 0);
		initVideoView();
	}

	public VideoCropView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		mContext = context;

		initAttributes(context, attrs, defStyleAttr);
		initVideoView();
	}

	private void initAttributes() {
		mRatioWidth = 3;
		mRatioHeight = 4;
	}

	private void initAttributes(Context context, AttributeSet attrs, int defStyleAttr) {
		TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.VideoCropView, defStyleAttr, 0);

		mRatioWidth = typedArray.getInteger(R.styleable.VideoCropView_ratio_width, 3);
		mRatioHeight = typedArray.getInteger(R.styleable.VideoCropView_ratio_height, 4);
	}

	@Override
	protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
		int width = MeasureSpec.getSize(widthMeasureSpec);
		int height = MeasureSpec.getSize(heightMeasureSpec);
		int resizedWidth = (int) (height / mRatioHeight * mRatioWidth);
		int resizedHeight = (int) ((width / mRatioWidth) * mRatioHeight);

		if (resizedWidth > width) {
			height = resizedHeight;
		} else if (resizedHeight > height) {
			width = resizedWidth;
		}

		setMeasuredDimension(width, height);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(mCurrentState == STATE_ERROR || mCurrentState == STATE_IDLE || mCurrentState == STATE_PREPARING) {
			return false;
		}

		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				mPastX = event.getX();
				mPastY = event.getY();
				mTouchDistance = 0;
			case MotionEvent.ACTION_MOVE:
				float dx = event.getX() - mPastX;
				float dy = event.getY() - mPastY;
				updateViewPosition(dx, dy);
				mPastX = event.getX();
				mPastY = event.getY();
				mTouchDistance += (Math.abs(dx) + Math.abs(dy));
				break;
			case MotionEvent.ACTION_UP:
				if (mTouchDistance < 25) {
					if (isPlaying()) {
						pause();
					} else {
						start();
					}
				}

				mTouchDistance = 0;
				break;
		}

		return true;
	}

	@Override
	public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
		super.onInitializeAccessibilityEvent(event);
		event.setClassName(VideoView.class.getName());
	}

	@Override
	public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
		super.onInitializeAccessibilityNodeInfo(info);
		info.setClassName(VideoView.class.getName());
	}

	public int resolveAdjustedSize(int desiredSize, int measureSpec) {
		Log.d(LOG_TAG, "Resolve called.");
		int result = desiredSize;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		switch (specMode) {
		case MeasureSpec.UNSPECIFIED:
			/*
			 * Parent says we can be as big as we want. Just don't be larger
			 * than max size imposed on ourselves.
			 */
			result = desiredSize;
			break;

		case MeasureSpec.AT_MOST:
			/*
			 * Parent says we can be as big as we want, up to specSize. Don't be
			 * larger than specSize, and don't be larger than the max size
			 * imposed on ourselves.
			 */
			result = Math.min(desiredSize, specSize);
			break;

		case MeasureSpec.EXACTLY:
			// No choice. Do what we are told.
			result = specSize;
			break;
		}
		return result;
	}

	public void initVideoView() {
		mVideoHeight = 0;
		mVideoWidth = 0;
		setFocusable(false);
		setSurfaceTextureListener(mSurfaceTextureListener);
		mCurrentState = STATE_IDLE;
		mTargetState = STATE_IDLE;
	}
	
	public void setVideoPath(String path) {
		if (path != null) {
			setVideoURI(Uri.parse(path));
		}
	}

	public void setVideoURI(Uri pVideoURI) {
		mUri = pVideoURI;
		mSeekWhenPrepared = 0;

		MediaMetadataRetriever retriever = new  MediaMetadataRetriever();
		retriever.setDataSource(mContext, pVideoURI);

		// create thumbnail bitmap
		if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
			String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);

			try {
				mRotate = Integer.parseInt(rotation);
			} catch(NumberFormatException e) {
				mRotate = 0;
			}
		}

		retriever.release();

		openVideo();
		requestLayout();
		invalidate();
	}

	public void stopPlayback() {
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mCurrentState = STATE_IDLE;
			mTargetState = STATE_IDLE;
		}
	}

	public void openVideo() {
		if ((mUri == null) || (mSurface == null)) {
			// not ready for playback just yet, will try again later
			return;
		}
		// Tell the music playback service to pause
		// TODO: these constants need to be published somewhere in the
		// framework.
		Intent intent = new Intent("com.android.music.musicservicecommand");
		intent.putExtra("command", "pause");
		mContext.sendBroadcast(intent);

		// we shouldn't clear the target state, because somebody might have
		// called start() previously
		release(false);
		try {
			mMediaPlayer = new MediaPlayer();
			// TODO: create SubtitleController in MediaPlayer, but we need
			// a context for the subtitle renderers
			
			mMediaPlayer.setOnPreparedListener(mPreparedListener);
			mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
			mMediaPlayer.setOnCompletionListener(mCompletionListener);
			mMediaPlayer.setOnErrorListener(mErrorListener);
			mMediaPlayer.setOnInfoListener(mInfoListener);
			mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
			mCurrentBufferPercentage = 0;
			mMediaPlayer.setDataSource(mContext, mUri);
			mMediaPlayer.setSurface(mSurface);
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

			mMediaPlayer.setScreenOnWhilePlaying(true);
			mMediaPlayer.prepareAsync();
			mMediaPlayer.setLooping(true);
			mCurrentState = STATE_PREPARING;
		} catch (IllegalStateException e) {
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;
			e.printStackTrace();
		} catch (IOException e) {
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;
			e.printStackTrace();
		}
	}

	private OnVideoSizeChangedListener mSizeChangedListener = new OnVideoSizeChangedListener() {
		@Override
		public void onVideoSizeChanged(final MediaPlayer mp, final int width,
				final int height) {
			mVideoWidth = mp.getVideoWidth();
			mVideoHeight = mp.getVideoHeight();

			if (mVideoWidth != 0 && mVideoHeight != 0) {
				requestLayout();
				initVideo();
			}
		}
	};

	private OnPreparedListener mPreparedListener = new OnPreparedListener() {
		@Override
		public void onPrepared(final MediaPlayer mp) {
			mCurrentState = STATE_PREPARED;

			if (mOnPreparedListener != null) {
				mOnPreparedListener.onPrepared(mp);
			}
			
			mVideoWidth = mp.getVideoWidth();
			mVideoHeight = mp.getVideoHeight();

			int seekToPosition = mSeekWhenPrepared; // mSeekWhenPrepared may be
													// changed after seekTo()
			if (seekToPosition != 0) {
				seekTo(seekToPosition);
			}

			if ((mVideoWidth != 0) && (mVideoHeight != 0)) {
				initVideo();

				if (mTargetState == STATE_PLAYING) {
					start();
				}
			} else {
				// We don't know the video size yet, but should start anyway.
				// The video size might be reported to us later.
				if (mTargetState == STATE_PLAYING) {
					start();
				}
			}
		}
	};

	private OnCompletionListener mCompletionListener = new OnCompletionListener() {
		@Override
		public void onCompletion(final MediaPlayer mp) {
			mCurrentState = STATE_PLAYBACK_COMPLETED;
			mTargetState = STATE_PLAYBACK_COMPLETED;

			if (mOCompletionListener != null) {
				mOCompletionListener.onCompletion(mMediaPlayer);
			}
		}
	};

	private OnInfoListener mInfoListener = new OnInfoListener() {
		public boolean onInfo(MediaPlayer mp, int arg1, int arg2) {
			if (mOnInfoListener != null) {
				mOnInfoListener.onInfo(mp, arg1, arg2);
			}
			return true;
		}
	};

	private OnErrorListener mErrorListener = new OnErrorListener() {
		@Override
		public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
			Log.d(LOG_TAG, "Error: " + framework_err + "," + impl_err);
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;

			/* If an error handler has been supplied, use it and finish. */
			if (mOnErrorListener != null) {
				if (mOnErrorListener.onError(mMediaPlayer, framework_err,
						impl_err)) {
					return true;
				}
			}
			return true;
		}
	};

	private OnBufferingUpdateListener mBufferingUpdateListener = new OnBufferingUpdateListener() {
		@Override
		public void onBufferingUpdate(final MediaPlayer mp, final int percent) {
			mCurrentBufferPercentage = percent;
		}
	};

	public void setOnPreparedListener(OnPreparedListener listener) {
		mOnPreparedListener = listener;
	}

	public void setOnCompletionListener(OnCompletionListener listener) {
		mOCompletionListener = listener;
	}

	public void setOnErrorListener(OnErrorListener listener) {
		mOnErrorListener = listener;
	}

	public void setOnInfoListener(OnInfoListener listener) {
		mOnInfoListener = listener;
	}

	private void release(boolean cleartargetstate) {
		if (mMediaPlayer != null) {
			mMediaPlayer.reset();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mCurrentState = STATE_IDLE;
			if (cleartargetstate) {
				mTargetState = STATE_IDLE;
			}
		}
	}
	
	@Override
	public void start() {
		if (isInPlaybackState()) {
			mMediaPlayer.start();
			mCurrentState = STATE_PLAYING;
		}
		mTargetState = STATE_PLAYING;
	}
	
	@Override
	public void pause() {
		if (isInPlaybackState()) {
			if (mMediaPlayer.isPlaying()) {
				mMediaPlayer.pause();
				mCurrentState = STATE_PAUSED;
			}
		}
		
		mTargetState = STATE_PAUSED;
	}

	@Override
	public int getDuration() {
		if (isInPlaybackState()) {
			return mMediaPlayer.getDuration();
		}

		return -1;
	}

	@Override
	public int getCurrentPosition() {
		if (isInPlaybackState()) {
			return mMediaPlayer.getCurrentPosition();
		}
		return 0;
	}

	@Override
	public void seekTo(int msec) {
		if (isInPlaybackState()) {
			mMediaPlayer.seekTo(msec);
			mSeekWhenPrepared = 0;
		} else {
			mSeekWhenPrepared = msec;
		}
	}

	@Override
	public boolean isPlaying() {
		return isInPlaybackState() && mMediaPlayer.isPlaying();
	}

	@Override
	public int getBufferPercentage() {
		if (mMediaPlayer != null) {
			return mCurrentBufferPercentage;
		}
		return 0;
	}

	private boolean isInPlaybackState() {
		return (mMediaPlayer != null && mCurrentState != STATE_ERROR
				&& mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
	}

	@Override
	public boolean canPause() {
		return false;
	}

	@Override
	public boolean canSeekBackward() {
		return false;
	}

	@Override
	public boolean canSeekForward() {
		return false;
	}

	@Override
	public int getAudioSessionId() {
		return -1;
	}

	SurfaceTextureListener mSurfaceTextureListener = new SurfaceTextureListener() {
		@Override
		public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
			mSurface = new Surface(surface);
			openVideo();
		}

		@Override
		public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
			boolean isValidState = (mTargetState == STATE_PLAYING);
			boolean hasValidSize = (mVideoWidth == width && mVideoHeight == height);
			if (mMediaPlayer != null && isValidState && hasValidSize) {
				start();
			}
		}

		@Override
		public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
			if (mMediaPlayer != null) {
				mMediaPlayer.reset();
				mMediaPlayer.release();
				mMediaPlayer = null;
			}

			if (mSurface != null) {
				mSurface.release();
				mSurface = null;
			}

			return true;
		}

		@Override
		public void onSurfaceTextureUpdated(final SurfaceTexture surface) {

		}
	};

	@Override
	protected void onVisibilityChanged(View changedView, int visibility) {
		super.onVisibilityChanged(changedView, visibility);

		if (visibility == View.INVISIBLE || visibility == View.GONE) {
			if (isPlaying()) {
				stopPlayback();
			}
		}
	}

	public float getScale() {
		return mScale;
	}

	private void initVideo() {
		try {
			int viewWidth = getWidth();
			int viewHeight = getHeight();

			mScaleX = 1.0f;
			mScaleY = 1.0f;
			mPositionX = 0;
			mPositionY = 0;
			mBoundX = 0;
			mBoundY = 0;
			mMatrix = new Matrix();

			mScaleX = (float) mVideoWidth / viewWidth;
			mScaleY = (float) mVideoHeight / viewHeight;

			mBoundX = viewWidth - mVideoWidth / mScaleY;
			mBoundY = viewHeight - mVideoHeight / mScaleX;
				
			if(mScaleX < mScaleY) {
				mScale = mScaleX;
				mScaleY = mScaleY * (1.0f / mScaleX);
				mScaleX = 1.0f;
				mBoundX = 0;
			} else {
				mScale = mScaleY;
				mScaleX = mScaleX * (1.0f / mScaleY);
				mScaleY = 1.0f;
				mBoundY = 0;
			}

			mMatrix.setScale(mScaleX, mScaleY);
			setTransform(mMatrix);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
	}

	public void updateViewPosition(float x, float y) {
		
		float nextX = mPositionX + x;
		float nextY = mPositionY + y;
		
		if(mScaleX == 1.0f) {
			x = 0;
		} else {
			if(nextX > 0) {
				x = -mPositionX;
				mPositionX = mPositionX + x;
			} else if(nextX < mBoundX) {
				x = mBoundX - mPositionX;
				mPositionX = mPositionX + x;
			} else {
				mPositionX = nextX;
			}
		}
		
		if(mScaleY == 1.0f) {
			y = 0;
		} else {
			if(nextY > 0) {
				y = -mPositionY;
				mPositionY = mPositionY + y;
			} else if(nextY < mBoundY) {
				y = mBoundY - mPositionY;
				mPositionY = mPositionY + y;
			} else {
				mPositionY = nextY;
			}
		}
		
		if(mOnTranslatePositionListener != null) {
			mOnTranslatePositionListener.onTranslatePosition(mPositionX, mPositionY, mPositionX * -mScale, mPositionY * -mScale);
		}

		mMatrix.postTranslate(x, y);
		setTransform(mMatrix);
		invalidate();
	}

	public void setOriginalRatio() {
		if(mVideoWidth != 0 && mVideoHeight != 0) {
			int gcd = gcd(mVideoWidth, mVideoHeight);
			setRatio(mVideoWidth / gcd, mVideoHeight / gcd);
		}
	}

	public int gcd(int n, int m) {
		while (m != 0) {
			int t = n % m;
			n = m;
			m = t;
		}

		return Math.abs(n);
	}

	public void setRatio(float ratioWidth, float ratioHeight) {
		mRatioWidth = ratioWidth;
		mRatioHeight = ratioHeight;

		int seek = getCurrentPosition();

		requestLayout();
		invalidate();
		openVideo();

		seekTo(seek);
	}


	public float getRatioWidth() {
		return mRatioWidth;
	}

	public float getRatioHeight() {
		return mRatioHeight;
	}

	public float getRealPositionX() {
		return mPositionX * -mScale;
	}

	public float getRealPositionY() {
		return mPositionY * -mScale;
	}

	public int getVideoWidth() {
		return mVideoWidth;
	}

	public int getVideoHeight() {
		return mVideoHeight;
	}

	public int getRotate() {
		return mRotate;
	}
	
	public void setOnTranslatePositionListener(OnTranslatePositionListener pOnTranslatePositionListener) {
		mOnTranslatePositionListener = pOnTranslatePositionListener;
	}
	
	public interface OnTranslatePositionListener {
		public abstract void onTranslatePosition(float x, float y, float rx, float ry);
	}

}
