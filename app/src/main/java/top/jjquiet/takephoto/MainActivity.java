package top.jjquiet.takephoto;

import android.os.Handler;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.*;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.widget.TextView;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import top.jjquiet.takephoto.databinding.ActivityMainBinding;
import top.jjquiet.takephoto.databinding.DialogLayoutBinding;
import top.jjquiet.takephoto.databinding.VideoDialogLayoutBinding;
import android.Manifest;
import top.jjquiet.takephoto.services.NetworkClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import android.app.Dialog;
import android.content.ContentValues;

public class MainActivity extends AppCompatActivity {
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private DialogLayoutBinding dialogBinding;
    private VideoDialogLayoutBinding videoDialogBinding;
    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 103;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 104;
    private Uri photoURI;
    private Uri videoURI;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<Intent> takeVideoLauncher;
    private Dialog dialog;
    private WebSocket webSocket;
    private MediaRecorder mediaRecorder;
    private Camera camera;
    private SurfaceHolder holder;
    private boolean isRecording = false;
    private boolean isMediaRecorderPrepared = false;
    private String videoPath;
    private boolean isSurfaceReady = false;
    private boolean isRecordingRequested = false;
    private long startTime = 0;
    private TextView timerTextView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        dialog = new Dialog(this);
        dialogBinding = DialogLayoutBinding.inflate(getLayoutInflater());
        videoDialogBinding = VideoDialogLayoutBinding.inflate(getLayoutInflater());
        timerTextView = binding.timerTextView;
        setContentView(binding.getRoot());
        requestPermissions();
        setSupportActionBar(binding.toolbar);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        setupWebSocket();
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 50, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                JSONObject locationJson = new JSONObject();
                try {
                    locationJson.put("CameraName", "单兵1");
                    locationJson.put("LAT_VAL", String.valueOf(latitude));
                    locationJson.put("LNG_VAL", String.valueOf(longitude));
                    webSocket.send(locationJson.toString());
                    Log.d("JSON解析成功", "经纬度：latitude:" + latitude + "," + "longitude:" + longitude);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {}
        });
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean result) {
                if (result) {
                    if (navController.getCurrentDestination().getId() == R.id.FirstFragment) {
                        navController.navigate(R.id.action_FirstFragment_to_SecondFragment);
                    }
                    savePhotoUri(photoURI);
                    dialogBinding.photoView.setImageURI(photoURI);
                    dialog.setContentView(dialogBinding.getRoot());
                    dialog.show();
                } else {
                    // 用户取消或拍摄失败
                }
            }
        });
        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera();
            }
        });
        takeVideoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        videoURI = result.getData().getData();
                        videoDialogBinding.videoView.setVideoURI(videoURI);
                        dialog.setContentView(videoDialogBinding.getRoot());
                        dialog.show();
                    }
                });
        holder = binding.surfaceView.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                isSurfaceReady = true;
                    prepareMediaRecorder(holder);
            }
            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}
            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {}
        });
        binding.fabRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTakeVideo();
            }
        });
        dialogBinding.buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String description = dialogBinding.editTextDescription.getText().toString();
                uploadImage(description);
                dialog.dismiss();
                dialogBinding.editTextDescription.setText("");
            }
        });
        dialogBinding.buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        videoDialogBinding.buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String description = videoDialogBinding.editTextDescription.getText().toString();
                uploadVideoToServer(description);
                dialog.dismiss();
                videoDialogBinding.editTextDescription.setText("");
            }
        });
        videoDialogBinding.buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        binding.btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
            }
        });
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.action_album) {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            if (navController.getCurrentDestination().getId() == R.id.FirstFragment) {
                navController.navigate(R.id.action_FirstFragment_to_SecondFragment);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
    private void openCamera() {
        photoURI = createImageFile();
        takePictureLauncher.launch(photoURI);
    }
    private Uri createImageFile() {
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        values.put(MediaStore.Images.Media.DISPLAY_NAME, timeStamp + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/top.jjquiet.takephoto");
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        return uri;
    }
    private void savePhotoUri(Uri photoUri) {
        SharedPreferences sharedPrefs = getSharedPreferences("PhotoUris", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        // 获取已存储的照片Uri列表
        Set<String> photoUriSet = new HashSet<>(sharedPrefs.getStringSet("photoUris", new HashSet<>()));
        photoUriSet.add(photoUri.toString());
        editor.putStringSet("photoUris", photoUriSet);
        editor.apply();
    }
    private void uploadImage(String description) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(photoURI);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            // 处理Bitmap，例如显示在ImageView上
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            String uploaderName = "单兵1";
            // 创建一个不检查证书的 请求
            OkHttpClient client = NetworkClient.getUnsafeOkHttpClient();
            MultipartBody.Builder builder = new MultipartBody.Builder();
            builder.setType(MultipartBody.FORM);
            builder.addFormDataPart("file", getFileNameFromUri(photoURI),
                    RequestBody.create(MediaType.parse("image/jpeg"), byteArray));
            builder.addFormDataPart("uploader_name", uploaderName);
            builder.addFormDataPart("photo_description", description);
            RequestBody requestBody = builder
                    .build();
            Request request = new Request.Builder()
                    .url("http://192.168.0.100:8080/uploadPhoto") // 修改为自己的服务器地址 ToDo
                    .post(requestBody)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION);
                }
            }
            // 异步请求，避免阻塞主线程
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    // 处理成功响应
                    Log.d("霜序见我", "图片上传成功:"+response.toString());
                }
                @Override
                public void onFailure(Call call, IOException e) {
                    // 处理失败情况
                    Log.d("霜序见我", "图片上传失败" + e.toString());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            Log.d("霜序见我", "图片上传Exception:"+e.toString());
            e.printStackTrace();
        }
    }
    private void uploadVideoToServer(String description) {
        try {
            String uploaderName = "单兵1";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION);
                }
            }
            File videoFile = new File(videoPath);
            OkHttpClient client = NetworkClient.getUnsafeOkHttpClient();
            MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("uploader_name", uploaderName)
                .addFormDataPart("video_description", description)
                .addFormDataPart("file",videoPath.substring(videoPath.lastIndexOf("/") + 1),
                    RequestBody.create(MediaType.parse("video/*"), videoFile));
            RequestBody requestBody = builder.build();
            Request request = new Request.Builder()
                    .url("http://192.168.0.100:8080/uploadVideo")// 修改为自己的服务器地址 ToDo
                    .post(requestBody)
                    .build();
            // 异步请求，避免阻塞主线程
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    // 处理成功响应
                    Log.d("霜序见我", "视频上传成功："+response.toString());
                }
                @Override
                public void onFailure(Call call, IOException e) {
                    // 处理失败情况
                    Log.d("霜序见我", "视频上传失败" + e.toString());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            Log.d("霜序见我", "视频上传Exception"+e.toString());
            e.printStackTrace();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            // 检查每个权限是否被授予
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    // 权限被授予
//                    Log.d("霜序见我", permissions[i]+"权限被授予");
                } else {
                    // 权限被拒绝
                    Log.d("霜序见我", permissions[i]+"权限被拒绝");
                }
            }
        }
    }
    public String getFileNameFromUri(Uri photoUri) {
        String fileName = null;
        if (photoUri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(photoUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    // 在不同设备上，列名称可能会有所不同
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                    fileName = cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                // 处理异常
            }
        } else if (photoUri.getScheme().equals("file")) {
            File file = new File(photoUri.getPath());
            fileName = file.getName();
        }
        return fileName;
    }
    private void setupWebSocket() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url("ws://192.168.0.100:8080/ws").build();
        WebSocketListener webSocketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                // WebSocket连接成功
                Log.d("WebSocket", "WebSocket连接成功");
            }
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                // 收到服务器消息
                Log.d("WebSocket", "收到消息：" + text);
            }
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e("WebSocket", "Error during WebSocket connection", t);
                if (response != null) {
                    Log.e("WebSocket", "Response: " + response.toString());
                }
            }
            // ...其他回调方法...
        };
        webSocket = client.newWebSocket(request, webSocketListener);
    }
    private String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO
        };

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_CODE);
    }
    private void prepareMediaRecorder(SurfaceHolder holder) {
        Log.d("霜序见我", "prepareMediaRecorder");
        camera = Camera.open();
        camera.setDisplayOrientation(90);
        try {
            camera.setPreviewDisplay(holder);

        }catch (IOException e){
            Log.d("霜序见我", "camera setPreviewDisplay failed");
            e.printStackTrace();
        }
        camera.startPreview();
        camera.unlock();
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setCamera(camera);
        mediaRecorder.setOrientationHint(90);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(1280, 720); // 例如，设置分辨率
        mediaRecorder.setVideoFrameRate(30); // 设置帧率
        mediaRecorder.setVideoEncodingBitRate(1000000); // 设置比特率
        File videoFile = getOutputMediaFile(); // 创建文件保存录制的视频
        mediaRecorder.setOutputFile(videoFile.toString());
        videoPath = videoFile.toString();
        try {
            mediaRecorder.setPreviewDisplay(holder.getSurface());
            mediaRecorder.prepare();
            Log.d("霜序见我", "MediaRecorder prepare success");
        } catch (IOException e) {
            Log.e("霜序见我", "MediaRecorder prepare failed");
            e.printStackTrace();
        }
    }
    private File getOutputMediaFile(){
        // 检查存储卡是否已装入
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            return null;
        }
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SimpleTakePhoto");
        // 如果目录不存在，则创建它
        if (!mediaStorageDir.exists()){
            if (!mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // 创建媒体文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File mediaFile;
        String mImageName = "VID_" + timeStamp + ".mp4";
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        return mediaFile;
    }
    private void startRecording() {
        try {
            camera.stopPreview();
            startTime = System.currentTimeMillis();
            timerHandler.postDelayed(timerRunnable, 0);
            isRecording = true;
            mediaRecorder.start();
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void stopRecording() {
        if (mediaRecorder != null &&  isRecording) {
            isRecording = false;
            try {
                mediaRecorder.stop();
            } catch (RuntimeException stopException) {
                // 处理异常，例如记录日志或提醒用户
                Log.d("霜序见我","mediaRecord.stop:"+stopException);
            }
            mediaRecorder.release();
            camera.lock();
            camera.release();
            mediaRecorder = null;
            camera = null;
//            binding.fabRecord.setImageResource(android.R.drawable.ic_media_play); // 更换为开始图标
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            binding.surfaceContainer.setVisibility(View.GONE);
            videoDialogBinding.videoView.setVideoPath(videoPath);
            videoDialogBinding.videoView.start();
            dialog.setContentView(videoDialogBinding.getRoot());
            dialog.show();
        }
    }
    private void startTakeVideo(){
        if (!isRecording) {
            binding.surfaceContainer.setVisibility(View.VISIBLE);
            if (isSurfaceReady) {
                prepareMediaRecorder(holder);
            }
        } else {
            // 延迟停止录制
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopRecording();
                }
            }, 1000); // 延迟 300 毫秒
        }
    }
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            int hours = minutes / 60;
            minutes = minutes % 60;
//            binding.timerTextView.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            binding.timerTextView.setText(String.format("%02d:%02d",  minutes, seconds));
            timerHandler.postDelayed(this, 500);
        }
    };
}