"""
TDMS 文件上传解析 API
支持前端上传 TDMS 文件，解析后返回信号数据
端口: 3004
"""

import os
import sys
import json
import tempfile
from pathlib import Path
from http.server import HTTPServer, BaseHTTPRequestHandler
import cgi

try:
    from nptdms import TdmsFile
    print("[OK] nptdms 已加载")
except ImportError:
    print("[ERROR] 请安装 nptdms: pip install npTDMS")
    sys.exit(1)

import numpy as np

CONFIG = {
    'http_port': 3004,
    'max_file_size': 100 * 1024 * 1024,  # 100MB
    'max_samples_return': 500000,  # 最多返回50万样本
}


def parse_tdms_file(file_path):
    """解析 TDMS 文件，返回所有通道数据"""
    try:
        tdms = TdmsFile.read(file_path)
        result = {
            'success': True,
            'channels': [],
            'totalSamples': 0
        }
        
        for group in tdms.groups():
            group_name = group.name
            for channel in group.channels():
                channel_name = channel.name
                data = np.array(channel[:], dtype=np.float32)
                
                # 限制返回的样本数
                if len(data) > CONFIG['max_samples_return']:
                    # 降采样
                    factor = len(data) // CONFIG['max_samples_return']
                    data = data[::factor]
                
                result['channels'].append({
                    'group': group_name,
                    'name': channel_name,
                    'sampleCount': len(data),
                    'samples': data.tolist(),
                    'min': float(np.min(data)),
                    'max': float(np.max(data)),
                    'mean': float(np.mean(data)),
                    'std': float(np.std(data))
                })
                result['totalSamples'] += len(data)
        
        return result
    except Exception as e:
        return {
            'success': False,
            'error': str(e)
        }


class TDMSUploadHandler(BaseHTTPRequestHandler):
    """处理 TDMS 文件上传"""
    
    def do_POST(self):
        if self.path == '/upload':
            self.handle_upload()
        elif self.path == '/parse':
            self.handle_parse()
        else:
            self.send_error(404)
    
    def do_GET(self):
        if self.path == '/health':
            self.send_json({'status': 'ok', 'service': 'tdms-upload-api'})
        else:
            self.send_error(404)
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def handle_upload(self):
        """处理文件上传 (multipart/form-data)"""
        try:
            content_type = self.headers.get('Content-Type', '')
            
            if 'multipart/form-data' in content_type:
                # 解析 multipart 数据
                form = cgi.FieldStorage(
                    fp=self.rfile,
                    headers=self.headers,
                    environ={
                        'REQUEST_METHOD': 'POST',
                        'CONTENT_TYPE': content_type
                    }
                )
                
                if 'file' not in form:
                    self.send_json({'success': False, 'error': '未找到文件'})
                    return
                
                file_item = form['file']
                if not file_item.filename:
                    self.send_json({'success': False, 'error': '文件名为空'})
                    return
                
                # 保存到临时文件
                with tempfile.NamedTemporaryFile(delete=False, suffix='.tdms') as tmp:
                    tmp.write(file_item.file.read())
                    tmp_path = tmp.name
                
                # 解析 TDMS
                result = parse_tdms_file(tmp_path)
                result['filename'] = file_item.filename
                
                # 删除临时文件
                os.unlink(tmp_path)
                
                self.send_json(result)
            else:
                self.send_json({'success': False, 'error': '请使用 multipart/form-data 格式'})
                
        except Exception as e:
            self.send_json({'success': False, 'error': str(e)})
    
    def handle_parse(self):
        """处理 base64 编码的文件数据"""
        try:
            content_length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(content_length).decode('utf-8')
            data = json.loads(body)
            
            if 'fileData' not in data:
                self.send_json({'success': False, 'error': '缺少 fileData'})
                return
            
            import base64
            file_bytes = base64.b64decode(data['fileData'])
            
            # 保存到临时文件
            with tempfile.NamedTemporaryFile(delete=False, suffix='.tdms') as tmp:
                tmp.write(file_bytes)
                tmp_path = tmp.name
            
            # 解析 TDMS
            result = parse_tdms_file(tmp_path)
            result['filename'] = data.get('filename', 'unknown.tdms')
            
            # 删除临时文件
            os.unlink(tmp_path)
            
            self.send_json(result)
            
        except Exception as e:
            self.send_json({'success': False, 'error': str(e)})
    
    def send_json(self, data):
        response = json.dumps(data)
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Content-Length', len(response))
        self.end_headers()
        self.wfile.write(response.encode('utf-8'))
    
    def log_message(self, format, *args):
        print(f"[API] {args[0]}")


def main():
    print("=" * 60)
    print("  TDMS 文件上传解析 API")
    print("=" * 60)
    print(f"  端口: {CONFIG['http_port']}")
    print(f"  最大文件: {CONFIG['max_file_size'] // 1024 // 1024}MB")
    print("  POST /upload - 上传 TDMS 文件 (multipart/form-data)")
    print("  POST /parse  - 解析 base64 编码的 TDMS 数据")
    print("  GET  /health - 健康检查")
    print("=" * 60)
    
    server = HTTPServer(('0.0.0.0', CONFIG['http_port']), TDMSUploadHandler)
    print(f"\n[OK] 服务启动在 http://0.0.0.0:{CONFIG['http_port']}")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[停止]")


if __name__ == '__main__':
    main()
