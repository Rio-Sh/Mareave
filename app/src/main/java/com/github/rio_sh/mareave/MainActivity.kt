package com.github.rio_sh.mareave

import android.app.Dialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.github.rio_sh.mareave.common.helpers.FullScreenHelper
import com.github.rio_sh.mareave.common.samplerender.SampleRender
import com.github.rio_sh.mareave.helpers.MainView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.codelabs.hellogeospatial.helpers.ARCoreSessionLifecycleHelper
import com.google.ar.core.codelabs.hellogeospatial.helpers.GeoPermissionsHelper
import com.google.ar.core.exceptions.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    lateinit var view: MainView
    lateinit var renderer: GeoRenderer
    lateinit var showMessageDialog: Dialog
    lateinit var createMessageDialog: Dialog
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        arCoreSessionHelper.exceptionCallback =
            { exception ->
                val message =
                    when (exception) {
                        is UnavailableUserDeclinedInstallationException ->
                            "Please install Google Play Services for AR"
                        is UnavailableApkTooOldException -> "Please update ARCore"
                        is UnavailableSdkTooOldException -> "Please update this app"
                        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
                        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
                        else -> "Failed to create AR session: $exception"
                    }
                Log.e(TAG, "ARCore threw an exception", exception)
                view.snackbarHelper.showError(this, message)
            }

        // Set up Session
        arCoreSessionHelper.beforeSessionResume = ::configureSession
        lifecycle.addObserver(arCoreSessionHelper)

        // Set up UI
        renderer = GeoRenderer(this)
        lifecycle.addObserver(renderer)

        // Set up UI
        view = MainView(this)
        lifecycle.addObserver(view)
        setContentView(view.root)

        // Set up create message dialog
        createMessageDialog = Dialog(this)
        createMessageDialog.setContentView(R.layout.dialog_create_message)
        createMessageDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val dialogBtnWrite = createMessageDialog.findViewById<FloatingActionButton>(R.id.writeButton)
        dialogBtnWrite.setOnClickListener {
            val markerMessage =
                createMessageDialog.findViewById<EditText>(R.id.editMessage).text.toString()
            view.latLng?.let { renderer.createAnchor(it, markerMessage) }
            createMessageDialog.dismiss()
        }

        SampleRender(view.surfaceView, renderer, assets)
    }

    fun showMessageDialog(messageText: String) {
        showMessageDialog = Dialog(this)
        showMessageDialog.setContentView(R.layout.dialog_show_message)
        showMessageDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        showMessageDialog.findViewById<TextView>(R.id.MessageText).text = messageText
        val dialogBtnClose = showMessageDialog.findViewById<FloatingActionButton>(R.id.closeButton)
        dialogBtnClose.setOnClickListener {
            showMessageDialog.dismiss()
        }
        val dialogBtnFavorite = showMessageDialog.findViewById<FloatingActionButton>(R.id.favoriteButton)
        dialogBtnFavorite.setOnClickListener {
            // TODO implement Favorite button click listener
        }
        showMessageDialog.show()
    }

    //Configure ARCore session. While The GeospatialModel is Enabled, the application can obtain
    // Geospatial information.
    fun configureSession(session: Session) {
        session.configure(
            session.config.apply {
                geospatialMode = Config.GeospatialMode.ENABLED
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!GeoPermissionsHelper.hasGeoPermissions(this)) {
            Toast.makeText(this,
                "Camera and location permissions are needed to run this application",
                Toast.LENGTH_LONG)
                .show()
            if(!GeoPermissionsHelper.shouldShowRequestPermissionRationale(this)) {
                GeoPermissionsHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

}