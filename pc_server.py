import socket
import struct
import subprocess
import mss
import numpy as np

HOST = '127.0.0.1'
PORT = 5555
WIDTH = 1920
HEIGHT = 1080
FPS = 60

def get_ffmpeg_cmd():
    return [
        'ffmpeg',
        '-f', 'rawvideo',
        '-pix_fmt', 'bgr24',
        '-s', f'{WIDTH}x{HEIGHT}',
        '-r', str(FPS),
        '-i', '-',
        '-c:v', 'libx264',      # Dùng CPU x264 (hoặc 'h264_nvenc' nếu có card Nvidia)
        '-preset', 'ultrafast',
        '-tune', 'zerolatency',
        '-g', str(FPS * 2),     # GOP tự động định kỳ
        '-an',
        '-f', 'h264',           # Xuất định dạng luồng H.264 bitstream
        'pipe:1'
    ]

def handle_client(conn):
    print("Điện thoại đã kết nối qua cổng USB! Đang truyền màn hình...")
    ffmpeg_process = subprocess.Popen(
        get_ffmpeg_cmd(),
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL
    )

    try:
        with mss.mss() as sct:
            monitor = sct.monitors[1]
            while True:
                img = sct.grab(monitor)
                frame = np.array(img, dtype=np.uint8)
                frame_bgr = frame[:, :, :3]

                # Gửi frame thô vào encoder FFmpeg
                try:
                    ffmpeg_process.stdin.write(frame_bgr.tobytes())
                    ffmpeg_process.stdin.flush()
                except (BrokenPipeError, IOError):
                    break

                # Đọc luồng H.264 từ ffmpeg stdout và truyền sang Android theo chuẩn Length-Prefixed
                # (Phần này bạn có thể tinh chỉnh bộ đệm đọc NAL Unit từ stdout của ffmpeg)
                
    except (ConnectionResetError, BrokenPipeError):
        print("Điện thoại đã ngắt kết nối.")
    finally:
        try:
            ffmpeg_process.terminate()
            ffmpeg_process.wait()
        except:
            pass
        conn.close()

def main():
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)
    print(f"PC Server đang lắng nghe tại {HOST}:{PORT}...")

    while True:
        conn, addr = server_socket.accept()
        print(f"Thiết bị kết nối từ: {addr}")
        handle_client(conn)

if __name__ == '__main__':
    main()
