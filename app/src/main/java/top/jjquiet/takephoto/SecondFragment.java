package top.jjquiet.takephoto;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.SharedPreferences;

import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import top.jjquiet.takephoto.databinding.FragmentSecondBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static android.content.Context.MODE_PRIVATE;

public class SecondFragment extends Fragment {
    private FragmentSecondBinding binding;
    private ImageView imageView;
    private RecyclerView recyclerView;
    private PhotoAdapter photoAdapter;
    private ArrayList<Uri> photoList = new ArrayList<>();
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        recyclerView = binding.recyclerView;
        photoList = loadPhotoUris();
        Log.d("蒋建琪", "onCreateView photoSize: " + photoList.size());
        photoAdapter = new PhotoAdapter(photoList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(photoAdapter);
        return binding.getRoot();
    }
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        ImageView imageView = view.findViewById(R.id.CameraImageView);
//        if (getArguments() != null && getArguments().containsKey("photoUri")) {
//            Uri imageUri = Uri.parse(getArguments().getString("photoUri"));
//            imageView.setImageURI(imageUri); // Set the ImageView to display the image
//        }
        binding.buttonSecond.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            }
        });
    }
    public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
        private ArrayList<Uri> photoList;
        public PhotoAdapter(ArrayList<Uri> photos) {
            photoList = photos;
        }
        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.photo_item, parent, false);
            return new PhotoViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            Uri photoUri = photoList.get(position);
            Log.d("蒋建琪", "onBindViewHolder photoUri: " + photoUri.toString());
//            holder.imageView.setImageURI(photoUri);
            // 使用Glide来加载图片
            Glide.with(holder.imageView.getContext())
                    .load(photoUri)
                    .into(holder.imageView);
            Log.d("蒋建琪", "onBindViewHolder: " + photoUri.toString());
        }
        @Override
        public int getItemCount() {
            return photoList.size();
        }
        private class PhotoViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            public PhotoViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.image_view);
            }
        }
    }
    // 更新照片列表的方法
    public void addPhoto(Uri photoUri) {
        photoList.add(0, photoUri); // 将新照片添加到列表的开头
        photoAdapter.notifyItemInserted(0); // 通知Adapter插入操作
    }
    private ArrayList<Uri> loadPhotoUris() {
        SharedPreferences sharedPrefs = getActivity().getSharedPreferences("PhotoUris", MODE_PRIVATE);
        Set<String> photoUriSet = sharedPrefs.getStringSet("photoUris", new HashSet<>());
        ArrayList<Uri> photoUris = new ArrayList<>();
        for (String uriString : photoUriSet) {
            photoUris.add(Uri.parse(uriString));
        }
        // 可能需要根据实际情况对Uri列表进行排序，以保证新拍的照片在上面
        Collections.reverse(photoUris);
        return photoUris;
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}