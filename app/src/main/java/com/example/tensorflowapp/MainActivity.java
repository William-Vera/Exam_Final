package com.example.tensorflowapp;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.tensorflowapp.camera.CameraConnectionFragment;
import com.example.tensorflowapp.camera.ImageUtils;
import com.example.tensorflowapp.ml.ModelUnquant;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.text.Text;
import org.tensorflow.lite.DataType;

import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnSuccessListener<Text>,
        OnFailureListener, ImageReader.OnImageAvailableListener {
    public static int REQUEST_CAMERA = 111;
    Bitmap mSelectedImage;
    ImageView mImageView;
    ArrayList<String> permisosNoAprobados;
    private List<String> clases = new ArrayList<>();
    Button btnCamara;
    TextView txtResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ArrayList<String> permisos_requeridos = new ArrayList<String>();
        permisos_requeridos.add(Manifest.permission.CAMERA);
        permisos_requeridos.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        permisos_requeridos.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        cargarLabels(getApplicationContext());
        txtResults = findViewById(R.id.txtresults);
        btnCamara = findViewById(R.id.btCamera);
        permisosNoAprobados  = getPermisosNoAprobados(permisos_requeridos);
        requestPermissions(permisosNoAprobados.toArray(new String[permisosNoAprobados.size()]),
                100);
    }

    public void abrirCamera (View view){
        this.setFragment();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && null != data) {
            try {
                if (requestCode == REQUEST_CAMERA)
                    mSelectedImage = (Bitmap) data.getExtras().get("data");
                else
                    mSelectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());

                mImageView.setImageBitmap(mSelectedImage);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        for(int i=0; i<permissions.length; i++){
            if(permissions[i].equals(Manifest.permission.CAMERA)){
                btnCamara.setEnabled(grantResults[i] == PackageManager.PERMISSION_GRANTED);
            } else if(permissions[i].equals(Manifest.permission.MANAGE_EXTERNAL_STORAGE) ||
                    permissions[i].equals(Manifest.permission.READ_EXTERNAL_STORAGE)
            ) {
                //btnGaleria.setEnabled(grantResults[i] == PackageManager.PERMISSION_GRANTED);
            }
        }
    }

    public ArrayList<String> getPermisosNoAprobados(ArrayList<String>  listaPermisos) {
        ArrayList<String> list = new ArrayList<String>();
        Boolean habilitado;


        if (Build.VERSION.SDK_INT >= 23)
            for(String permiso: listaPermisos) {
                if (checkSelfPermission(permiso) != PackageManager.PERMISSION_GRANTED) {
                    list.add(permiso);
                    habilitado = false;
                }else
                    habilitado=true;

                if(permiso.equals(Manifest.permission.CAMERA))
                    btnCamara.setEnabled(habilitado);
                else if (permiso.equals(Manifest.permission.MANAGE_EXTERNAL_STORAGE)  ||
                        permiso.equals(Manifest.permission.READ_EXTERNAL_STORAGE));

            }


        return list;
    }


    @Override
    public void onFailure(@NonNull Exception e) {
        txtResults.setText("Error al procesar imagen");
    }

    @Override
    public void onSuccess(Text text) {
        List<Text.TextBlock> blocks = text.getTextBlocks();
        String resultados="";
        if (blocks.size() == 0) {
            resultados = "Sin resultados";
        }else{
            for (int i = 0; i < blocks.size(); i++) {
                List<Text.Line> lines = blocks.get(i).getLines();
                for (int j = 0; j < lines.size(); j++) {
                    List<Text.Element> elements = lines.get(j).getElements();
                    for (int k = 0; k < elements.size(); k++) {
                        resultados = resultados + elements.get(k).getText() + " ";
                    }
                }
            }
            resultados=resultados + "\n";
        }
        txtResults.setText(resultados);
    }

    int previewHeight = 0,previewWidth = 0;
    int sensorOrientation;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void setFragment() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            cameraId = manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        CameraConnectionFragment fragment;
        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                previewHeight = size.getHeight();    previewWidth = size.getWidth();
                                sensorOrientation = rotation - getScreenOrientation();
                            }
                        },
                        this,   R.layout.camara_ly,            new Size(640, 480));

        camera2Fragment.setCamera(cameraId);
        fragment = camera2Fragment;
        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }
    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;
    @Override
    public void onImageAvailable(ImageReader imageReader) {
        if (previewWidth == 0 || previewHeight == 0)           return;
        if (rgbBytes == null)    rgbBytes = new int[previewWidth * previewHeight];
        try {
            final Image image = imageReader.acquireLatestImage();
            if (image == null)    return;
            if (isProcessingFrame) {           image.close();            return;         }
            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();
            imageConverter =  new Runnable() {
                @Override
                public void run() {
                    ImageUtils.convertYUV420ToARGB8888( yuvBytes[0], yuvBytes[1], yuvBytes[2], previewWidth,  previewHeight,
                            yRowStride,uvRowStride, uvPixelStride,rgbBytes);
                }
            };
            postInferenceCallback =      new Runnable() {
                @Override
                public void run() {  image.close(); isProcessingFrame = false;  }
            };

            processImage();

        } catch (final Exception e) {    }

    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }
    TextToSpeech lector;
    private String label = "", nuevoLabel = "";
    private void processImage() {
        imageConverter.run();

        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

        try {
            ModelUnquant model = ModelUnquant.newInstance(getApplicationContext());
            rgbFrameBitmap = Bitmap.createScaledBitmap(rgbFrameBitmap, 224, 224, true);

            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[224 * 224];
            rgbFrameBitmap.getPixels(intValues, 0, rgbFrameBitmap.getWidth(), 0, 0, rgbFrameBitmap.getWidth(), rgbFrameBitmap.getHeight());

            int pixel = 0;
            for (int i = 0; i < rgbFrameBitmap.getHeight(); i++) {
                for (int j = 0; j < rgbFrameBitmap.getWidth(); j++) {
                    int val = intValues[pixel++];
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);
            ModelUnquant.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] probabilidades = outputFeature0.getFloatArray();
            int maxPos = 0;
            float maxPro = 0;

            for (int i = 0; i < probabilidades.length; i++) {
                if (probabilidades[i] > maxPro) {
                    maxPro = probabilidades[i];
                    maxPos = i;
                }
            }

            String str = "";
            label = clases.get(maxPos);
            final String nuevoLabel;
            nuevoLabel = clases.get(maxPos);

            for (int i = 0; i < clases.size(); i++) {
                if (i < probabilidades.length) {
                    str += String.format("%s: %.1f%%\n", clases.get(i), probabilidades[i] * 100);
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    txtResults.setText(label);
                }
            });

            lector = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        lector.setLanguage(new Locale("es", "ES"));
                        lector.setSpeechRate(1.0f);
                        lector.speak(label, TextToSpeech.QUEUE_ADD, null);
                        if (!nuevoLabel.equals(label)) {
                            label = nuevoLabel;
                            lector.speak(label, TextToSpeech.QUEUE_ADD, null);
                        }
                    }
                }
            });

            model.close();
        } catch (Exception e) {
            txtResults.setText("Error al procesar la imagen");
        }

        postInferenceCallback.run();
    }


    private void cargarLabels(Context context) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("labels.txt");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                clases.add(line);
            }
            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}