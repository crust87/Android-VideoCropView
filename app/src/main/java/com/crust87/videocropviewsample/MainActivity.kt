package com.crust87.videocropviewsample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        videoCropView.setOnPreparedListener { videoCropView.start() }

        buttonLoad.setOnClickListener {
            val lIntent = Intent(Intent.ACTION_PICK)
            lIntent.type = "video/*"
            lIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivityForResult(lIntent, 1000)
        }

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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1000 && resultCode == Activity.RESULT_OK) {
            data?.data?.let {
                videoCropView.setVideoURI(it)
                videoCropView.seekTo(1)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}