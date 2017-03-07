package com.rafa.trainingat;

import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.api.BooleanResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "HomeActivity";
    private static final String GPIO_BTN_NAME = "BCM21";
    private static final String GPIO_LED_NAME = "BCM6";
//    private Gpio mButtonGpio;
    private Button mButton;
    private Gpio mLedGpio;
    private Handler mHandler = new Handler();
    private Handler bckgrndHandler;
    private HandlerThread bckgrndThread;
    private FirebaseDatabase mDatabase;
    private FirebaseStorage storage;
    private TrainingCamera myCamera;



    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

//        PeripheralManagerService pms = new PeripheralManagerService();
//        Log.d(TAG, "Available GPIO: " + pms.getGpioList());

        //Create the GPIO Service Connections
//        PeripheralManagerService service = new PeripheralManagerService();
        PeripheralManagerService ledService = new PeripheralManagerService();

        startBackgroundThread();

        myCamera = TrainingCamera.getInstance();
        myCamera.initializeCamera(this, bckgrndHandler, onImageAvailableListener);

//        try
//        {
//            //Create GPIO Connection
//            mButtonGpio = service.openGpio(GPIO_BTN_NAME);
//
//            //Configure GPIO button as an input
//            mButtonGpio.setDirection(Gpio.DIRECTION_IN);
//
//            //Configure the Edge Trigger
//            mButtonGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
//
//            //Register an event callback
//            mButtonGpio.registerGpioCallback(mCallback);
//        }
//        catch (IOException io)
//        {
//            Log.e(TAG, "Error in PeripheralIO API ", io);
//        }



        try
        {
            mLedGpio = ledService.openGpio(GPIO_LED_NAME);

            //Configure as an output
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            //Repeat using a handler
            mHandler.post(mBlinkRunnable);
        }
        catch (IOException io)
        {
            Log.e(TAG, "Error in PeripheralIO LED API", io);
        }

        mDatabase = FirebaseDatabase.getInstance();
        storage = FirebaseStorage.getInstance();
        initializeMainActivityButton();
        listenToDB();

    }

    //Start background thread
    private void startBackgroundThread()
    {
        bckgrndThread = new HandlerThread("InputThread");
        bckgrndThread.start();
        bckgrndHandler = new Handler(bckgrndThread.getLooper());
    }



    //Initialize the Button
    private void initializeMainActivityButton()
    {
        try
        {
            mButton = new Button(GPIO_BTN_NAME, Button.LogicState.PRESSED_WHEN_LOW);
            mButton.setOnButtonEventListener(mButtonCallback);
        }
        catch (IOException io)
        {
            Log.e(TAG, "Error Initializing Button: ", io);
        }
    }

    //Event Listener for button callbacks
    private Button.OnButtonEventListener mButtonCallback = new Button.OnButtonEventListener()
    {
        @Override
        public void onButtonEvent(Button button, boolean pressed)
        {
            if(pressed)
            {
                Log.i(TAG, "Button pressed");
                mHandler.post(mBlinkRunnable);
                myCamera.takePicture();
            }
        }
    };

    // Callback to receive captured camera image data
    private ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener()
    {
        @Override
        public void onImageAvailable(ImageReader reader)
        {
            //Get Raw image bytes
            Image img = reader.acquireLatestImage();
            ByteBuffer imgBuffer = img.getPlanes()[0].getBuffer();
            final byte [] imgBytes = new byte[imgBuffer.remaining()];
            imgBuffer.get(imgBytes);
            img.close();

            onPictureTaken(imgBytes);
        }
    };

    private void onPictureTaken(byte[] imgBytes)
    {
        if (imgBytes != null)
        {
            Log.d(TAG, "Picture taken");

//            try
//            {
//                // Write contents to DB
//                final DatabaseReference log = mDatabase.getReference("camera").push();
//                log.child("timestamp").setValue(ServerValue.TIMESTAMP);
//
//                // Save image data Base64 encoded
//                String encoded = Base64.encodeToString(imgBytes, Base64.NO_WRAP | Base64.URL_SAFE);
//                log.child("image").setValue(encoded);
//            }
//            catch (Exception e)
//            {
//                Log.d(TAG, "Error in uploading picture to firebase");
//            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddhhss");
            final String date = sdf.format(new Date());
            StorageReference storageRef = storage.getReference();
            StorageReference imgRef = storageRef.child("images/"+date.toString()+".jpg");
            UploadTask uploadTask = imgRef.putBytes(imgBytes);



            uploadTask.addOnFailureListener(new OnFailureListener()
            {
                @Override
                public void onFailure(@NonNull Exception e)
                {
                    Log.d(TAG, "Couldn't upload image to storage", e);

                }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>()
            {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot)
                {
                    Uri downloadURL = taskSnapshot.getDownloadUrl();
                    Log.d(TAG, "Upload successful in following url: " + downloadURL);
                    //upload to database
                    try
                    {
                        DatabaseReference log = mDatabase.getReference("camera").push();
                        log.child("timestamp").setValue(date.toString());
                        log.child("image").setValue(downloadURL.toString());

                    }
                    catch (Exception e)
                    {
                        Log.d(TAG, "Error in saving to DB after upload ", e);
                    }
                }
            });





        }
    }


    //Register an event callback when button pressed
