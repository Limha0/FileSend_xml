package Main;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
            
         // 로컬 테스트 (프로젝트 내에 있는 config 사용)
//            configControllerClass configObject = new configControllerClass();
//            Properties properties = configObject.getConfigObject();
            
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
            
            // XML 파일이 있는 디렉토리 가져오기
            String xmlFileDir = properties.getProperty("zip_file_dirpath"); // XML 파일이 있는 폴더
            File dir = new File(xmlFileDir);
            
            if (!dir.exists() || !dir.isDirectory()) {
                logWriter.write("[" + fullStartTime + "] 오류: XML 파일 디렉토리가 존재하지 않음.\n");
                return;
            }
            
            // XML 파일 목록 가져오기
            File[] xmlFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));

            if (xmlFiles == null || xmlFiles.length == 0) {
                logWriter.write("[" + fullStartTime + "] 전송할 XML 파일이 없음.\n");
                return;
            }
            
            // TCP 파일 전송
            logWriter.write("[" + fullStartTime + "] TCP 파일 전송 시작\n");
            System.out.println("[" + fullStartTime + "] TCP 파일 전송 시작\n");
            
            // XML 파일을 하나씩 TCP 전송
            for (File xmlFile : xmlFiles) {
                String filePath = xmlFile.getAbsolutePath();

                logWriter.write("[" + fullStartTime + "] 전송할 파일: " + filePath + "\n");
                System.out.println("[" + fullStartTime + "] 전송할 파일: " + filePath + "\n");

                // ✅ TCP 전송 메서드 호출 (파일명 먼저 전송하도록 수정)
                Object tcpTransferResult = sendFileWithFileName(xmlFile, logWriter);

                if (tcpTransferResult.toString().isEmpty()) {
                    logWriter.write("[" + fullStartTime + "] TCP 파일 전송 완료: " + filePath + "\n");
                } else {
                    logWriter.write("[" + fullStartTime + "] TCP 파일 전송 중 오류 발생: " + tcpTransferResult.toString() + "\n");
                    logWriter.write("[" + fullStartTime + "] 프로그램 종료: 파일 이동 작업을 생략합니다.\n");
                    throw new Exception("TCP 파일 전송 실패: " + tcpTransferResult.toString());
                }
            }

            // XML 파일 이동 (전송 성공한 경우)
            logWriter.write("[" + fullStartTime + "] XML 파일 이동 시작\n");
            System.out.println("[" + fullStartTime + "] XML 파일 이동 시작\n");
            boolean isMoveSuccess = false;

            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String successDirPath = properties.getProperty("zip_file_success_dirpath");
                    for (File xmlFile : xmlFiles) {
                        File destFile = new File(successDirPath, xmlFile.getName());
                        if (xmlFile.renameTo(destFile)) {
                            logWriter.write("[" + fullDateFormat.format(new Date()) + "] XML 파일 이동 완료: " + destFile.getAbsolutePath() + "\n");
                            System.out.println("[" + fullDateFormat.format(new Date()) + "] XML 파일 이동 완료: " + destFile.getAbsolutePath() + "\n");
                        } else {
                            throw new IOException("파일 이동 실패: " + xmlFile.getAbsolutePath());
                        }
                    }
                    isMoveSuccess = true;
                    break;
                } catch (Exception e) {
                    logWriter.write("[" + fullDateFormat.format(new Date()) + "] 파일 " + attempt + "차 재시도\n");
                    if (attempt == 3) {
                        logWriter.write("[" + fullDateFormat.format(new Date()) + "] XML 파일 이동 실패\n");
                    }
                    Thread.sleep(30000); // 30초 대기 후 재시도
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
    

    private static Object sendFileWithFileName(File xmlFile, FileWriter logWriter) {
        Properties properties = (new configControllerClass()).getConfigObject();
        String serverIp = properties.getProperty("tcp_receiver_ip");
        int serverPort = Integer.parseInt(properties.getProperty("tcp_receiver_port"));

//        try (Socket socket = new Socket(serverIp, serverPort)) {
        try (Socket socket = new Socket()) {
            // 1. 연결 타임아웃 설정
//            socket.connect(new InetSocketAddress(serverIp, serverPort), 30000); //30초
            socket.connect(new InetSocketAddress(serverIp, serverPort), 60000); //60초
            
            socket.setSendBufferSize(16 * 1024); // 16KB 송신 버퍼
//            socket.setSoTimeout(300000); // 타임아웃 300초 (5분)
            socket.setSoTimeout(300000); // 타임아웃 300초 (10분)
            socket.setKeepAlive(true);   // Keep-Alive 설정
            
            // 1. 파일명 전송
            byte[] fileNameBytes = xmlFile.getName().getBytes(StandardCharsets.UTF_8);
//            socket.getOutputStream().write(fileNameBytes.length); // 파일명 길이 전송
            socket.getOutputStream().write(ByteBuffer.allocate(4).putInt(fileNameBytes.length).array()); // 파일명 길이 전송
            socket.getOutputStream().write(fileNameBytes); // 파일명 전송
            socket.getOutputStream().flush();

            // 2. 파일 크기 전송
            long fileSize = xmlFile.length();
            socket.getOutputStream().write(ByteBuffer.allocate(8).putLong(fileSize).array()); // 파일 크기 전송
            socket.getOutputStream().flush();

            // 4. 파일 데이터 전송
            try (FileInputStream fis = new FileInputStream(xmlFile);
                 BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream())) {
                
                byte[] buffer = new byte[1024];
                int bytesRead;
                long totalSentBytes = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    totalSentBytes += bytesRead;
                    
                 // **송신 진행률 로그 출력**
                    double progress = (totalSentBytes * 100.0) / fileSize;
                    logWriter.write(String.format("[INFO] 전송 진행률: %.2f%% (%d / %d bytes)\n", progress, totalSentBytes, fileSize));
                    System.out.println(String.format("[INFO] 전송 진행률: %.2f%% (%d / %d bytes)", progress, totalSentBytes, fileSize));
                    
                 // 네트워크 부하 방지
//                    Thread.sleep(10); // 10ms 대기
                }

                bos.flush();

                if (totalSentBytes != fileSize) {
                    throw new IOException("파일 크기 불일치!");
                }

                logWriter.write(String.format("[INFO] 파일 전송 완료 (크기: %d bytes)\n", totalSentBytes));
                System.out.println(String.format("[INFO] 파일 전송 완료 (크기: %d bytes)\n", totalSentBytes));
                return "";
            }
        } catch (Exception e) {
            try {
                logWriter.write("[ERROR] 파일 전송 실패: " + e.getMessage() + "\n");
            } catch (IOException logEx) {
                logEx.printStackTrace();
            }
            return "파일 전송 오류: " + e.getMessage();
        }
    }
}
