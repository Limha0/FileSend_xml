package FileControl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Properties;
import java.io.FileWriter;

import Config.configControllerClass;

public class FileTcpTransferController {
    public Object tcpFileSender(String sendFilePath, FileWriter logWriter) {
        boolean isConnected = false;
        Object returnObj = "";

        for (int i = 0; i < 3; i++) {
            if (isConnected) break;

            try {
                logWriter.write("\n[INFO] 전송 시도 " + (i + 1) + "번째 ==================\n");

                
                Properties properties = (new configControllerClass()).getConfigObject();
                
                //로컬에서 테스트할 경우
//                configControllerClass configObject = new configControllerClass();
//                Properties propObject = configObject.getConfigObject();
                
                String recServerIpAddr = properties.getProperty("tcp_receiver_ip");
                int recServerPortNum = Integer.parseInt(properties.getProperty("tcp_receiver_port"));

                File sendFile = new File(sendFilePath);
                if (!sendFile.exists()) {
                    returnObj = "File not Exist.";
                    logWriter.write("[ERROR] 파일 없음: " + sendFilePath + "\n");
                    continue;
                }

                long fileSize = sendFile.length();
                logWriter.write("[INFO] 전송할 파일 크기: " + fileSize + " bytes\n");
                logWriter.write("[INFO] 전송할 파일 해시(MD5): " + calculateMD5(sendFilePath) + "\n");

                try (Socket socket = new Socket(recServerIpAddr, recServerPortNum);
                    FileInputStream fis = new FileInputStream(sendFile);
                    OutputStream os = socket.getOutputStream()) {
                    
                    // 소켓 연결 성공
                    logWriter.write("[INFO] 소켓 연결 성공: " + recServerIpAddr + ":" + recServerPortNum + "\n");
                
                    // ✅ **소켓 타임아웃 설정 (10초)**
//                    socket.setSoTimeout(10000);

                   // 소켓 송신 버퍼 크기 설정 (64KB)
                   socket.setSendBufferSize(64 * 1024);

                   byte[] buffer = new byte[16384]; // 16KB 버퍼
                   int readBytes;
                   long totalReadBytes = 0;
                   long lastLoggedBytes = 0;
//                   long startTime = System.currentTimeMillis();
                   long logInterval = Math.max(fileSize / 10, 16384); // ✅ 10% 단위 또는 최소 16KB마다 로그 출력

                   logWriter.write("[INFO] 파일 전송 시작...\n");

                   while ((readBytes = fis.read(buffer)) > 0) {
                       os.write(buffer, 0, readBytes);
                       os.flush(); // 각 청크를 즉시 전송
                       totalReadBytes += readBytes;

                       // 진행 로그 출력 (10% 단위)
//                       if (fileSize > 0 && totalReadBytes % (fileSize / 10) == 0) {
//                           logWriter.write(String.format("[INFO] 전송 진행률: %.1f%% (%d/%d bytes)\n",
//                               (totalReadBytes * 100.0 / fileSize), totalReadBytes, fileSize));
//                       }
                       
                    // ✅ **전송된 데이터 크기가 logInterval 이상 증가할 때마다 로그 출력**
                       if (totalReadBytes - lastLoggedBytes >= logInterval) {
                           double progress = (totalReadBytes * 100.0) / fileSize;
                           logWriter.write(String.format("[INFO] 전송 진행률: %.1f%% (%d/%d bytes)\n", progress, totalReadBytes, fileSize));
                           lastLoggedBytes = totalReadBytes;
                       }
                   }
                   
                   // 데이터 강제 전송 및 송신 종료 신호
                   os.flush(); // 버퍼에 남은 데이터 강제 전송
                   socket.shutdownOutput(); // 송신 종료 ,수신 측이 정확히 EOF를 인식하도록 신호 전송
                   
                   logWriter.write("[INFO] 파일 전송 완료\n");
                   
                   logWriter.write("[INFO] 파일 전송 성공 (총 크기: " + totalReadBytes + " bytes)\n");

//                   전송 속도를 모니터링하고 싶을 때
//                   long endTime = System.currentTimeMillis();
//                   double elapsedTime = (endTime - startTime) / 1000.0;
//                   double transferSpeed = (totalReadBytes / 1024.0) / elapsedTime;

//                   logWriter.write(String.format("[INFO] 파일 전송 완료 (총 전송 크기: %d bytes, 전송 속도: %.2f KB/s)\n", 
//                           totalReadBytes, transferSpeed));

                   isConnected = true;
               }

            }catch(UnknownHostException e){
                System.out.println("[ERROR] 호스트 이름을 확인할 수 없습니다. : " + e.getMessage() + " ______ ");
                e.printStackTrace();
                returnObj = e.getMessage();
            }catch (SocketException e) {
                System.out.println("[ERROR] 소켓 예외 발생: " + e.getMessage() + " ______ ");
                e.printStackTrace();
                returnObj = e.getMessage();
            }catch(IOException e){
                System.out.println("[ERROR] I/O 예외 발생: " + e.getMessage() + " ______ ");
                e.printStackTrace();
                returnObj = e.getMessage();
            }catch(Exception e){
                System.out.println("[ERROR] 일반 예외 발생: "  + e.getMessage() + " ______ ");
                e.printStackTrace();
                returnObj = e.getMessage();
            }
        }

        return isConnected ? "" : returnObj;
    }

    private static String calculateMD5(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }

            byte[] digest = md.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            return "MD5 계산 실패: " + e.getMessage();
        }
    }
}