//    private GpioCallback mCallback = new GpioCallback()
//    {
//        @Override
//        public boolean onGpioEdge(Gpio gpio)
//        {
//            Log.i(TAG, "GPIO changed, button pressed");
//            mHandler.post(mBlinkRunnable);
//            //Return true to keep the callback active
//            return true;
//        }
//    };

    private Runnable mBlinkRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            //Exit if the GPIO is already closed
            if (mLedGpio == null)
            {
                return;
            }

            try
            {
                //Toggle de Led state
                mLedGpio.setValue(!mLedGpio.getValue());
                writeToDB();
            }
            catch (IOException io)
            {
                Log.e(TAG, "Error LED Peripheral IO ", io);
            }
        }
    };

    private ValueEventListener event = new ValueEventListener()
    {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot)
        {
            String status = dataSnapshot.getValue().toString();
            Log.i(TAG, "The status of the Led in firebase is: " + status);

            try
            {
                if (status.equals("on"))
                {
                    mLedGpio.setValue(true);
                }
                else
                {
                    mLedGpio.setValue(false);
                }
            }
            catch (IOException io)
            {
                Log.e(TAG, "Error on turning on Led through Firebase: ", io);
            }

        }

        @Override
        public void onCancelled(DatabaseError databaseError)
        {
            Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
        }
    };

    //Listen to changes in the Database
    private void listenToDB()
    {
        final DatabaseReference listen;
        listen = mDatabase.getReference("myLed");
        listen.child("status").addValueEventListener(event);
        Log.i(TAG, "Listening to database");
    }

    //Write to DB
    private void writeToDB()
    {
        final DatabaseReference log;
        try
        {
            log = mDatabase.getReference("foquito").push();
            log.child("TimeStamp").setValue(ServerValue.TIMESTAMP);
            log.child("on").setValue(Boolean.toString(mLedGpio.getValue()));

            Log.i(TAG, "Uploaded to Database with values -> Timestamp: " + ServerValue.TIMESTAMP + " and status: " + Boolean.toString(mLedGpio.getValue()));
        }
        catch (IOException io)
        {
            Log.e(TAG, "Error in Upload to Firebase: ", io);
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        //Close de resource
//        if (mButtonGpio != null)
//        {
//            mButtonGpio.unregisterGpioCallback(mCallback);
//            try
//            {
//                mButtonGpio.close();
//            }
//            catch (IOException io)
//            {
//                Log.e(TAG, "Error in PeripheralIO API ", io);
//            }
//        }

        // Step 4. Remove handler events on close.
        mHandler.removeCallbacks(mBlinkRunnable);

        // Step 5. Close the resource.
        if (mLedGpio != null) {
            try {
                mLedGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }

        if (mButton != null)
        {
            try
            {
                mButton.close();
            }
            catch (IOException io)
            {
                Log.e(TAG, "Can't destroy Button: ", io);
            }
        }

        if (myCamera != null)
        {
            myCamera.shutdown();
        }

    }
}
