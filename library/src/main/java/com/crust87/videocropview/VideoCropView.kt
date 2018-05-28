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

package com.crust87.videocropview

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.MediaController.MediaPlayerControl
import android.widget.VideoView
import java.io.IOException

private const val LOG_TAG = "VideoCropView"
private const val STATE_ERROR = -1
private const val STATE_IDLE = 0
private const val STATE_PREPARING = 1
private const val STATE_PREPARED = 2
private const val STATE_PLAYING = 3
private const val STATE_PAUSED = 4
private const val STATE_PLAYBACK_COMPLETED = 5

class VideoCropView : TextureView, MediaPlayerControl {

    // MediaPlayer Components
    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null

    // CropView Components
    private var cropViewMatrix: Matrix? = null

    // MediaPlayer Attributes
    protected var uri: Uri? = null
    private var currentBufferPercentage = 0
    private var seekWhenPrepared = 0
    var videoWidth = 0
        private set
    var videoHeight = 0
        private set

    // CropView Attributes
    var ratioWidth = 0f
        private set
    var ratioHeight = 0f
        private set
    private var positionX = 0f
    private var positionY = 0f
    private var boundX = 0f
    private var boundY = 0f
    var rotate = 0
        private set
    private var viewScaleX = 0f
    private var viewScaleY = 0f
    var scale = 0f
        private set
    val realPositionX: Float
        get() = positionX * -scale

    val realPositionY: Float
        get() = positionX * -scale

    // Working Variables
    private var currentState = STATE_IDLE
    private var targetState = STATE_IDLE

    // Touch Event
    // past position x, y and move point
    internal var pastX: Float = 0.toFloat()
    internal var pastY: Float = 0.toFloat()
    internal var touchDistance: Float = 0.toFloat()

    /*
    [Internal Listener START]
     */
    private val internalSizeChangedListener = OnVideoSizeChangedListener { mp, _, _ ->
        videoWidth = mp.videoWidth
        videoHeight = mp.videoHeight

        if (videoWidth != 0 && videoHeight != 0) {
            requestLayout()
            initVideo()
        }
    }

    private val internalPreparedListener = OnPreparedListener { mp ->
        currentState = STATE_PREPARED

        onPreparedListener?.onPrepared(mp)

        videoWidth = mp.videoWidth
        videoHeight = mp.videoHeight

        val seekToPosition = seekWhenPrepared // seekWhenPrepared may be
        // changed after seekTo()
        if (seekToPosition != 0) {
            seekTo(seekToPosition)
        }

        if (videoWidth != 0 && videoHeight != 0) {
            initVideo()

            if (targetState == STATE_PLAYING) {
                start()
            }
        } else {
            // We don't know the video size yet, but should start anyway.
            // The video size might be reported to us later.
            if (targetState == STATE_PLAYING) {
                start()
            }
        }
    }

    private val internalCompletionListener = OnCompletionListener {
        currentState = STATE_PLAYBACK_COMPLETED
        targetState = STATE_PLAYBACK_COMPLETED

        onCompletionListener?.onCompletion(mediaPlayer)
    }

    private val internalInfoListener = OnInfoListener { mp, what, extra ->
        onInfoListener?.onInfo(mp, what, extra) ?: true
    }

    private val internalErrorListener = OnErrorListener { _, what, extra ->
        Log.d(LOG_TAG, "Error: $what, $extra")
        currentState = STATE_ERROR
        targetState = STATE_ERROR

        onErrorListener?.onError(mediaPlayer, what, extra) ?: true
    }

    private val internalBufferingUpdateListener = OnBufferingUpdateListener { _, percent -> currentBufferPercentage = percent }

    private var internalSurfaceTextureListener: TextureView.SurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            surface = Surface(surfaceTexture)
            openVideo()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            val isValidState = targetState == STATE_PLAYING
            val hasValidSize = videoWidth == width && videoHeight == height
            if (mediaPlayer != null && isValidState && hasValidSize) {
                start()
            }
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            mediaPlayer?.run {
                reset()
                release()
            }

