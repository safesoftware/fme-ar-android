package com.safe.fmear;

import android.content.Intent;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TapHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.examples.java.helloar.HelloArActivity;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// =================================================================================================
// ARActivity
public class ARActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = HelloArActivity.class.getSimpleName();
    private static final float FME_TO_OPENGL_ROTATION_ANGLE = (float) 270;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private TapHelper tapHelper;

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];

    // Anchors created from taps used for object placing.
    private final ArrayList<Anchor> anchors = new ArrayList<>();

    // If this boolean is set to true, onDrawFrame will try to load the obj files from the temp
    // directory.
    private boolean datasetDrawRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up tap listener.
        tapHelper = new TapHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(tapHelper);

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        installRequested = false;

        // Initialize the temp directory by removing previous content and recreate the directory
        initDirectory(tempDirectory());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO: Selecting a fmear file from the Android file system will resume on this activity.
        // However, we can end up in this onResume function even no file is selected. We should
        // handle both cases.
        // Get the data from the intent and unzip the FME AR dataset into the temp directory.
        // If we successfully extracted the content from a .fmear file, we will set the boolean
        // datasetDrawRequested to true. Then, in onDrawFrame, we load the obj and update the
        // geometry.
        datasetDrawRequested = extractDatasetFromIntent();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();

        messageSnackbarHelper.showMessage(this, "Searching for surfaces...");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(/*context=*/ this);

            // Create the shaders and the program first. We will load the obj into this object later
            virtualObject.createProgram(this);

            virtualObjectShadow.createOnGlThread(
                    /*context=*/ this, "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            // Handle taps. Handling only one tap per frame, as taps are usually low frequency
            // compared to frame rate.

            MotionEvent tap = tapHelper.poll();
            if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(tap)) {
                    // Check if any plane was hit, and if it was hit inside the plane polygon
                    Trackable trackable = hit.getTrackable();
                    // Creates an anchor if a plane or an oriented point was hit.
                    if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
                            || (trackable instanceof Point
                            && ((Point) trackable).getOrientationMode()
                            == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                        // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                        // Cap the number of objects created. This avoids overloading both the
                        // rendering system and ARCore.
                        if (anchors.size() >= 20) {
                            anchors.get(0).detach();
                            anchors.remove(0);
                        }
                        // Adding an Anchor tells ARCore that it should track this position in
                        // space. This anchor is created on the Plane to place the 3D model
                        // in the correct position relative both to the world and to the plane.
                        anchors.add(hit.createAnchor());
                        break;
                    }
                }
            }

            // Draw background.
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Visualize tracked points.
            PointCloud pointCloud = frame.acquirePointCloud();
            pointCloudRenderer.update(pointCloud);
            pointCloudRenderer.draw(viewmtx, projmtx);

            // Application is responsible for releasing the point cloud resources after
            // using it.
            pointCloud.release();

            // Check if we detected at least one plane. If so, hide the loading message.
            if (messageSnackbarHelper.isShowing()) {
                for (Plane plane : session.getAllTrackables(Plane.class)) {
                    if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING
                            && plane.getTrackingState() == TrackingState.TRACKING) {
                        messageSnackbarHelper.hide(this);
                        break;
                    }
                }
            }

            // Visualize planes.
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

            // Visualize anchors created by touch.
            float scaleFactor = 1.0f;
            for (Anchor anchor : anchors) {
                if (anchor.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }
                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.
                anchor.getPose().toMatrix(anchorMatrix, 0);

                // Load the new obj files if any
                if (datasetDrawRequested) {
                    datasetDrawRequested = false;
                    List<File> objFiles = new FileFinder(".obj").find(tempDirectory());
                    if (!objFiles.isEmpty()) {
                        // TODO: We need to render all the obj files instead of just the first one
                        File firstObjFile = objFiles.get(0);

                        try {

                            virtualObject.loadObj(this, firstObjFile);
                            //                virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
                            //                virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

                        } catch (IOException e) {
                            Log.e(TAG, "Failed to read an asset file", e);
                        }
                    }
                }

                // Rotate model 270 degrees around the x axis, this is needed to translate
                // between FMEAR's understanding of the z-axis (pointing upwards) to opengl's where
                // the z-axis is flat while the y-axis points up
                Matrix.rotateM(anchorMatrix, 0, FME_TO_OPENGL_ROTATION_ANGLE, 1, 0, 0);
                // Update and draw the model and its shadow.
                virtualObject.updateModelMatrix(anchorMatrix, 1);
                virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba);
                virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba);
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // This function gets the data from the view intent. If the data exists, this function will
    // unzip the data, assuming it's a .fmear file, into the temp directory named "fmear" in the
    // default cache directory.
    private boolean extractDatasetFromIntent() {
        boolean result = false;
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();

                // Get the temp directory
                File tempDir = tempDirectory();
                initDirectory(tempDir);

                // Unzip the content to the temporary directory
                result = unzipContent(uri, tempDir);

                // Find all the .obj files
                List<File> objFiles = new FileFinder(".obj").find(tempDir);
                for (File file : objFiles) {
                    Log.d("FME AR", "OBJ File: " + file.toString());
                }

            }
        }

        return result;
    }

    // ---------------------------------------------------------------------------------------------
    // This function delete all files and sub-directories of the specified file.
    private void deleteFileRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteFileRecursively(child);

        fileOrDirectory.delete();
    }

    // ---------------------------------------------------------------------------------------------
    // This function returns a directory named "fmear" in the cache directory.
    private File tempDirectory() {
        // Get the cache directory
        File cacheDir = getCacheDir();

        // Find out if we already have the directory in the cache dir
        return new File(cacheDir.toString(), "fmear");
    }

    // ---------------------------------------------------------------------------------------------
    // This function deletes everything in dir including itself and then creates dir again.
    private void initDirectory(File dir) {
        try {
            // Delete the fmear directory from previous runs and
            // recreate it so that it's empty
            if (dir.exists()) {
                deleteFileRecursively(dir);
            }
            dir.mkdir();
        } catch (SecurityException e) {
            Log.e("FME AR", "Failed to create the temp directory \"" + dir.toString() + "\"");
        }

        Log.i("FME AR", "Temp directory: \"" + dir.toString() + "\"");
    }

    // ---------------------------------------------------------------------------------------------
    // This function unzip the content from the contentPath to the destinationFolder. This
    // function creates all the directories necessary for the unzipped files.
    private boolean unzipContent(Uri contentPath, File destinationFolder)
    {
        try
        {
            InputStream inputStream = getContentResolver().openInputStream(contentPath);
            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream));

            // Create a buffer to read the zip file content
            byte[] buffer = new byte[1024];
            int numBytes;

            String zipEntryName;
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null)
            {
                zipEntryName = zipEntry.getName();

                File unzippedFile = new File(destinationFolder, zipEntryName);

                // Skip the __MACOSX folders in the .fmear file in case the .fmear archive was
                // created on macOS with the __MACOSX resource fork.
                if (!zipEntryName.startsWith("__MACOSX")) {
                    if (zipEntry.isDirectory()) {
                        // If the entry is a directory, we need to make sure all the parent
                        // directories leading up to this directory exist before writing a file
                        // in the directory
                        unzippedFile.mkdirs();
                    } else {
                        // If the entry is a file, we need to make sure all the parent directories
                        // leading up to this file exist before writing the file to the path.
                        File subfolder = unzippedFile.getParentFile();
                        if (subfolder != null) {
                            subfolder.mkdirs();
                        }

                        // Now we can unzip the file
                        Log.i("FME AR", "Unzipping '" + unzippedFile.toString() + "' ...");
                        FileOutputStream fileOutputStream = new FileOutputStream(unzippedFile);
                        while ((numBytes = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, numBytes);
                        }
                        fileOutputStream.close();
                    }
                }

                zipInputStream.closeEntry();
            }

            zipInputStream.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
