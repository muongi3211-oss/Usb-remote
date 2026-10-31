import socket
import struct
import mss
import cv2
import numpy as np

HOST = '127.0.0.1'
PORT = 5555

def main():
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)
    print(f"PC Server đang chờ kết nối từ điện thoại tại cổng {PORT}...")

    while True:
        conn, addr = server_socket.accept()
        print(f"Điện thoại đã cắm cáp và kết nối thành công: {addr}")
        
        try:
            with mss.mss() as sct:
                monitor = sct.monitors[1] # Lấy màn hình chính
                while True:
                    # 1. Chụp màn hình siêu tốc bằng mss
                    img = sct.grab(monitor)
                    frame = np.array(img)
                    frame_bgr = cv2.cvtColor(frame, cv2.COLOR_BGRA2BGR)
                    
                    # 2. Nén frame thành định dạng JPEG (Chất lượng 80% tối ưu băng thông USB)
                    success, encoded_image = cv2.imencode('.jpg', frame_bgr, [int(cv2.IMWRITE_JPEG_QUALITY), 80])
                    if not success:
                        continue
                    
                    data = encoded_image.tobytes()
                    
                    # 3. Gửi theo chuẩn Length-Prefixed (4 byte kích thước + dữ liệu byte)
                    header = struct.pack(">I", len(data))
                    conn.sendall(header + data)
                    
        except (ConnectionResetError, BrokenPipeError):
            print("Điện thoại đã ngắt kết nối cáp USB. Đang chờ kết nối lại...")
            conn.close()

if __name__ == '__main__':
    main()
