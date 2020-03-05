package org.beahugs.imagepicker.model;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;


import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import androidx.core.content.ContextCompat;

import com.donkingliang.imageselector.R;

import org.beahugs.imagepicker.ImagePicker;
import org.beahugs.imagepicker.entry.Folder;
import org.beahugs.imagepicker.entry.Image;
import org.beahugs.imagepicker.utils.ImageUtil;
import org.beahugs.imagepicker.utils.StringUtils;
import org.beahugs.imagepicker.utils.UriUtils;
import org.beahugs.imagepicker.utils.VersionUtils;

import static org.beahugs.imagepicker.model.FileLoadModel.getAllFile;
import static org.beahugs.imagepicker.model.FileLoadModel.getDurationCondition;

/**
 * @Author: wangyibo
 * @Version: 1.0
 */
public class ImageModel {

    /**
     * 缓存图片
     */
    private static ArrayList<Folder> cacheImageList = null;
    private static boolean isNeedCache = false;
    private static PhotoContentObserver observer;

    /**
     * 预加载图片
     *
     * @param context
     */
    public static void preloadAndRegisterContentObserver(final Context context) {
        isNeedCache = true;
        if (observer == null) {
            observer = new PhotoContentObserver(context.getApplicationContext());
            context.getApplicationContext().getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, observer);
        }
        preload(context);
    }

    private static void preload(final Context context) {
        int hasWriteExternalPermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (hasWriteExternalPermission == PackageManager.PERMISSION_GRANTED) {
            //有权限，加载图片。
            loadImageForSDCard(context, true, null, 0);
        }
    }

    /**
     * 清空缓存
     */
    public static void clearCache(Context context) {
        isNeedCache = false;
        if (observer != null) {
            context.getApplicationContext().getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (ImageModel.class) {
                    if (cacheImageList != null) {
                        cacheImageList.clear();
                        cacheImageList = null;
                    }
                }
            }
        }).start();
    }

    /**
     * 从SDCard加载图片
     *
     * @param context
     * @param callback
     */
    public static void loadImageForSDCard(final Context context, final DataCallback callback, int fileType) {
        loadImageForSDCard(context, false, callback, fileType);
    }

    /**
     * 从SDCard加载图片
     *
     * @param context
     * @param isPreload 是否是预加载
     * @param callback
     */
    private static void loadImageForSDCard(final Context context, final boolean isPreload, final DataCallback callback, final int fileType) {
        //由于扫描图片是耗时的操作，所以要在子线程处理。
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (ImageModel.class) {
                    boolean isAndroidQ = VersionUtils.isAndroidQ();
                    String imageCacheDir = ImageUtil.getImageCacheDir(context);
                    ArrayList<Folder> folders = null;
                    if (cacheImageList == null || isPreload) {
                        ArrayList<Image> imageList = loadImage(context);
                        ArrayList<Image> videoList = loadVideo(context);

                        Log.i("videosize",videoList.size()+"");
                        imageList.addAll(videoList);

                        //2020年3月5日   为了显示视频   需要优化
                       // ArrayList<Image> images = new ArrayList<>();

//                        for (Image image : imageList) {
//                            //过滤未下载完成或者不存在的文件
//                            boolean isEffective = isAndroidQ ? true  // 由于在Android Q用uri判断图片是否有效的方法耗时，所以去掉判断。
//                                    : ImageUtil.isEffective(image.getPath());
//                            //过滤剪切保存的图片；
//                            boolean isCutImage = ImageUtil.isCutImage(imageCacheDir, image.getPath());
//                            if (isEffective && !isCutImage) {
//                                images.add(image);
//                            }
//                        }
                   //     Log.i("imagesimagesimages",images.size());
                        Collections.reverse(imageList);
                        folders = splitFolder(context, imageList);
                        if (isNeedCache) {
                            cacheImageList = folders;
                        }
                    } else {
                        folders = cacheImageList;
                    }

                    Log.i("filesize",folders.size()+"");
                    if (callback != null) {
                        callback.onSuccess(folders);
                    }
                }
            }
        }).start();
    }

    /**
     * 从SDCard加载图片
     *
     * @param context
     * @return
     */
    private static synchronized ArrayList<Image> loadImage(Context context) {
        //扫描图片
        Uri mImageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        ContentResolver mContentResolver = context.getContentResolver();

        Cursor mCursor = mContentResolver.query(mImageUri, new String[]{
                        MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED,
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.MIME_TYPE},
                null
                ,
                null,
                MediaStore.Images.Media.DATE_ADDED);

        ArrayList<Image> images = new ArrayList<>();

        //读取扫描到的图片
        if (mCursor != null) {
            while (mCursor.moveToNext()) {
                // 获取图片的路径
                long id = mCursor.getLong(mCursor.getColumnIndex(MediaStore.Images.Media._ID));
                String path = mCursor.getString(
                        mCursor.getColumnIndex(MediaStore.Images.Media.DATA));
                //获取图片名称
                String name = mCursor.getString(
                        mCursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));
                //获取图片时间
                long time = mCursor.getLong(
                        mCursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED));

                //获取图片类型
                String mimeType = mCursor.getString(
                        mCursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE));

                //获取图片uri
                Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(String.valueOf(id)).build();

                images.add(new Image(path, time, name, mimeType, uri));
            }
            mCursor.close();
        }
        return images;
    }


    public static synchronized ArrayList<Image> loadVideo(Context context) {
        String[] projection = {MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.MIME_TYPE};
        Uri mvideoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        ContentResolver mContentResolver = context.getContentResolver();

        Cursor mCursor = mContentResolver.query(mvideoUri, projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED);

        ArrayList<Image> images = new ArrayList<>();

        //读取扫描到的图片
        if (mCursor != null) {
            while (mCursor.moveToNext()) {
                // 获取图片的路径
                long id = mCursor.getLong(mCursor.getColumnIndex(MediaStore.Video.Media._ID));
                String path = mCursor.getString(
                        mCursor.getColumnIndex(MediaStore.Video.Media.DATA));
                //获取图片名称
                String name = mCursor.getString(
                        mCursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME));
                //获取图片时间
                long time = mCursor.getLong(
                        mCursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED));

                //获取图片类型
                String mimeType = mCursor.getString(
                        mCursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE));

                //获取图片uri
                Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(String.valueOf(id)).build();

                images.add(new Image(path, time, name, mimeType, uri));
            }
            mCursor.close();
        }
        return images;
    }


    /**
     * 检查图片是否存在。ContentResolver查询处理的数据有可能文件路径并不存在。
     *
     * @param filePath
     * @return
     */
    private static boolean checkImgExists(String filePath) {
        return new File(filePath).exists();
    }

    private static String getPathForAndroidQ(Context context, long id) {
        return UriUtils.getPathForUri(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                .appendPath(String.valueOf(id)).build());
    }

    /**
     * 把图片按文件夹拆分，第一个文件夹保存所有的图片
     *
     * @param images
     * @return
     */
    private static ArrayList<Folder> splitFolder(Context context, ArrayList<Image> images) {
        ArrayList<Folder> folders = new ArrayList<>();
        folders.add(new Folder(context.getString(R.string.selector_all_image), images));

        if (images != null && !images.isEmpty()) {
            int size = images.size();
            for (int i = 0; i < size; i++) {
                String path = images.get(i).getPath();
                String name = getFolderName(path);
                if (StringUtils.isNotEmptyString(name)) {
                    Folder folder = getFolder(name, folders);
                    folder.addImage(images.get(i));
                }
            }
        }
        return folders;
    }

    /**
     * Java文件操作 获取文件扩展名
     */
    public static String getExtensionName(String filename) {
        if (filename != null && filename.length() > 0) {
            int dot = filename.lastIndexOf('.');
            if (dot > -1 && dot < filename.length() - 1) {
                return filename.substring(dot + 1);
            }
        }
        return "";
    }

    /**
     * 根据图片路径，获取图片文件夹名称
     *
     * @param path
     * @return
     */
    private static String getFolderName(String path) {
        if (StringUtils.isNotEmptyString(path)) {
            String[] strings = path.split(File.separator);
            if (strings.length >= 2) {
                return strings[strings.length - 2];
            }
        }
        return "";
    }

    private static Folder getFolder(String name, List<Folder> folders) {
        if (!folders.isEmpty()) {
            int size = folders.size();
            for (int i = 0; i < size; i++) {
                Folder folder = folders.get(i);
                if (name.equals(folder.getName())) {
                    return folder;
                }
            }
        }
        Folder newFolder = new Folder(name);
        folders.add(newFolder);
        return newFolder;
    }

    public interface DataCallback {
        void onSuccess(ArrayList<Folder> folders);
    }

    private static class PhotoContentObserver extends ContentObserver {

        private Context context;

        public PhotoContentObserver(Context appContext) {
            super(null);
            context = appContext;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            preload(context);
        }
    }


}
