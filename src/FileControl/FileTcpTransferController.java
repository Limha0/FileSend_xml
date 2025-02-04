package FileControl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;
import java.io.FileWriter;

import Config.configControllerClass;

public class FileTcpTransferController {
    // TCP 통신을 이용해 내부 수집서버로 수집한 데이터의 압축파일을 전송
    public Object tcpFileSender(String sendFilePath, FileWriter logWriter) {
        
        boolean isConnected = false;
        Object returnObj = "";
        
        for(int i=0; i < 3; i++) {
            if(isConnected) break;

            try {
                logWriter.write("\n[INFO] 전송 시도 " + (i + 1) + "번째 ==================\n");
                
                // 설정 로드 및 로깅
                configControllerClass configObject = new configControllerClass();
                Properties propObject = configObject.getConfigObject();
                String recServerIpAddr = propObject.getProperty("tcp_receiver_ip");
                int recServerPortNum = Integer.parseInt(propObject.getProperty("tcp_receiver_port"));
                
//                logWriter.write("[INFO] 대상 서버 정보: " + recServerIpAddr + ":" + recServerPortNum + "\n");
                
                // 파일 체크 및 로깅
                File sendFile = new File(sendFilePath);
                if(!sendFile.exists()) {
                    returnObj = "File not Exist.";
                    logWriter.write("[ERROR] 파일 없음: " + sendFilePath + "\n");
                    continue;
                }
                
                logWriter.write("[INFO] 전송할 파일: " + sendFilePath + " (크기: " + sendFile.length() + " bytes)\n");
                
                // 소켓 연결 시도
                logWriter.write("[INFO] 서버 연결 시도 중...\n");
                Socket socket = new Socket();
                socket.setSoTimeout(30000); // 읽기 타임아웃 30초
                
                // 연결 전 로컬 주소 정보 로깅
                logWriter.write("[INFO] 로컬 시스템 정보: " + java.net.InetAddress.getLocalHost() + "\n");
                
                // 연결 시도 (타임아웃 5초)
                socket.connect(new java.net.InetSocketAddress(recServerIpAddr, recServerPortNum), 5000);
                
                if (socket.isConnected()) {
                    logWriter.write("[INFO] 서버 연결 성공!\n");
//                    logWriter.write("[INFO] 연결 상세정보:\n");
//                    logWriter.write("- 로컬 주소: " + socket.getLocalAddress() + ":" + socket.getLocalPort() + "\n");
//                    logWriter.write("- 원격 주소: " + socket.getRemoteSocketAddress() + "\n");
                    
                    isConnected = true;
                    
                    // 파일 전송
                    FileInputStream fis = new FileInputStream(sendFile);
                    OutputStream os = socket.getOutputStream();
                    
                    byte[] buffer = new byte[8192];
                    int readBytes;
                    long totalReadBytes = 0;
                    long fileSize = sendFile.length();
                    
                    while((readBytes = fis.read(buffer)) > 0) {
                        os.write(buffer, 0, readBytes);
                        totalReadBytes += readBytes;
                        if(totalReadBytes % (fileSize/10) == 0) { // 10% 단위로 진행상황 로깅
                            logWriter.write(String.format("[INFO] 전송 진행률: %.1f%% (%d/%d bytes)\n", 
                                (totalReadBytes * 100.0 / fileSize), totalReadBytes, fileSize));
                        }
                    }
                    
                    logWriter.write("[INFO] 파일 전송 완료\n");
                    fis.close();
                    os.close();
                    socket.close();
                }

            }catch(UnknownHostException e){
                System.out.println("tcpFileSender UnknownHostException : " + e.getMessage() + " ______ ");
                e.printStackTrace();
                returnObj = e.getMessage();
            }catch(IOException e){
                System.out.println("tcpFileSender IOException : " + e.getMessage() + " ______ ");
                e.printStackTrace();
                returnObj = e.getMessage();
            }catch(Exception e){
                System.out.println("tcpFileSender Exception : " + e.getMessage() + " ______ ");
                e.printStackTrace();
                returnObj = e.getMessage();
            }
            
        }
        
        return isConnected ? "" : returnObj;
    }

}
