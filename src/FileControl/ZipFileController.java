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
    public String createZipFileUsingDirPath(String dirPath, String fileName, String zipDirFlag){
        String returnString = "";
        
        Properties properties = (new configControllerClass()).getConfigObject();
        String rootDirPath =  properties.getProperty("zip_file_dirpath");
        String ZipDirPath = properties.getProperty("zip_file_send_dirpath");
        String readDirPath = dirPath;

        try{
            if(readDirPath.equals("")){
                readDirPath = rootDirPath;
            }
            
            File readFileDir = new File(readDirPath);
            File writeFileDir = new File(rootDirPath);
            File zipDir = new File(ZipDirPath); 
            
            // rootDirPath 생성
            if(!writeFileDir.exists()){
                writeFileDir.mkdirs();
            }
            
            // zip_file_send_dirpath 생성
            if (!zipDir.exists()) {
                zipDir.mkdirs();
            }
            
            
            String createZipFileNameWithDirPath = ZipDirPath + fileName + ".zip";
            FileOutputStream createZipFile = new FileOutputStream(createZipFileNameWithDirPath);
            ZipOutputStream zipFleOutputStream = new ZipOutputStream(createZipFile);
            
            // 날짜 포맷 설정 (예: 20241113 형식으로 날짜를 추가)
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            String todayDate = dateFormat.format(new Date());
            
            returnString = recursiveCreateZipFile(zipFleOutputStream, readFileDir, "",todayDate);
            
            zipFleOutputStream.flush();
            createZipFile.flush();
            zipFleOutputStream.close();
            createZipFile.close();
            
            if(returnString.equals("")){
                returnString = createZipFileNameWithDirPath;
            }
            
        }catch(FileNotFoundException e){
            System.out.println("createZipFileUsingDirPath FileNotFoundException : " + e.getMessage() + " ______ ");
            e.printStackTrace();
            returnString = "**Error_Msg** " + e.getMessage();
        }catch(Exception e){
            System.out.println("createZipFileUsingDirPath Exception : " + e.getMessage() + " ______ ");
            e.printStackTrace();
            returnString = "**Error_Msg** " + e.getMessage();
        }finally {
            
        }
        
        return returnString;
    }
    // Zip 파일 생성
    public String recursiveCreateZipFile(ZipOutputStream zos, File readDir, String relativeDirPath, String todayDate){
        String resultStr = "";
        
        try{
            if(readDir == null || !readDir.exists()){
                return "";
            }
            
            
            // 현재 디렉토리의 파일들만 처리
            File[] files = readDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    // 디렉토리가 아닌 파일이면서 XML 파일인 경우만 처리
                    if (!file.isDirectory() && file.getName().endsWith(".xml")) {
                        byte[] dataBuffer = new byte[2014];
                        FileInputStream fis = new FileInputStream(file);
                        zos.putNextEntry(new ZipEntry(relativeDirPath + file.getName()));
                        int length;
                        while ((length = fis.read(dataBuffer)) > 0) {
                            zos.write(dataBuffer, 0, length);
                        }
                        zos.closeEntry();
                        fis.close();
                    }
                }
            }
            
        }catch(FileNotFoundException e){
            System.out.println("recursiveCreateZipFile FileNotFoundException : " + e.getMessage() + " ______ ");
            e.printStackTrace();
            resultStr = "**Error_Msg** " + e.getMessage();
        }catch(Exception e){
            System.out.println("recursiveCreateZipFile Exception : " + e.getMessage() + " ______ ");
            e.printStackTrace();
            resultStr = "**Error_Msg** " + e.getMessage();
        }finally {
            
        }
        
        return resultStr;

    }
    // Zip 파일 암호화 및 복호화
    public String encryptionZipFile(String cryptModeFlag, String filePath){
       
        String resultStr = "";
        
        InputStream is = null;
        OutputStream os  = null;
        
        try {
            
            // Properties 파일에서 암호화 키 가져오기
            Properties properties = (new configControllerClass()).getConfigObject();
            String encryptkey = properties.getProperty("encrypt_key");
            String encryptAlgorothm =  properties.getProperty("encrypt_algorothm");
            
            // 날짜 포맷 설정 (예: 20241113 형식으로 날짜를 추가)
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            String todayDate = dateFormat.format(new Date());
            
            String encryptFileName =  properties.getProperty("encrypt_filename")+"_"+todayDate; //국민신문고_enc_20241113
//            String zipFileName = "국민신문고_" + todayDate;
            
            int cryptModeCode = 0;
            if(cryptModeFlag.equals("En")){
                //ENCRYPT_MODE
                cryptModeCode = Cipher.ENCRYPT_MODE;
            }
            else{ // cryptModeFlag : De
                cryptModeCode = Cipher.DECRYPT_MODE;
                encryptFileName += "_de";
            }
            // 키 생성 로직 개선
            byte[] keyBytes = toBytes(encryptkey, 16);
            Key secretKey = new SecretKeySpec(keyBytes, "AES");
            
            // Cipher 초기화
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(cryptModeCode, secretKey);
            
            File readZipFile = new File(filePath);
            String createEncryptFileStr = readZipFile.getParent() + File.separator + encryptFileName + ".zip";
            
            System.out.println("encryptionZipFile readZipFile: " + readZipFile);
            System.out.println("encryptionZipFile createEncryptFileStr: " + createEncryptFileStr);
            
            // 파일 전체를 메모리에 로드
            byte[] inputBytes = new byte[(int) readZipFile.length()];
            try (FileInputStream fis = new FileInputStream(readZipFile)) {
                fis.read(inputBytes);
            }
            
            // 암호화/복호화 수행
            byte[] outputBytes;
            if (cryptModeCode == Cipher.ENCRYPT_MODE) {
                // 암호화 시 패딩 자동 처리
                outputBytes = cipher.doFinal(inputBytes);
            } else {
                // 복호화 시 입력 길이 검증
                if (inputBytes.length % 16 != 0) {
                    throw new IllegalArgumentException("암호화된 데이터 길이가 16의 배수가 아닙니다: " + inputBytes.length);
                }
                outputBytes = cipher.doFinal(inputBytes);
            }
            
            // 결과 파일 쓰기
            try (FileOutputStream fos = new FileOutputStream(createEncryptFileStr)) {
                fos.write(outputBytes);
                fos.flush();
            }
            
            resultStr = createEncryptFileStr;
            System.out.println("encryptionZipFile resultStr: " + resultStr);
            
        } catch (Exception e) {
            System.out.println("encryptionZipFile Exception : " + e.getMessage() + " ______ ");
            e.printStackTrace();
            resultStr = "**Error_Msg** " + e.getMessage();
        }
        
        return resultStr;
    }
    
    public static byte[] toBytes(String digits, int blockSize) throws IllegalArgumentException, NumberFormatException {
        if (digits == null) {
            throw new IllegalArgumentException("입력 문자열이 null입니다.");
        }
        
        // 16진수 문자열을 바이트 배열로 변환
        byte[] result = new byte[blockSize];
        byte[] temp = new byte[digits.length() / 2];
        
        for (int i = 0; i < temp.length; i++) {
            int index = i * 2;
            int j = Integer.parseInt(digits.substring(index, index + 2), 16);
            temp[i] = (byte) j;
        }
        
        // 결과 배열의 크기를 16바이트로 맞춤
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
}
