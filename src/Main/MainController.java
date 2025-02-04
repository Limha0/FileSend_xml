package Main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import Config.configControllerClass;
import FileControl.FileDeleteController;
import FileControl.FileTcpTransferController;
import FileControl.ZipFileController;


public class MainController {

    public static void main(String[] args) {
        FileWriter logWriter = null;
        
        try {
            
            Properties properties = (new configControllerClass()).getConfigObject();
            
            // 날짜 포맷 설정 (예: 20241113 형식으로 날짜를 추가)
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
            DateFormat monthFormat = new SimpleDateFormat("yyyyMM");
            DateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            Date now = new Date();
            String currentMonth = monthFormat.format(now); // 현재 월 (예: 202411)
            String todayDate = dateFormat.format(new Date());
            String currentTime = timeFormat.format(new Date());
            String fullStartTime = fullDateFormat.format(now); // 현재 날짜 및 시간 (예: 2024-11-13 15:30:45)

            
            
            // 로그 경로 및 파일 생성
            String baseLogDirPath = properties.getProperty("log_dirpath");
            String monthlyLogDirPath = baseLogDirPath + File.separator + currentMonth; // 월별 디렉토리
            File monthlyLogDir = new File(monthlyLogDirPath);
            if (!monthlyLogDir.exists()) {
                monthlyLogDir.mkdirs(); // 월별 로그 폴더 생성
            }

            String logFilePath = monthlyLogDirPath + File.separator + currentMonth + "_log.txt"; // 월별 로그 파일 경로
            logWriter = new FileWriter(logFilePath, true); // 로그 파일에 내용 추가

            // 프로그램 시작 로그 작성
            logWriter.write("[" + fullStartTime + "] ------------------------------------------\n");
            logWriter.write("[" + fullStartTime + "] 프로그램 시작\n");
            logWriter.write("[" + fullStartTime + "] ------------------------------------------\n");
            
            ZipFileController zipFileControlObj = new ZipFileController();

            // zip_file_dirpath 위치에 있는 파일을 zipFileName이라는 이름의 파일로 압축
            String zipFileName = "국민신문고_" + todayDate;
            String fileZippingResultStr = zipFileControlObj.createZipFileUsingDirPath(properties.getProperty("zip_file_dirpath"), zipFileName, "");
            if(fileZippingResultStr.contains(" **Error_Msg** ")){
                logWriter.write("[" + fullStartTime  + "] 압축 중 오류 발생 : " + fileZippingResultStr + "\n");
                return ;
            }
            logWriter.write("[" + fullStartTime  + "] 압축 성공: " + fileZippingResultStr.replace("/", "\\") + "\n");

            
            // 파일 암호화
            String fileEncryptionPath = zipFileControlObj.encryptionZipFile("En", fileZippingResultStr);
            File encryptedFile = new File(fileEncryptionPath);
            
            if (encryptedFile.exists()) {
                logWriter.write("[" + fullStartTime  + "] 암호화된 파일 경로: " + fileEncryptionPath + "\n");
                logWriter.write("[" + fullStartTime  + "] 암호화된 파일 크기 (바이트): " + encryptedFile.length() + "\n");
            } else {
                logWriter.write("[" + fullStartTime  + "] MainControl Error: Encrypted file not found or encryption failed.\n");
                return;
            }
            
            
            // TCP 파일 전송
            logWriter.write("[" + fullStartTime + "] TCP 파일 전송 시작\n");
            Object tcpTransferResult = (new FileTcpTransferController()).tcpFileSender(fileEncryptionPath, logWriter);
            
            // TCP 전송 성공/실패 여부 확인
            if (tcpTransferResult.toString().isEmpty()) {
                // TCP 전송 성공 시 파일 이동 로직 실행
                logWriter.write("[" + fullStartTime + "] TCP 파일 전송 결과: 완료\n");
            } else {
                // TCP 전송 실패 시 로그 작성 및 예외 발생
                logWriter.write("[" + fullStartTime + "] TCP 파일 전송 중 오류 발생: " + tcpTransferResult.toString() + "\n");
                logWriter.write("[" + fullStartTime + "] 프로그램 종료: 파일 이동 작업을 생략합니다.\n");
                throw new Exception("TCP 파일 전송 실패: " + tcpTransferResult.toString());
            }

            
            //전송 성공 후, .xml 파일을 zip_file_success_dirpath로 이동
            logWriter.write("[" + fullStartTime + "] XML 파일 이동 시작\n");
            boolean isMoveSuccess = false;

            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String successDirPath = zipFileControlObj.moveZipFileDirToSuccessDir(properties.getProperty("zip_file_dirpath"), zipFileName, "");
                    logWriter.write("[" + fullDateFormat.format(new Date()) + "] XML 파일 이동 완료: " + successDirPath + "\n");
                    isMoveSuccess = true;
                    break;
                } catch (Exception e) {
                    logWriter.write("[" + fullDateFormat.format(new Date()) + "] 파일 " + attempt + "차 재시도\n");
                    if (attempt == 3) {
                        logWriter.write("[" + fullDateFormat.format(new Date()) + "] XML 파일 이동 실패\n");
                    }
                    Thread.sleep(30000); // 30초 대기
                }
            }

            if (!isMoveSuccess) {
                throw new Exception("파일 이동 실패: 모든 재시도가 실패했습니다.");
            }
            
 
            
            //전송 성공 후, zip_file_dirpath(xml이 있던 경로)/ zip_file_send_dirpath(xml을 압축한 경로) 위치에 있는 파일 삭제
//            FileDeleteController fileDeleteControlObj = new FileDeleteController();
//            fileDeleteControlObj.deleteFilesInDirectory(properties.getProperty("zip_file_dirpath"));
//            fileDeleteControlObj.deleteFilesInDirectory(properties.getProperty("zip_file_send_dirpath"));
//            logWriter.write("[" + currentTime + "] 모든 파일 삭제 완료\n");
            
        } catch (Exception e) {
            try {
                if (logWriter != null) {
                    logWriter.write("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
                            + "] MainControl Exception : " + e.getMessage() + "\n");
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            e.printStackTrace();
        } finally {
            try {
                if (logWriter != null) {
                    logWriter.write("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())+ "] 프로그램 종료\n");
//                    logWriter.write("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
//                            + "] finally IN ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
                    logWriter.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
