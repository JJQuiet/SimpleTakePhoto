package top.jjquiet.takephoto;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import top.jjquiet.takephoto.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.Manifest;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final int REQUEST_IMAGE_CAPTURE = 102;
    private Uri photoURI;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // 初始化ActivityResultLauncher
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), new ActivityResultCallback<Boolean>() {
            @Override
            public void onActivityResult(Boolean result) {
                if (result) {
                    Bundle bundle = new Bundle();
                    bundle.putString("photoUri", photoURI.toString());
                    savePhotoUri(photoURI);
//                    SecondFragment secondFragment = new SecondFragment();
//                    secondFragment.setArguments(bundle);
                    if(navController.getCurrentDestination().getId() == R.id.FirstFragment){
                        navController.navigate(R.id.action_FirstFragment_to_SecondFragment, bundle);
                    }
                } else {
                    // 用户取消或拍摄失败
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
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
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
            File photoFile = createImageFile();
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,
                        "top.jjquiet.takephoto.file-provider",
                        photoFile);
                takePictureLauncher.launch(photoURI);
                savePhotoUri(photoURI);
            }
    }
    private File createImageFile()  {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
        String imageFileName = timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = null;
        try {
            image = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpg",   /* suffix */
                    storageDir      /* directory */
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return image;
    }
    private void savePhotoUri(Uri photoUri) {
        SharedPreferences sharedPrefs = getSharedPreferences("PhotoUris", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPrefs.edit();

        // 获取已存储的照片Uri列表
        Set<String> photoUriSet = new HashSet<>(sharedPrefs.getStringSet("photoUris", new HashSet<>()));
        // 将新的照片Uri添加到列表中
        photoUriSet.add(photoUri.toString());
        // 保存更新后的列表
        editor.putStringSet("photoUris", photoUriSet);
        editor.apply();
    }

}