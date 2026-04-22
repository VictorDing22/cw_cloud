#!/bin/bash
# 停止分布式滤波服务

echo "停止分布式滤波服务..."

pkill -f "dispatcher.py" 2>/dev/null
pkill -f "filter_worker.py" 2>/dev/null
pkill -f "aggregator.py" 2>/dev/null

echo "已停止所有分布式服务进程"
