package com.crust87.videocropviewsample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

const val REQUEST_CODE_LOAD_VIDEO = 1000

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        videoCropView.setOnPreparedListener { videoCropView.start() }

        button43.setOnClickListener {
            videoCropView.setRatio(4f, 3f)
        }

        button11.setOnClickListener {
            videoCropView.setRatio(1f, 1f)
        }

        button34.setOnClickListener {
            videoCropView.setRatio(3f, 4f)
        }

        buttonOriginal.setOnClickListener {
            videoCropView.setOriginalRatio()
        }

        buttonLoad.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "video/*"
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            startActivityForResult(intent, REQUEST_CODE_LOAD_VIDEO)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_LOAD_VIDEO && resultCode == Activity.RESULT_OK) {
            data?.data?.let {
                videoCropView.setVideoURI(it)
                videoCropView.seekTo(1)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}