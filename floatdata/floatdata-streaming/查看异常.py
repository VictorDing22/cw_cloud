#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""实时查看异常检测结果"""

from kafka import KafkaConsumer
import json
from datetime import datetime

print("\n" + "="*60)
print("  实时查看异常检测结果")
print("="*60 + "\n")
print("连接到 Kafka...")

try:
    consumer = KafkaConsumer(
        'anomaly-detection-result',
        bootstrap_servers='localhost:9092',
        auto_offset_reset='earliest',
        value_deserializer=lambda x: json.loads(x.decode('utf-8'))
    )
    
    print("✓ 已连接！正在监听异常结果...")
    print("按 Ctrl+C 停止\n")
    print("-"*60)
    
    count = 0
    anomaly_count = 0
    
    for message in consumer:
        result = message.value
        count += 1
        
        if result.get('isAnomaly', False):
            anomaly_count += 1
            timestamp = datetime.fromtimestamp(result['timestamp']/1000).strftime('%H:%M:%S')
            
            print(f"\n⚠️  异常 #{anomaly_count} (总处理: {count})")
            print(f"   时间: {timestamp}")
            print(f"   传感器: {result.get('sensorId', 'N/A')}")
            print(f"   位置: {result.get('location', 'N/A')}")
            print(f"   异常分数: {result.get('anomalyScore', 0):.4f}")
            print(f"   异常类型: {result.get('anomalyType', 'N/A')}")
            print(f"   能量水平: {result.get('energyLevel', 0):.4f}")
            print(f"   频率得分: {result.get('frequencyScore', 0):.4f}")
            print("-"*60)
        else:
            # 每100条正常数据显示一次统计
            if count % 100 == 0:
                rate = (anomaly_count / count) * 100 if count > 0 else 0
                print(f"✓ 已处理 {count} 条，异常 {anomaly_count} 条 (异常率: {rate:.2f}%)")

except KeyboardInterrupt:
    print("\n\n停止监听")
    print(f"总共处理: {count} 条")
    print(f"检测异常: {anomaly_count} 条")
    if count > 0:
        print(f"异常率: {(anomaly_count/count)*100:.2f}%")
except Exception as e:
    print(f"\n错误: {e}")
    print("\n请确保:")
    print("1. Kafka正在运行 (localhost:9092)")
    print("2. 已安装 kafka-python: pip install kafka-python")
