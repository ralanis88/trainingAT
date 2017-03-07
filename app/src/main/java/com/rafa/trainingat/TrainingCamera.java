package com.rafa.trainingat;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import java.util.Collections;

/**
 * Created by rafa on 3/4/17.
 *
 * Raspberry PI Camera 1.3 dimensions: 2592×1944
 */

public class TrainingCamera
{
    // Camera image parameters
    private static final int IMG_WIDTH = 1920;
    private static final int IMG_HEIGHT = 1080;
    private static final int MAX_IMAGES = 10;

    private static final String TAG = "TrainingCamera";


    // Image Result
    private ImageReader imgReader;
    // Active camera device connection
    private CameraDevice camDevice;
    // Active camera capture session
    private CameraCaptureSession capSession;


    private TrainingCamera()
    {
    }

    private static class InstanceHolder
    {
        private static TrainingCamera myCamera = new TrainingCamera();
    }


    public static TrainingCamera getInstance()
    {
        return InstanceHolder.myCamera;
    }

    public void initializeCamera(Context context, Handler bckgrdHandler,ImageReader.OnImageAvailableListener imgListener)
    {
        // Discover the camera instance
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        String [] camIds = {};

        try
        {
            camIds = manager.getCameraIdList();
        }
        catch (CameraAccessException e)
        {
            Log.d(TAG, "Cam Access exception getting IDs", e);
        }

        if (camIds.length < 1)
        {
            Log.d(TAG, "No cameras found");
            return;
        }

        String id = camIds[0];


        //Initializer Image Processor
        imgReader = ImageReader.newInstance(IMG_WIDTH, IMG_HEIGHT, ImageFormat.JPEG, MAX_IMAGES);
        imgReader.setOnImageAvailableListener(imgListener, bckgrdHandler);

        //Open the camera source
        try
        {
            manager.openCamera(id, stateCallback, bckgrdHandler);
        }
        catch (CameraAccessException e)
        {
            Log.d(TAG, "Camera access exception", e);
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(CameraDevice camera)
        {
            camDevice = camera;
        }

        @Override
        public void onClosed(CameraDevice camera)
        {
            super.onClosed(camera);
        }

        @Override
        public void onDisconnected(CameraDevice camera)
        {

        }

        @Override
        public void onError(CameraDevice camera, int error)
        {

        }


    };

    // Close camera resources
    public void shutdown()
    {
        if (camDevice != null)
        {
            camDevice.close();
        }
    }


    public void takePicture()
    {
        if (camDevice == null)
        {
            Log.w(TAG, "Cannot take picture, camera not initialized");
            return;
        }

        // make a capture session for taking still pictures
        try
        {
            camDevice.createCaptureSession(Collections.singletonList(imgReader.getSurface()), sessionCallback, null);
        }
        catch (CameraAccessException e)
        {
            Log.d(TAG, "access exception while taking picture", e);
        }
    }

    private final CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback()
    {
        @Override
        public void onConfigured(CameraCaptureSession ccs)
        {
            //When session is ready we start capture
            capSession = ccs;
            triggerImageCapture();
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession ccs)
        {
            Log.w(TAG, "Failed to configure camera");
        }
    };


    private void triggerImageCapture()
    {
        try
        {
            final CaptureRequest.Builder captureBuilder = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureBuilder.addTarget(imgReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            capSession.capture(captureBuilder.build(), captureCallback, null);
        }
        catch (CameraAccessException e)
        {
            Log.d(TAG, "camera capture exception", e);
        }
    }

    // Callback handling capture progress events
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback()
    {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result)
        {
            if (session != null)
            {
                session.close();
                capSession = null;
                Log.d(TAG, "CaptureSession closed");
            }
        }

    };


}
