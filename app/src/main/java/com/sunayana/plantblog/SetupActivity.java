package com.sunayana.plantblog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class SetupActivity extends AppCompatActivity {

    private CircleImageView setupImage;
    private Uri mainImageUri = null;
    private String user_id;

    private EditText setup_name;
    private Button setup_btn;
    private ProgressBar setup_progress;

    private StorageReference StorageRef;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    private boolean isChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        Toolbar setupToolbar = findViewById(R.id.setupToolbar);
        setSupportActionBar(setupToolbar);
        getSupportActionBar().setTitle("Account Setup");

        firebaseAuth = FirebaseAuth.getInstance();

        user_id = firebaseAuth.getCurrentUser().getUid();
        StorageRef = FirebaseStorage.getInstance().getReference();
        firebaseFirestore = FirebaseFirestore.getInstance();

        setupImage = findViewById(R.id.setup_image);
        setup_name = findViewById(R.id.setup_name);
        setup_btn = findViewById(R.id.setup_btn);
        setup_progress = findViewById(R.id.setup_progress);

        setup_progress.setVisibility(View.VISIBLE);
        setup_btn.setEnabled(false);
        firebaseFirestore.collection("Users").document(user_id).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if(task.isSuccessful()){
                    if(task.getResult().exists()){
                        String name = task.getResult().getString("name");
                        String image = task.getResult().getString("image");

                        setup_name.setText(name);

                        RequestOptions placeholderRequest = new RequestOptions();
                        placeholderRequest.placeholder(R.drawable.default_image);

                        Glide.with(SetupActivity.this).load(image).into(setupImage);
                    }
                }else{
                    String error = task.getException().getMessage();
                    Toast.makeText(SetupActivity.this, "\"(FIRESTORE Retrieve Error) " + error , Toast.LENGTH_LONG).show();
                }

                setup_progress.setVisibility(View.INVISIBLE);
                setup_btn.setEnabled(true);
            }
        });

        setup_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String user_name = setup_name.getText().toString();
                if (!TextUtils.isEmpty(user_name) && mainImageUri != null) {
                    setup_progress.setVisibility(View.VISIBLE);
                    if(isChanged) {
                            final String user_id = firebaseAuth.getCurrentUser().getUid();

                            StorageReference image_path = StorageRef.child("profile_image").child(user_id + ".jpeg");
                            image_path.putFile(mainImageUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {

                                    if (task.isSuccessful()) {
                                        storeFirestore(task, user_name);
                                    } else {
                                        String error = task.getException().getMessage();
                                        Toast.makeText(SetupActivity.this, "Error : " + error, Toast.LENGTH_LONG).show();
                                        setup_progress.setVisibility(View.INVISIBLE);
                                    }

                                }
                            });
                    }else{
                            storeFirestore(null, user_name);
                    }
                }
            }
        });

        setupImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    if(ContextCompat.checkSelfPermission(SetupActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
                        Toast.makeText(SetupActivity.this, "Permission Denied", Toast.LENGTH_LONG).show();
                        ActivityCompat.requestPermissions(SetupActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                    }else{
                        BuildImagePicker();
                    }
                }else{
                    BuildImagePicker();
                }
            }
        });
    }

    private void storeFirestore(Task<UploadTask.TaskSnapshot> task, String user_name) {
        Uri download_uri;

        if(task != null){
            String prob = task.getResult().getStorage().getDownloadUrl().toString();
            Uri sol = Uri.parse(prob);
            download_uri = sol;
        }else{
            download_uri = mainImageUri;
        }

        Map<String, String> userMap = new HashMap<>();
        userMap.put("name", user_name);
        userMap.put("image", download_uri.toString());

        firebaseFirestore.collection("Users").document(user_id).set(userMap).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {

                if(task.isSuccessful()){
                    Toast.makeText(SetupActivity.this, "The user Settings are updated." , Toast.LENGTH_LONG).show();
                    Intent mainIntent = new Intent(SetupActivity.this, MainActivity.class);
                    startActivity(mainIntent);
                    finish();
                }else{
                    String error = task.getException().getMessage();
                    Toast.makeText(SetupActivity.this, "(FIRESTORE Error) : " + error , Toast.LENGTH_LONG).show();
                }
                setup_progress.setVisibility(View.INVISIBLE);
            }

        });


    }

    private void BuildImagePicker() {
        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON)
                .setAspectRatio(1,1)
                .start(SetupActivity.this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                mainImageUri = result.getUri();
                setupImage.setImageURI(mainImageUri);
                isChanged = true;
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
            }
        }
    }
}
