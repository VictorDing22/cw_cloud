"""
Kafka测试工具集
整合了连接测试、发送测试和快速测试功能
"""

import sys
import json
import time
from kafka import KafkaProducer, KafkaConsumer
from kafka.errors import KafkaError

def test_connection():
    """测试Kafka连接"""
    print("\n" + "="*50)
    print("  测试1: Kafka连接")
    print("="*50)
    
    try:
        producer = KafkaProducer(
            bootstrap_servers=['localhost:9092'],
            api_version=(0, 10, 1),
            request_timeout_ms=5000
        )
        print("[OK] Kafka连接成功")
        producer.close()
        return True
    except Exception as e:
        print(f"[ERROR] Kafka连接失败: {e}")
        return False

def test_send_messages(topic='sample-input', count=10):
    """测试发送消息"""
    print("\n" + "="*50)
    print(f"  测试2: 发送{count}条消息到 {topic}")
    print("="*50)
    
    try:
        producer = KafkaProducer(
            bootstrap_servers=['localhost:9092'],
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            api_version=(0, 10, 1),
            acks=1,
            request_timeout_ms=10000
        )
        
        success_count = 0
        for i in range(count):
            msg = {
                'deviceId': f'test-device-{i}',
                'timestamp': int(time.time() * 1000),
                'sampleRate': 2000000,
                'samples': [float(x) for x in range(100)],
                'test': True,
                'index': i
            }
            
            try:
                future = producer.send(topic, value=msg)
                future.get(timeout=10)
                success_count += 1
                if (i + 1) % 5 == 0:
                    print(f"  发送 {i+1}/{count}")
            except Exception as e:
                print(f"  [ERROR] 消息 #{i+1} 发送失败: {e}")
        
        producer.close()
        print(f"\n[OK] 成功发送 {success_count}/{count} 条消息")
        return success_count == count
        
    except Exception as e:
        print(f"[ERROR] 发送测试失败: {e}")
        return False

def test_consume_messages(topic='sample-input', timeout=5):
    """测试消费消息"""
    print("\n" + "="*50)
    print(f"  测试3: 从 {topic} 读取消息")
    print("="*50)
    
    try:
        consumer = KafkaConsumer(
            topic,
            bootstrap_servers=['localhost:9092'],
            auto_offset_reset='latest',
            consumer_timeout_ms=timeout * 1000,
            value_deserializer=lambda m: json.loads(m.decode('utf-8'))
        )
        
        count = 0
        for message in consumer:
            count += 1
            print(f"  收到消息 #{count}:")
            print(f"    分区: {message.partition}")
            print(f"    偏移量: {message.offset}")
            if message.value and 'deviceId' in message.value:
                print(f"    设备ID: {message.value.get('deviceId')}")
            if count >= 3:  # 只显示前3条
                break
        
        consumer.close()
        
        if count > 0:
            print(f"\n[OK] 成功读取 {count} 条消息")
            return True
        else:
            print("\n[WARN] 未读取到消息（可能topic为空）")
            return False
            
    except Exception as e:
        print(f"[ERROR] 消费测试失败: {e}")
        return False

def run_all_tests():
    """运行所有测试"""
    print("\n" + "="*60)
    print("  Kafka完整测试套件")
    print("="*60)
    
    results = {}
    
    # 测试1: 连接
    results['connection'] = test_connection()
    
    if results['connection']:
        # 测试2: 发送
        results['send'] = test_send_messages(count=10)
        
        # 等待消息被消费
        if results['send']:
            print("\n等待2秒...")
            time.sleep(2)
            
            # 测试3: 消费
            results['consume'] = test_consume_messages(timeout=5)
    
    # 总结
    print("\n" + "="*60)
    print("  测试结果总结")
    print("="*60)
    
    for test_name, result in results.items():
        status = "[PASS]" if result else "[FAIL]"
        color = "green" if result else "red"
        print(f"{status} {test_name.upper()}")
    
    all_pass = all(results.values())
    
    if all_pass:
        print("\n✓ 所有测试通过！Kafka运行正常")
        return 0
    else:
        print("\n✗ 部分测试失败，请检查Kafka配置")
        return 1

if __name__ == '__main__':
    try:
        exit_code = run_all_tests()
        sys.exit(exit_code)
    except KeyboardInterrupt:
        print("\n\n测试被中断")
        sys.exit(1)
    except Exception as e:
        print(f"\n[FATAL ERROR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
