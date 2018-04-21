package com.safe.fmear;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

public class FileFinder implements FileFilter {

    private final String mPattern;
    private final List<File> mResults;

    // ---------------------------------------------------------------------------------------------
    // TODO: Currently we only use endsWith to find the files. In the future, we can use regex for
    // matching.
    public FileFinder(String matchPattern) {
        mPattern = matchPattern;
        mResults = new ArrayList<File>();
    }

    // ---------------------------------------------------------------------------------------------
    @Override
    public boolean accept(File filePath) {
        return filePath.isDirectory() || filePath.getName().toLowerCase().endsWith(mPattern);
    }

    // ---------------------------------------------------------------------------------------------
    public List<File> find(File... files) {
        List<File> results = new ArrayList<File>();
        findRecursively(results, files);
        return results;
    }

    // ---------------------------------------------------------------------------------------------
    private void findRecursively(List<File> results, File... files) {
        for (File file : files) {
            if (file.isDirectory()) {
                findRecursively(results, file.listFiles(this));
            } else  {
                boolean b = accept(file);
                results.add(file);
            }
        }
    }
}
