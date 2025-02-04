package FileControl;

import java.io.File;

public class FileDeleteController {
    public void deleteFilesInDirectory(String dirPath) {
        File directory = new File(dirPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (file.delete()) {
                            System.out.println("Deleted file: " + file.getAbsolutePath());
                        } else {
                            System.out.println("Failed to delete file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        } else {
            System.out.println("Directory does not exist or is not a directory: " + dirPath);
        }
    }
}