            mediaPlayer = null

            surface?.run {
                release()
            }

            surface = null

            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
    }

    /*
    [Internal Listener END]
     */

    private val isInPlaybackState: Boolean
        get() = (mediaPlayer != null && currentState != STATE_ERROR
                && currentState != STATE_IDLE && currentState != STATE_PREPARING)

    // Constructors
    constructor(context: Context) : super(context) {
        initAttributes()
        initVideoView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initAttributes(context, attrs, 0)
        initVideoView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initAttributes(context, attrs, defStyleAttr)
        initVideoView()
    }

    private fun initAttributes() {
        ratioWidth = 3f
        ratioHeight = 4f
    }

    private fun initAttributes(context: Context, attrs: AttributeSet, defStyleAttr: Int) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.VideoCropView, defStyleAttr, 0)

        ratioWidth = typedArray.getInteger(R.styleable.VideoCropView_ratio_width, 3).toFloat()
        ratioHeight = typedArray.getInteger(R.styleable.VideoCropView_ratio_height, 4).toFloat()

        typedArray.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = View.MeasureSpec.getSize(widthMeasureSpec)
        var height = View.MeasureSpec.getSize(heightMeasureSpec)
        val resizedWidth = (height / ratioHeight * ratioWidth).toInt()
        val resizedHeight = (width / ratioWidth * ratioHeight).toInt()

        Log.d("WTF", "$width $height $resizedWidth $resizedHeight")

        if (resizedWidth > width) {
            height = resizedHeight
        } else if (resizedHeight > height) {
            width = resizedWidth
        }

        setMeasuredDimension(width, height)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentState == STATE_ERROR || currentState == STATE_IDLE || currentState == STATE_PREPARING) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pastX = event.x
                pastY = event.y
                touchDistance = 0f
                val dx = event.x - pastX
                val dy = event.y - pastY
                updateViewPosition(dx, dy)
                pastX = event.x
                pastY = event.y
                touchDistance += Math.abs(dx) + Math.abs(dy)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - pastX
                val dy = event.y - pastY
                updateViewPosition(dx, dy)
                pastX = event.x
                pastY = event.y
                touchDistance += Math.abs(dx) + Math.abs(dy)
            }
            MotionEvent.ACTION_UP -> {
                if (touchDistance < 25) {
                    if (isPlaying) {
                        pause()
                    } else {
                        start()
                    }
                }

                touchDistance = 0f
            }
        }

        return true
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.className = VideoView::class.java.name
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = VideoView::class.java.name
    }

    fun resolveAdjustedSize(desiredSize: Int, measureSpec: Int): Int {
        Log.d(LOG_TAG, "Resolve called.")
        var result = desiredSize
        val specMode = View.MeasureSpec.getMode(measureSpec)
        val specSize = View.MeasureSpec.getSize(measureSpec)

        when (specMode) {
            View.MeasureSpec.UNSPECIFIED ->
                /*
			 * Parent says we can be as big as we want. Just don't be larger
			 * than max size imposed on ourselves.
			 */
                result = desiredSize

            View.MeasureSpec.AT_MOST ->
                /*
			 * Parent says we can be as big as we want, up to specSize. Don't be
			 * larger than specSize, and don't be larger than the max size
			 * imposed on ourselves.
			 */
                result = Math.min(desiredSize, specSize)

            View.MeasureSpec.EXACTLY ->
                // No choice. Do what we are told.
                result = specSize
        }
        return result
    }

    fun initVideoView() {
        videoHeight = 0
        videoWidth = 0
        isFocusable = false
        surfaceTextureListener = internalSurfaceTextureListener
        currentState = STATE_IDLE
        targetState = STATE_IDLE
    }

    fun setVideoPath(path: String?) {
        if (path != null) {
            setVideoURI(Uri.parse(path))
        }
    }

    fun setVideoURI(pVideoURI: Uri) {
        uri = pVideoURI
        seekWhenPrepared = 0

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, pVideoURI)

        // create thumbnail bitmap
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            try {
                rotate = Integer.parseInt(rotation)
            } catch (e: NumberFormatException) {
                rotate = 0
            }

        }

        retriever.release()

        openVideo()
        requestLayout()
        invalidate()
    }

    fun stopPlayback() {
        mediaPlayer?.run {
            stop()
            release()
        }

        mediaPlayer = null
        currentState = STATE_IDLE
        targetState = STATE_IDLE
    }

    fun openVideo() {
        if (uri == null || surface == null) {
            // not ready for playback just yet, will try again later
            return
        }
        // Tell the music playback service to pause
        // TODO: these constants need to be published somewhere in the
        // framework.
        val intent = Intent("com.android.music.musicservicecommand")
        intent.putExtra("command", "pause")
        context.sendBroadcast(intent)

        // we shouldn't clear the target state, because somebody might have
        // called start() previously
        release(false)
        try {
            mediaPlayer = MediaPlayer().apply {
                setOnPreparedListener(internalPreparedListener)
                setOnVideoSizeChangedListener(internalSizeChangedListener)
                setOnCompletionListener(internalCompletionListener)
                setOnErrorListener(internalErrorListener)
                setOnInfoListener(internalInfoListener)
                setOnBufferingUpdateListener(internalBufferingUpdateListener)
                setDataSource(context, uri)
                setSurface(surface)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                setScreenOnWhilePlaying(true)
                prepareAsync()
                isLooping = true
            }

            currentBufferPercentage = 0

            currentState = STATE_PREPARING
        } catch (e: IllegalStateException) {
            currentState = STATE_ERROR
            targetState = STATE_ERROR
            e.printStackTrace()
        } catch (e: IOException) {
            currentState = STATE_ERROR
            targetState = STATE_ERROR
            e.printStackTrace()
        }

    }

    private fun release(cleartargetstate: Boolean) {
        mediaPlayer?.run {
            reset()
            release()
        }

        mediaPlayer = null
        currentState = STATE_IDLE
        if (cleartargetstate) {
            targetState = STATE_IDLE
        }
    }

    override fun start() {
        if (isInPlaybackState) {
            mediaPlayer?.start()
            currentState = STATE_PLAYING
        }
        targetState = STATE_PLAYING
    }

    override fun pause() {
        if (isInPlaybackState) {
            mediaPlayer?.run {
                if (isPlaying) {
                    pause()
                    currentState = STATE_PAUSED
                }
            }
        }

        targetState = STATE_PAUSED
    }

    override fun getDuration(): Int {
        return if (isInPlaybackState) {
            mediaPlayer?.duration ?: -1
        } else {
            -1
        }
    }

    override fun getCurrentPosition(): Int {
        return if (isInPlaybackState) {
            return mediaPlayer?.currentPosition ?: 0
        } else {
            0
        }
    }

    override fun seekTo(msec: Int) {
        when (isInPlaybackState) {
            true -> {
                mediaPlayer?.seekTo(msec)
                seekWhenPrepared = 0
            }
            false -> {
                seekWhenPrepared = msec
            }
        }
    }

    override fun isPlaying(): Boolean {
        return isInPlaybackState && mediaPlayer?.isPlaying ?: false
    }

    override fun getBufferPercentage(): Int {
        return if (mediaPlayer != null) {
            currentBufferPercentage
        } else {
            0
        }
    }

    override fun canPause(): Boolean {
        return false
    }

    override fun canSeekBackward(): Boolean {
        return false
    }

    override fun canSeekForward(): Boolean {
        return false
    }

    override fun getAudioSessionId(): Int {
        return -1
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)

        if (visibility == View.INVISIBLE || visibility == View.GONE) {
            if (isPlaying) {
                stopPlayback()
            }
        }
    }

    private fun initVideo() {
        try {
            val viewWidth = width
            val viewHeight = height

            viewScaleX = 1.0f
            viewScaleX = 1.0f
            positionX = 0f
            positionY = 0f
            boundX = 0f
            boundY = 0f
            cropViewMatrix = Matrix()

            viewScaleX = videoWidth.toFloat() / viewWidth
            viewScaleY = videoHeight.toFloat() / viewHeight

            boundX = viewWidth - videoWidth / viewScaleY
            boundY = viewHeight - videoHeight / viewScaleX

            if (viewScaleX < viewScaleY) {
                scale = viewScaleX
                viewScaleY *= (1.0f / viewScaleX)
                viewScaleX = 1.0f
                boundX = 0f
            } else {
                scale = viewScaleY
                viewScaleX *= (1.0f / viewScaleY)
                viewScaleY = 1.0f
                boundY = 0f
            }

            cropViewMatrix?.setScale(viewScaleX, viewScaleY)
            setTransform(cropViewMatrix)
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }

    }

    fun updateViewPosition(x: Float, y: Float) {
        var x = x
        var y = y

        val nextX = positionX + x
        val nextY = positionY + y

        if (viewScaleX == 1.0f) {
            x = 0f
        } else {
            when {
                nextX > 0 -> {
                    x = -positionX
                    positionX += x
                }
                nextX < boundX -> {
                    x = boundX - positionX
                    positionX += x
                }
                else -> {
                    positionX = nextX
                }
            }
        }

        if (viewScaleY == 1.0f) {
            y = 0f
        } else {
            when {
                nextY > 0 -> {
                    y = -positionY
                    positionY += y
                }
                nextY < boundY -> {
                    y = boundY - positionY
                    positionY += + y
                }
                else -> {
                    positionY = nextY
                }
            }
        }

        onTranslatePositionListener?.onTranslatePosition(positionX, positionY, positionX * -scale, positionY * -scale)

        cropViewMatrix?.postTranslate(x, y)
        setTransform(cropViewMatrix)
        invalidate()
    }

    fun setOriginalRatio() {
        if (videoWidth != 0 && videoHeight != 0) {
            val gcd = gcd(videoWidth, videoHeight)
            setRatio((videoWidth / gcd).toFloat(), (videoHeight / gcd).toFloat())
        }
    }

    fun setRatio(ratioWidth: Float, ratioHeight: Float) {
        this.ratioWidth = ratioWidth
        this.ratioHeight = ratioHeight

        val seek = currentPosition

        requestLayout()
        invalidate()
        openVideo()

        seekTo(seek)
    }

    interface OnTranslatePositionListener {
        fun onTranslatePosition(x: Float, y: Float, rx: Float, ry: Float)
    }

    // Listener
    private var onInfoListener: OnInfoListener? = null
    private var onCompletionListener: OnCompletionListener? = null
    private var onErrorListener: OnErrorListener? = null
    private var onPreparedListener: OnPreparedListener? = null
    private var onTranslatePositionListener: OnTranslatePositionListener? = null

    fun setOnInfoListener(action: (mp: MediaPlayer, what: Int, extra: Int) -> Boolean) {
        onInfoListener = OnInfoListener { mp, what, extra ->
            action(mp, what, extra)
        }
    }

    fun setOnCompletionListener(action: (mp: MediaPlayer?) -> Unit) {
        onCompletionListener = OnCompletionListener { mp ->
            action(mp)
        }
    }

    fun setOnErrorListener(action: (mp: MediaPlayer, what: Int, extra: Int) -> Boolean) {
        onErrorListener = OnErrorListener { mp, what, extra ->
            action(mp, what, extra)
        }
    }

    fun setOnPreparedListener(action: (mp: MediaPlayer?) -> Unit) {
        onPreparedListener = OnPreparedListener { mp ->
            action(mp)
        }
    }

    fun setOnTranslatePositionListener(action: (x: Float, y: Float, rx: Float, ry: Float) -> Unit) {
        onTranslatePositionListener = object : OnTranslatePositionListener {
            override fun onTranslatePosition(x: Float, y: Float, rx: Float, ry: Float) {
                action(x, y, rx, ry)
            }
        }
    }
}
