package top.jjquiet.takephoto;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import top.jjquiet.takephoto.databinding.ActivityMainBinding;
import top.jjquiet.takephoto.databinding.DialogLayoutBinding;

import android.view.Menu;
import android.view.MenuItem;
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
//import services.NetworkClient;
import android.app.Dialog;
import android.content.ContentValues;
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private DialogLayoutBinding dialogBinding;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_IMAGE_CAPTURE = 102;
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 103;
    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private Uri photoURI;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Dialog dialog;
    private WebSocket webSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION);
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        dialog = new Dialog(this);
        dialogBinding = DialogLayoutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        setupWebSocket();
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4000, 3, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                // 在这里更新 UI 或处理位置信息
//                Log.d("经纬度：", "latitude:"+latitude + "," + "longitude:" + longitude);
                // 发送位置信息到服务器
                JSONObject locationJson = new JSONObject();
                try {
                    locationJson.put("CameraName", "单兵1");
//                    locationJson.put("LAT_VAL", latitude);
                    locationJson.put("LAT_VAL", String.valueOf(latitude));
                    locationJson.put("LNG_VAL", String.valueOf(longitude));
//                    locationJson.put("LNG_VAL", longitude);
                    webSocket.send(locationJson.toString());
                    Log.d("JSON解析成功", "经纬度：latitude:"+latitude + "," + "longitude:" + longitude);
                } catch (JSONException e) {
                    Log.d("JSON", "JSON解析失败");
                    e.printStackTrace();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        });

        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean result) {
                if (result) {
                    if(navController.getCurrentDestination().getId() == R.id.FirstFragment){
                        navController.navigate(R.id.action_FirstFragment_to_SecondFragment);
                    }
                    savePhotoUri(photoURI);
//                    uploadImage(); // 调用上传图片的方法
                    // 上传图片
                    dialogBinding.photoView.setImageURI(photoURI);
                    dialog.setContentView(dialogBinding.getRoot());
                    dialog.show();
                } else {
                    // 用户取消或拍摄失败
                    Log.d("蒋建琪取消","用户取消拍摄");
                }
            }
        });
        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                } else {
                    // Permission has already been granted
                }
                openCamera();
            }
        });
        dialogBinding.buttonUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String description = dialogBinding.editTextDescription.getText().toString();
                uploadImage(description);
                // 这里添加上传图片和描述的逻辑
                dialog.dismiss();
            }
        });
        dialogBinding.buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
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
    private Uri createImageFile()  {
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        values.put(MediaStore.Images.Media.DISPLAY_NAME, timeStamp+".jpg");
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
                    // ToDo
//                    .url("http://192.168.0.113:8080/uploadPhoto") // 修改为自己的服务器地址
                    .url("http://192.168.43.3:8080/uploadPhoto")
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
                    Log.d("蒋建琪onResponse",response.toString());
                }
                @Override
                public void onFailure(Call call, IOException e) {
                    // 处理失败情况
                    Log.d("蒋建琪onFailure","失败"+e.toString());
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            Log.d("蒋建琪Exception",e.toString());
            e.printStackTrace();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予
                Log.d("蒋建琪","权限被授予");
            } else {
                // 权限被拒绝
                Log.d("蒋建琪","权限被拒绝");
            }
        }
    }
    public String getFileNameFromUri( Uri photoUri) {
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
        Request request = new Request.Builder().url("ws://192.168.0.144:8080/ws").build();
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

}