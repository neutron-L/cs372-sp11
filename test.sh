#!/bin/bash

# 检查参数数量
if [ $# -ne 2 ]; then
    echo "使用方法: $0<指令> <次数>"
    echo "示例: $0 'ls -l' 5"
    exit 1
fi

# 获取参数
command_to_run="$1"
times="$2"

# 验证次数是否为正整数
if ! [[ "$times" =~ ^[1-9][0-9]*$ ]]; then
    echo "错误: 次数必须是一个正整数。"
    exit 1
fi

# 执行指定次数的指令
for ((i=1; i<=times; i++)); do
    echo "第 $i 次执行:"
    eval "$command_to_run"
    
    # 检查上一个命令的退出状态
    exit_status=$?
    if [ "$exit_status" -ne 0 ]; then
        echo "第 $i 次执行失败，退出状态码: $exit_status"
        exit "$exit_status"
    fi
    
    echo "第 $i 次执行成功"
done

echo "所有 $times 次执行均成功完成。"