/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Changes from the original file
 * - Class name
 * - onMapClick
 * - Replace AR model And Rotate it
 * - Add function(createAnchor)
 */
package com.github.rio_sh.mareave

import android.graphics.Color
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.maps.model.LatLng
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.github.rio_sh.mareave.common.helpers.DisplayRotationHelper
import com.github.rio_sh.mareave.common.helpers.TrackingStateHelper
import com.github.rio_sh.mareave.common.samplerender.Framebuffer
import com.github.rio_sh.mareave.common.samplerender.Mesh
import com.github.rio_sh.mareave.common.samplerender.SampleRender
import com.github.rio_sh.mareave.common.samplerender.Shader
import com.github.rio_sh.mareave.common.samplerender.Texture
import com.github.rio_sh.mareave.common.samplerender.arcore.BackgroundRenderer
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.io.IOException


class GeoRenderer(val activity: MainActivity) :
  SampleRender.Renderer, DefaultLifecycleObserver {
  //<editor-fold desc="ARCore initialization" defaultstate="collapsed">
  companion object {
    val TAG = "GeoRenderer"

    private val Z_NEAR = 0.1f
    private val Z_FAR = 1000f
  }

  lateinit var backgroundRenderer: BackgroundRenderer
  lateinit var virtualSceneFramebuffer: Framebuffer
  var hasSetTextureNames = false

  private var altitude = 0.0
  var qx = 0f
  var qy = 0f
  var qz = 0f
  var qw = 1f

  // Virtual object (ARCore pawn)
  lateinit var virtualObjectMesh: Mesh
  lateinit var virtualObjectShader: Shader
  lateinit var virtualObjectTexture: Texture

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  val modelMatrix = FloatArray(16)
  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  val modelViewMatrix = FloatArray(16) // view x model
  val rotationMatrix = FloatArray(16)
  val rotatedMatrix = FloatArray(16)

  val modelViewProjectionMatrix = FloatArray(16) // projection x view x model

  val session
    get() = activity.arCoreSessionHelper.session

  val displayRotationHelper = DisplayRotationHelper(activity)
  val trackingStateHelper = TrackingStateHelper(activity)

  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
    hasSetTextureNames = false
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }

  override fun onSurfaceCreated(render: SampleRender) {
    // Prepare the rendering objects.
    // This involves reading shaders and 3D model files, so may throw an IOException.
    try {
      backgroundRenderer = BackgroundRenderer(render)
      virtualSceneFramebuffer = Framebuffer(render, /*width=*/ 1, /*height=*/ 1)

      // Virtual object to render (Geospatial Marker)
      virtualObjectTexture =
        Texture.createFromAsset(
          render,
          "models/spatial_marker_baked_2.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.SRGB
        )

      virtualObjectMesh = Mesh.createFromAsset(render, "models/fukidashi.obj")
      virtualObjectShader =
        Shader.createFromAssets(
          render,
          "shaders/ar_unlit_object.vert",
          "shaders/ar_unlit_object.frag",
          /*defines=*/ null)
          .setTexture("u_Texture", virtualObjectTexture)

      backgroundRenderer.setUseDepthVisualization(render, false)
      backgroundRenderer.setUseOcclusion(render, false)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showError("Failed to read a required asset file: $e")
    }
  }

  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
    virtualSceneFramebuffer.resize(width, height)
  }
  //</editor-fold>

  override fun onDrawFrame(render: SampleRender) {
    val session = session ?: return

    //<editor-fold desc="ARCore frame boilerplate" defaultstate="collapsed">
    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
      hasSetTextureNames = true
    }

    // -- Update per-frame state

    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    displayRotationHelper.updateSessionIfNeeded(session)

    // Obtain the current frame from ARSession. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    val frame =
      try {
        session.update()
      } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available during onDrawFrame", e)
        showError("Camera not available. Try restarting the app.")
        return
      }

    val camera = frame.camera

    // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    // used to draw the background camera image.
    backgroundRenderer.updateDisplayGeometry(frame)

    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

    // -- Draw background
    if (frame.timestamp != 0L) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      backgroundRenderer.drawBackground(render)
    }

    // If not tracking, don't draw 3D objects.
    if (camera.trackingState == TrackingState.PAUSED) {
      return
    }

    // Get projection matrix.
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)

    // Get camera matrix and draw.
    camera.getViewMatrix(viewMatrix, 0)

    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
    //</editor-fold>

    val earth = session.earth
    if (earth?.trackingState == TrackingState.TRACKING) {
      val cameraGeospatialPose = earth.cameraGeospatialPose
      activity.view.mapView?.updateMapPosition(
        latitude = cameraGeospatialPose.latitude,
        longitude = cameraGeospatialPose.longitude,
        heading = cameraGeospatialPose.heading
      )
      activity.view.updateStatusText(earth, cameraGeospatialPose)
    }

    // Draw the placed anchor, if it exists.
    // earthAnchor?.let {
    //   render.renderCompassAtAnchor(it)
    // }
    for (i in earthAnchors) {
      render.renderCompassAtAnchor(i)
    }

    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
  }

  // var earthAnchor: Anchor? = null
  var earthAnchors = mutableListOf<Anchor>()

  fun onMapClick() {
    val earth = session?.earth ?: return
    if(earth.trackingState != TrackingState.TRACKING) {
      return
    }
    // earthAnchor?.detach()
    // Place the earth anchor at the same latitude as that of the camera to make it  easier to view.
    altitude = earth.cameraGeospatialPose.altitude - 1
    // The rotation quaternion of the anchor in the East-Up-South (ENS) coordinate system.
    qx = 0f
    qy = 0f
    qz = 0f
    qw = 1f
  }

  fun createAnchor(latLng: LatLng, message: String) {
    val earth = session?.earth ?: return
    if(earth.trackingState != TrackingState.TRACKING) {
      return
    }
    earthAnchors.add(earth.createAnchor(latLng.latitude, latLng.longitude, altitude, qx, qy, qz, qw))
    //activity.view.mapView?.earthMarker?.apply {
    //  position = latLng
    //  isVisible = true
    //}
    activity.view.mapView?.createMessageMarker(
      Color.BLUE,
      latLng.latitude,
      latLng.longitude,
      message)
  }

  private fun SampleRender.renderCompassAtAnchor(anchor: Anchor) {
    // Get the current pose of the Anchor in world space. The Anchor pose is updated
    // during calls to session.update() as ARCore refines its estimate of the world.
    anchor.pose.toMatrix(modelMatrix, 0)

    // Calculate model/view/projection matrices
    Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

    // Create a rotation transformation
    val time = SystemClock.uptimeMillis() % 4000L
    val angle = 0.090f * time.toInt()
    Matrix.setRotateM(rotationMatrix, 0, angle, 0f, -1.0f, 0f)
    Matrix.multiplyMM(rotatedMatrix, 0, modelViewProjectionMatrix, 0, rotationMatrix, 0)

    // Update shader properties and draw
    virtualObjectShader.setMat4("u_ModelViewProjection", rotatedMatrix)
    draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
  }

  private fun showError(errorMessage: String) =
    activity.view.snackbarHelper.showError(activity, errorMessage)
}
