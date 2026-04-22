"""
TDMS文件转换工具
将floatdata目录中的TDMS文件转换为JSON格式，供Backend模拟器使用
"""

import os
import json
import numpy as np
from datetime import datetime

try:
    from nptdms import TdmsFile
    print("✓ nptdms库已安装")
except ImportError:
    print("× nptdms库未安装")
    print("请运行: pip install npTDMS")
    exit(1)

# 配置
CONFIG = {
    'input_dir': os.path.join(os.path.dirname(__file__), 'floatdata', 'data'),
    'output_dir': os.path.join(os.path.dirname(__file__), 'floatdata', 'json'),
    'max_samples_per_file': 100000,  # 每个JSON文件最多存储的采样点数
    'sample_rate': 1000000  # 采样率 1MHz
}

def convert_tdms_to_json(tdms_path, output_dir):
    """
    转换单个TDMS文件为JSON格式
    """
    try:
        print(f"\n处理: {os.path.basename(tdms_path)}")
        
        # 读取TDMS文件
        tdms_file = TdmsFile.read(tdms_path)
        
        # 获取所有组和通道
        groups = tdms_file.groups()
        print(f"  找到 {len(groups)} 个数据组")
        
        for group in groups:
            channels = group.channels()
            print(f"  组 '{group.name}': {len(channels)} 个通道")
            
            for channel in channels:
                # 读取通道数据
                data = channel[:]
                
                if data is None or len(data) == 0:
                    print(f"    通道 '{channel.name}': 无数据")
                    continue
                
                print(f"    通道 '{channel.name}': {len(data)} 个采样点")
                
                # 分批保存（如果数据量太大）
                total_samples = len(data)
                num_files = (total_samples + CONFIG['max_samples_per_file'] - 1) // CONFIG['max_samples_per_file']
                
                for file_idx in range(num_files):
                    start_idx = file_idx * CONFIG['max_samples_per_file']
                    end_idx = min((file_idx + 1) * CONFIG['max_samples_per_file'], total_samples)
                    
                    batch_data = data[start_idx:end_idx]
                    
                    # 构建输出文件名
                    base_name = os.path.splitext(os.path.basename(tdms_path))[0]
                    output_name = f"{base_name}_{group.name}_{channel.name}"
                    
                    if num_files > 1:
                        output_name += f"_part{file_idx + 1}"
                    
                    output_name += ".json"
                    output_path = os.path.join(output_dir, output_name)
                    
                    # 构建JSON数据
                    json_data = {
                        'metadata': {
                            'source_file': os.path.basename(tdms_path),
                            'group': group.name,
                            'channel': channel.name,
                            'sample_rate': CONFIG['sample_rate'],
                            'total_samples': len(batch_data),
                            'start_index': start_idx,
                            'end_index': end_idx,
                            'converted_at': datetime.now().isoformat(),
                            'properties': dict(channel.properties) if hasattr(channel, 'properties') else {}
                        },
                        'samples': batch_data.tolist()  # 转换NumPy数组为列表
                    }
                    
                    # 保存JSON文件
                    with open(output_path, 'w', encoding='utf-8') as f:
                        json.dump(json_data, f, indent=2, ensure_ascii=False)
                    
                    print(f"      → 保存: {output_name} ({len(batch_data)} 采样点)")
        
        return True
        
    except Exception as e:
        print(f"  × 转换失败: {str(e)}")
        return False

def main():
    """
    主函数 - 批量转换TDMS文件
    """
    print("=" * 60)
    print("  TDMS to JSON Converter")
    print("=" * 60)
    print()
    
    # 检查输入目录
    if not os.path.exists(CONFIG['input_dir']):
        print(f"× 错误: 输入目录不存在: {CONFIG['input_dir']}")
        return
    
    # 创建输出目录
    if not os.path.exists(CONFIG['output_dir']):
        os.makedirs(CONFIG['output_dir'])
        print(f"✓ 创建输出目录: {CONFIG['output_dir']}")
    
    # 查找所有TDMS文件
    tdms_files = []
    for root, dirs, files in os.walk(CONFIG['input_dir']):
        for file in files:
            if file.endswith('.tdms') and not file.endswith('.tdms_index'):
                tdms_files.append(os.path.join(root, file))
    
    if not tdms_files:
        print(f"× 错误: 未找到TDMS文件在 {CONFIG['input_dir']}")
        return
    
    print(f"\n找到 {len(tdms_files)} 个TDMS文件")
    print()
    
    # 让用户选择转换哪些文件
    print("请选择转换选项:")
    print("1. 转换所有文件")
    print("2. 只转换小文件 (< 50MB)")
    print("3. 转换指定数量的文件")
    print()
    
    choice = input("请选择 (1/2/3): ").strip()
    
    selected_files = []
    
    if choice == '1':
        selected_files = tdms_files
    elif choice == '2':
        selected_files = [f for f in tdms_files if os.path.getsize(f) < 50 * 1024 * 1024]
        print(f"选择了 {len(selected_files)} 个小于50MB的文件")
    elif choice == '3':
        count = int(input("输入要转换的文件数量: "))
        selected_files = tdms_files[:min(count, len(tdms_files))]
    else:
        print("无效选择")
        return
    
    # 开始转换
    print()
    print(f"开始转换 {len(selected_files)} 个文件...")
    print("=" * 60)
    
    success_count = 0
    fail_count = 0
    
    for tdms_file in selected_files:
        if convert_tdms_to_json(tdms_file, CONFIG['output_dir']):
            success_count += 1
        else:
            fail_count += 1
    
    print()
    print("=" * 60)
    print(f"转换完成!")
    print(f"  成功: {success_count}")
    print(f"  失败: {fail_count}")
    print(f"  输出目录: {CONFIG['output_dir']}")
    print("=" * 60)

if __name__ == '__main__':
    main()
