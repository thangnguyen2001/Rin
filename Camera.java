/*

    <uses-feature
        android:name="android.hardware.camera"
        android:required="true" />

    <uses-feature
        android:name="android.hardware.camera.front"
        android:required="false" />

    <uses-permission android:name="android.permission.CAMERA" />


*/





package com.momchil_atanasov.photodepth;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Surface;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 0;

    private final CameraDeviceStateHandler cameraDeviceStateHandler = new CameraDeviceStateHandler();
    private CameraManager manager;
    private ImageView display;
    private CameraDevice camera;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            finish();
        }

        display = (ImageView)findViewById(R.id.display);

    }

    @Override
    protected void onResume() {
        super.onResume();

        int cameraPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            initializeCamera();
        } else {
            requestCameraPermission();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.close();
            camera = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        if (requestCode == REQUEST_CAMERA_PERMISSION) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                System.out.println("Permission granted!");
//                initializeCamera();
//            } else {
//                System.err.println("Permission denied!");
//            }
//        }
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
    }

    private void initializeCamera() {
        System.out.println("Initializing camera...");
        String cameraId = getBackFacingCameraId();
        if (cameraId == null) {
            System.err.println("Could not find back-facing camera id.");
            finish();
            return;
        }
        try {
            //noinspection ResourceType
            manager.openCamera(cameraId, cameraDeviceStateHandler, null);
        } catch (CameraAccessException e) {
            System.err.println("Could not open camera due to: " + e.getMessage());
        }
    }

    private String getBackFacingCameraId() {
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer direction = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (direction != null && direction == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
        } catch (CameraAccessException e) {
            System.err.println("Could not list camera IDs due to: " + e.getMessage());
            return null;
        }
        return null;
    }

    private class CameraDeviceStateHandler extends CameraDevice.StateCallback {

        @Override
        public void onClosed(CameraDevice camera) {
            MainActivity.this.camera = null;
        }

        @Override
        public void onOpened(final CameraDevice camera) {
            MainActivity.this.camera = camera;
            System.out.println("Camera opened");

            final ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    System.out.println("Image available!");
                    final Image image = reader.acquireLatestImage();
                    final ByteBuffer data = image.getPlanes()[0].getBuffer();
                    final byte[] content = new byte[data.remaining()];
                    data.get(content);
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(content, 0, content.length);
                    display.setImageBitmap(bitmap);
                    image.close();
                }
            }, null);
            final List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(reader.getSurface());

            try {
                camera.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        System.out.println("On session configured!");
                        try {
                            final CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
                            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_ACTION);
                            builder.addTarget(reader.getSurface());
                            session.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                    System.out.println("Capture completed!");
                                }

                                @Override
                                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                                    System.err.println("Capture failed!");
                                }
                            }, null);
                        } catch (CameraAccessException e) {
                            System.err.println("Could not create capture request due to: " + e.getMessage());
                        }



                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        System.err.println("On session configuration failed!");
                    }
                }, null);
            } catch (CameraAccessException e) {
                System.err.println("Could not create capture session due to: " + e.getMessage());
            }

        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            System.out.println("Camera disconnected");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            System.out.println("Camera error");
        }

    }
}
