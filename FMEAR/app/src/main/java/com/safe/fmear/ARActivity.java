package com.safe.fmear;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// =================================================================================================
// ARActivity
public class ARActivity extends AppCompatActivity {

    // ---------------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);

        // Get the data from the intent and unzip the content
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();

                // Get the temp directory
                File tempDir = tempDirectory();

                // Unzip the content to the temporary directory
                unzipContent(uri, tempDir);

                // Find all the .obj files
                List<File> objFiles = new FileFinder(".obj").find(tempDir);
                for (File file : objFiles) {
                    Log.d("FME AR", "OBJ File: " + file.toString());
                }

            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // This function delete all files and sub-directories of the specified file.
    private void deleteFileRecursively(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteFileRecursively(child);

        fileOrDirectory.delete();
    }

    // ---------------------------------------------------------------------------------------------
    // This function returns a directory named "fmear" in the cache directory.
    private File tempDirectory() {
        // Get the cache directory
        File cacheDir = getCacheDir();

        // Find out if we already have the directory in the cache dir
        File fmearDir = new File(cacheDir.toString(), "fmear");
        try {
            // Delete the fmear directory from previous runs and
            // recreate it so that it's empty
            if (fmearDir.exists()) {
                deleteFileRecursively(fmearDir);
            }
            fmearDir.mkdir();
        } catch (SecurityException e) {
            Log.e("FME AR", "Failed to create the temp directory \"" + fmearDir.toString() + "\"");
        }

        Log.i("FME AR", "Temp directory: \"" + fmearDir.toString() + "\"");
        return fmearDir;
    }

    // ---------------------------------------------------------------------------------------------
    // This function unzip the content from the contentPath to the destinationFolder. This
    // function creates all the directories necessary for the unzipped files.
    private boolean unzipContent(Uri contentPath, File destinationFolder)
    {
        try
        {
            InputStream inputStream = getContentResolver().openInputStream(contentPath);
            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream));

            // Create a buffer to read the zip file content
            byte[] buffer = new byte[1024];
            int numBytes;

            String zipEntryName;
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null)
            {
                zipEntryName = zipEntry.getName();

                File unzippedFile = new File(destinationFolder, zipEntryName);

                // Skip the __MACOSX folders in the .fmear file in case the .fmear archive was
                // created on macOS with the __MACOSX resource fork.
                if (!zipEntryName.startsWith("__MACOSX")) {
                    if (zipEntry.isDirectory()) {
                        // If the entry is a directory, we need to make sure all the parent
                        // directories leading up to this directory exist before writing a file
                        // in the directory
                        unzippedFile.mkdirs();
                    } else {
                        // If the entry is a file, we need to make sure all the parent directories
                        // leading up to this file exist before writing the file to the path.
                        File subfolder = unzippedFile.getParentFile();
                        if (subfolder != null) {
                            subfolder.mkdirs();
                        }

                        // Now we can unzip the file
                        Log.i("FME AR", "Unzipping '" + unzippedFile.toString() + "' ...");
                        FileOutputStream fileOutputStream = new FileOutputStream(unzippedFile);
                        while ((numBytes = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, numBytes);
                        }
                        fileOutputStream.close();
                    }
                }

                zipInputStream.closeEntry();
            }

            zipInputStream.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
