package kom.study.simplediary;

import android.app.AlertDialog;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.github.channguyen.rsv.RangeSliderView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;

public class Fragment2 extends Fragment {
    private static final String TAG = "Fragment2";

    Context context;
    OnTabItemSelectedListener listener;
    OnRequestListener requestListener;

    ImageView weatherIcon;
    TextView locationTextView;
    TextView dateTextView;

    EditText contentsInput;
    ImageView pictureImageView;

    boolean isPhotoCaptured;
    boolean isPhotoFileSaved;
    boolean isPhotoCanceled;
    boolean save_state;

    int selectedPhotoMenu;

    File file;
    Bitmap resultPhotoBitmap;

    int mMode = AppConstants.MODE_INSERT;
    int _id = -1;
    int weatherIndex = 0;

    RangeSliderView sliderView;
    RangeSliderView moodSlider;
    int moodIndex = 2;

    Note item;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        this.context = context;

        if(context instanceof OnTabItemSelectedListener) {
            listener = (OnTabItemSelectedListener) context;
        }

        if(context instanceof OnRequestListener) {
            requestListener = (OnRequestListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if(context != null) {
            context = null;
            listener = null;
            requestListener = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if(!save_state) {
            Log.d(TAG,"Refresh Fragment");
            mMode = AppConstants.MODE_INSERT;
            requestListener.onRequest("getCurrentLocation");
            item = null;
            contentsInput.setText("");
            pictureImageView.setImageResource(R.drawable.imagetoset);
            sliderView.setInitialIndex(2);
            moodIndex = 2;
            save_state = true;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment2,container,false);

        initUI(rootView);

        if(requestListener != null) {
            requestListener.onRequest("getCurrentLocation");
        }

        return rootView;
    }

    private void initUI(ViewGroup rootView) {
        save_state = true;

        weatherIcon = rootView.findViewById(R.id.weatherIcon);
        locationTextView = rootView.findViewById(R.id.locationTextView);
        dateTextView = rootView.findViewById(R.id.dateTextView);

        contentsInput = rootView.findViewById(R.id.contentsInput);
        pictureImageView = rootView.findViewById(R.id.pictureImageView);

        pictureImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isPhotoCaptured || isPhotoFileSaved) {
                    showDialog(AppConstants.CONTENT_PHOTO_EX);
                } else {
                    showDialog(AppConstants.CONTENT_PHOTO);
                }
            }
        });

        Button saveButton = rootView.findViewById(R.id.saveButton);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mMode == AppConstants.MODE_INSERT) {
                    saveNote();
                } else if(mMode == AppConstants.MODE_MODIFY) {
                    modifyNote();
                }

                if(listener != null) {
                    save_state = false;
                    listener.onTabSelected(0);
                }
            }
        });

        Button closeButton = rootView.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(listener != null) {
                    save_state = false;
                    listener.onTabSelected(0);
                }
            }
        });

        Button deleteButton = rootView.findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteNote();

                if(listener != null) {
                    save_state = false;
                    listener.onTabSelected(0);
                }
            }
        });

        sliderView = rootView.findViewById(R.id.sliderView);
        sliderView.setOnSlideListener(new RangeSliderView.OnSlideListener() {
            @Override
            public void onSlide(int index) {
                //Toast.makeText(context,"moodIndex Changed to " + index,Toast.LENGTH_LONG).show();
                moodIndex = index;
            }
        });

        sliderView.setInitialIndex(2);

        if(mMode == AppConstants.MODE_MODIFY) {
            setAddress(item.address);
            setWeather(item.weather);
            setDateString(item.createDateStr);
            contentsInput.setText(item.contents);
            if(item.picture != "") {
                pictureImageView.setImageURI(Uri.parse("file://" + item.picture));
            } else {
                pictureImageView.setImageResource(R.drawable.noimagefound);
            }
            sliderView.setInitialIndex(Integer.parseInt(item.mood));
        }
    }

    public void setMode(int mode) { mMode = mode; }
    public void setItem(Note item) {
        this.item = item;
    }

    /***Save, Modify, Delete Note***/
    private void saveNote() {
        String address = locationTextView.getText().toString();
        String contents = contentsInput.getText().toString();

        String picturePath = savePicture();

        String sql = "insert into " + NoteDatabase.TABLE_NOTE +
                "(WEATHER, ADDRESS, LOCATION_X, LOCATION_Y, CONTENTS, MOOD, PICTURE) values (" +
                "'"+ weatherIndex + "', " +
                "'"+ address + "', " +
                "'"+ "" + "', " +
                "'"+ "" + "', " +
                "'"+ contents + "', " +
                "'"+ moodIndex + "', " +
                "'"+ picturePath + "')";

        Log.d(TAG, "sql : " + sql);
        NoteDatabase database = NoteDatabase.getInstance(context);
        database.execSQL(sql);
    }

    private void modifyNote() {
        if(item != null) {
            String address = locationTextView.getText().toString();
            String contents = contentsInput.getText().toString();

            String picturePath = savePicture();

            String sql = "update " + NoteDatabase.TABLE_NOTE +
                    " set " +
                    "  WEATHER = '" + weatherIndex + "'" +
                    "  ,ADDRESS = '" + address + "'" +
                    "  ,LOCATION_X = '" + "" + "'" +
                    "  ,LOCATION_Y = '" + "" + "'" +
                    "  ,CONTENTS = '" + contents + "'" +
                    "  ,MOOD = '" + moodIndex + "'" +
                    "  ,PICTURE = '" + picturePath + "'" +
                    " where " +
                    "  _id = " + item._id;

            Log.d(TAG, "sql : " + sql);
            NoteDatabase database = NoteDatabase.getInstance(context);
            database.execSQL(sql);
        }
    }

    private void deleteNote() {
        AppConstants.println("deleteNote called");

        if(item != null) {
            String sql = "delete from " + NoteDatabase.TABLE_NOTE +
                    " where " +
                    "  _id = " + item._id;

            Log.d(TAG, "sql : " + sql);
            NoteDatabase database = NoteDatabase.getInstance(context);
            database.execSQL(sql);
        }
    }

    /***Weather, Address, Date***/
    public void setWeather(String data) {
        if(data != null) {
            if(data.equals("맑음")) {
                weatherIcon.setImageResource(R.drawable.weather_1);
            } else if(data.equals("구름 조금")) {
                weatherIcon.setImageResource(R.drawable.weather_2);
            } else if(data.equals("구름 많음")) {
                weatherIcon.setImageResource(R.drawable.weather_3);
            } else if(data.equals("흐림")) {
                weatherIcon.setImageResource(R.drawable.weather_4);
            } else if(data.equals("비")) {
                weatherIcon.setImageResource(R.drawable.weather_5);
            } else if(data.equals("눈/비")) {
                weatherIcon.setImageResource(R.drawable.weather_6);
            }  else if(data.equals("눈")) {
                weatherIcon.setImageResource(R.drawable.weather_7);
            } else {
                Log.d("Fragment2", "Unknown weather string : " + data);
            }
        }
    }

    public void setAddress(String data) {
        locationTextView.setText(data);
    }

    public void setDateString(String dataString) {
        dateTextView.setText(dataString);
    }

    /***Photo***/
    public void showDialog(int id) {
        AlertDialog.Builder builder = null;

        switch(id) {

            case AppConstants.CONTENT_PHOTO:
                builder = new AlertDialog.Builder(context);

                builder.setTitle("사진 메뉴 선택");
                builder.setSingleChoiceItems(R.array.array_photo, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedPhotoMenu = which;
                    }
                });
                builder.setPositiveButton("선택", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(selectedPhotoMenu == 0) {
                            showPhotoCaptureActivity();
                        } else if(selectedPhotoMenu == 1) {
                            showPhotoSelectionActivity();
                        }
                    }
                });
                builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

                break;

            case AppConstants.CONTENT_PHOTO_EX:
                builder = new AlertDialog.Builder(context);

                builder.setTitle("사진 메뉴 선택");
                builder.setSingleChoiceItems(R.array.array_photo_ex, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selectedPhotoMenu = which;
                    }
                });
                builder.setPositiveButton("선택", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(selectedPhotoMenu == 0) {
                            showPhotoCaptureActivity();
                        } else if(selectedPhotoMenu == 1) {
                            showPhotoSelectionActivity();
                        } else if(selectedPhotoMenu == 2) {
                            isPhotoCanceled = true;
                            isPhotoCaptured = false;

                            pictureImageView.setImageResource(R.drawable.picture1);
                        }
                    }
                });
                builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });

                break;

            default:
                break;
        }

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showPhotoCaptureActivity() {
        if(file == null) {
            file = createFile();
        }

        Uri fileUri = FileProvider.getUriForFile(context,"kom.study.simplediary.fileprovider",file);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,fileUri);
        if(intent.resolveActivity(context.getPackageManager()) != null) {
            startActivityForResult(intent,AppConstants.REQ_PHOTO_CAPTURE);
        }
    }

    private File createFile() {
        String filename = "capture.jpg";
        File storageDir = Environment.getExternalStorageDirectory();
        File outFile = new File(storageDir,filename);

        return outFile;
    }

    public void showPhotoSelectionActivity() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, AppConstants.REQ_PHOTO_SELECTION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if(intent != null) {
            switch(requestCode) {
                case AppConstants.REQ_PHOTO_CAPTURE:
                    Log.d(TAG,"onActivityResult() for REQ_PHOTO_CAPTURE");
                    Log.d(TAG,"resultCode : " + resultCode);

                    resultPhotoBitmap = decodeSampledBitmapFromResource(file, pictureImageView.getWidth(), pictureImageView.getHeight());
                    pictureImageView.setImageBitmap(resultPhotoBitmap);

                    break;
                case AppConstants.REQ_PHOTO_SELECTION:
                    Log.d(TAG,"onActivityResult() for REQ_PHOTO_SELECTION");

                    Uri selectedImage = intent.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    Cursor cursor = context.getContentResolver().query(selectedImage,filePathColumn,null,null,null);
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                    String filePath = cursor.getString(columnIndex);
                    cursor.close();

                    resultPhotoBitmap = decodeSampledBitmapFromResource(new File(filePath), pictureImageView.getWidth(), pictureImageView.getHeight());
                    pictureImageView.setImageBitmap(resultPhotoBitmap);
                    isPhotoCaptured = true;

                    break;
            }
        }
    }

    public static Bitmap decodeSampledBitmapFromResource(File res, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(res.getAbsolutePath(),options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(res.getAbsolutePath(),options);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if(height > reqHeight || width > reqWidth) {
            final int halfHeight = height;
            final int halfWidth = width;

            while((halfHeight / inSampleSize) >= reqHeight && (halfHeight / inSampleSize) >= reqHeight) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private String createFileName() {
        Date curDate = new Date();
        String curDateStr = String.valueOf(curDate.getTime());

        return curDateStr;
    }

    private String savePicture() {
        if(resultPhotoBitmap == null) {
            AppConstants.println("No Picture to be saved");
            return "";
        }

        File photoFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + AppConstants.FOLDER_PHOTO);

        if(!photoFolder.isDirectory()) {
            Log.d(TAG,"creating photo folder : " + photoFolder);
            photoFolder.mkdirs();
        }

        String photoFilename = createFileName();
        String picturePath = photoFolder + File.separator + photoFilename;
        Log.d(TAG,picturePath);

        try {
            FileOutputStream outstream = new FileOutputStream(picturePath);
            resultPhotoBitmap.compress(Bitmap.CompressFormat.PNG,100,outstream);
            outstream.close();
        } catch (Exception e) {
            Log.d(TAG,"Picture Save Failed");
            Log.d(TAG,e.toString());
            e.printStackTrace();
        }

        return picturePath;
    }
}
