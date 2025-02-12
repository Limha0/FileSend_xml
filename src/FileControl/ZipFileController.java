package FileControl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.Key;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import Config.configControllerClass;

public class ZipFileController {
 // dirPath에 있는 파일을 fileName이라는 이름의 파일로 압축
    public String createZipFileUsingDirPath(String dirPath, String fileName, String string) {
        String returnString = "";
        try {
            Properties properties = (new configControllerClass()).getConfigObject();
            
            // 로컬 테스트 (프로젝트 내에 있는 config 사용)
//            configControllerClass configObject = new configControllerClass();
//            Properties properties = configObject.getConfigObject();
            
            
            String zipDirPath = properties.getProperty("zip_file_send_dirpath");

            File readFileDir = new File(dirPath);
            File zipDir = new File(zipDirPath);
            if (!zipDir.exists()) {
                zipDir.mkdirs();
            }

            String zipFilePath = zipDirPath + fileName + ".zip";
            try (FileOutputStream fos = new FileOutputStream(zipFilePath);
                 ZipOutputStream zipFleOutputStream = new ZipOutputStream(fos)) {

                returnString = recursiveCreateZipFile(zipFleOutputStream, readFileDir, "");
            }

            if (returnString.isEmpty()) {
                returnString = zipFilePath;
            }

        } catch (Exception e) {
            returnString = "**Error_Msg** " + e.getMessage();
        }
        return returnString;
    }

    // 📌 **ZIP 내부에 XML 파일 압축**
    private String recursiveCreateZipFile(ZipOutputStream zos, File readDir, String relativeDirPath) {
        String resultStr = "";
        try {
            if (readDir == null || !readDir.exists()) {
                return "";
            }

            File[] files = readDir.listFiles();
            if (files != null) {
                byte[] buffer = new byte[8192];  // **버퍼 크기 8KB (속도 최적화)**

                for (File file : files) {
                    if (!file.isDirectory() && file.getName().endsWith(".xml")) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            zos.putNextEntry(new ZipEntry(relativeDirPath + file.getName()));
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, length);
                            }
                            zos.closeEntry();
                        }
                    }
                }
            }
        } catch (Exception e) {
            resultStr = "**Error_Msg** " + e.getMessage();
        }
        return resultStr;
    }

    // 📌 **ZIP 파일 AES 암호화 (송신)**
    public String encryptionZipFile(String cryptModeFlag, String filePath) {
        String resultStr = "";
        try {
            Properties properties = (new configControllerClass()).getConfigObject();
            String encryptKey = properties.getProperty("encrypt_key");
            if (encryptKey == null || encryptKey.length() < 32) {
                throw new IllegalArgumentException("잘못된 AES 키! (32자리 16진수 필요)");
            }

            byte[] keyBytes = toBytes(encryptKey, 16);
            Key secretKey = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(cryptModeFlag.equals("En") ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, secretKey);

            File inputFile = new File(filePath);
            if (!inputFile.exists()) {
                throw new FileNotFoundException("파일이 존재하지 않음: " + filePath);
            }

            String outputFilePath = inputFile.getParent() + File.separator + "enc_" + inputFile.getName();
            byte[] inputBytes = Files.readAllBytes(inputFile.toPath());

            // **📌 복호화 시 16 바이트 단위인지 확인**
            if (cryptModeFlag.equals("De") && inputBytes.length % 16 != 0) {
                throw new IllegalArgumentException("암호화된 데이터 길이가 16의 배수가 아님! 파일이 손상되었을 수 있음.");
            }

            byte[] outputBytes = cipher.doFinal(inputBytes);
            Files.write(new File(outputFilePath).toPath(), outputBytes);

            resultStr = outputFilePath;
            System.out.println("✅ 암호화/복호화 성공: " + resultStr);

        } catch (Exception e) {
            System.out.println("❌ 암호화/복호화 실패: " + e.getMessage());
            resultStr = "**Error_Msg** " + e.getMessage();
        }
        return resultStr;
    }

    // 📌 **16진수 키 변환**
    private static byte[] toBytes(String hexString, int blockSize) {
        byte[] result = new byte[blockSize];
        byte[] temp = new byte[hexString.length() / 2];

        for (int i = 0; i < temp.length; i++) {
            int index = i * 2;
            temp[i] = (byte) Integer.parseInt(hexString.substring(index, index + 2), 16);
        }

        System.arraycopy(temp, 0, result, 0, Math.min(temp.length, blockSize));
        return result;
    }
    
    
    // dirPath에 있는 .xml 확장자 파일을 TCP 전송 이후, zip_file_success_dirpath 경로로 이동 시킴
    public String moveZipFileDirToSuccessDir(String dirPath, String fileName, String zipDirFlag) throws IOException{
        
        String returnString = "";
        Properties properties = (new configControllerClass()).getConfigObject();
        String zipFileDirPath = properties.getProperty("zip_file_dirpath");
        String successDirPath = properties.getProperty("zip_file_success_dirpath");
        File zipFileDir = new File(zipFileDirPath);
        File successDir = new File(successDirPath);
        

        // 성공 경로 디렉토리 생성
        if (!successDir.exists()) {
            successDir.mkdirs();
        }

        try {
            for (File file : zipFileDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".xml")) {
                    File targetFile = new File(successDir, file.getName());
                    Files.move(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("moveZipFileDirToSuccessDir targetFile: " + targetFile);
                }
            }
        } catch (IOException e) {
            System.err.println("Error while moving .xml files: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        System.out.println("xml 파일 이동 완료\n");
        return successDirPath;
    }
    
    // 📌 **파일을 MD5 해시로 변환 (전송 전/후 검증)**
    public static String calculateMD5(File file) {
        try (InputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] hashBytes = md.digest();

            // **16진수 문자열로 변환**
            StringBuilder hexString = new StringBuilder();
            for (byte hashByte : hashBytes) {
                hexString.append(String.format("%02x", hashByte));
            }
            return hexString.toString();

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
