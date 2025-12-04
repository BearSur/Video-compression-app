#!/usr/bin/env python3
"""
Git提交日期调整工具 - 修复版
修复获取提交历史的问题
"""

import os
import sys
import subprocess
from datetime import datetime, timedelta

def run_git(cmd, capture_output=True):
    """执行git命令，处理Windows编码问题"""
    try:
        if capture_output:
            result = subprocess.run(
                cmd, 
                shell=True, 
                capture_output=True, 
                text=True, 
                encoding='utf-8',
                errors='ignore'  # 忽略无法解码的字符
            )
            if result.returncode == 0:
                return result.stdout.strip()
            else:
                # 打印错误信息以便调试
                print(f"Git命令执行失败 (exit code {result.returncode}): {cmd}")
                if result.stderr:
                    print(f"错误信息: {result.stderr[:200]}")
                return None
        else:
            result = subprocess.run(cmd, shell=True)
            return result.returncode == 0
            
    except Exception as e:
        print(f"执行命令时出错: {e}")
        return None

def get_commits():
    """获取提交列表，处理可能的编码问题"""
    print("正在获取提交历史...")
    
    # 先检查是否有提交
    check_result = run_git("git rev-list --count HEAD")
    if check_result is None:
        # 尝试另一种方法检查
        check_result = run_git("git log --oneline | wc -l")
    
    if not check_result or check_result == '0':
        print("仓库中没有提交")
        return []
    
    print(f"找到 {check_result} 个提交")
    
    # 方法1: 使用git log获取所有信息
    cmd = "git log --reverse --format=%H%n%aI%n%s%n==END=="
    output = run_git(cmd)
    
    if not output:
        # 方法2: 使用git log --oneline
        print("尝试使用简化的方法获取提交...")
        output = run_git("git log --reverse --oneline")
        if output:
            commits = []
            lines = output.split('\n')
            for i, line in enumerate(lines):
                if line.strip():
                    parts = line.split(' ', 1)
                    if len(parts) >= 2:
                        commits.append({
                            'hash': parts[0],
                            'msg': parts[1],
                            'index': i + 1,
                            'date': '未知日期',
                            'original_date': None
                        })
            return commits
        else:
            print("无法获取提交历史")
            return []
    
    # 解析输出
    commits = []
    blocks = output.split('==END==\n')
    
    for i, block in enumerate(blocks):
        lines = block.strip().split('\n')
        if len(lines) >= 3:
            commit_hash = lines[0].strip()
            date_str = lines[1].strip()
            message = lines[2].strip()
            
            # 尝试格式化日期
            try:
                if 'Z' in date_str:
                    date_str = date_str.replace('Z', '+00:00')
                commit_date = datetime.fromisoformat(date_str)
                date_formatted = commit_date.strftime("%Y-%m-%d %H:%M")
            except:
                date_formatted = date_str[:16] if len(date_str) >= 16 else date_str
            
            commits.append({
                'hash': commit_hash,
                'msg': message[:100],
                'index': i + 1,
                'date': date_formatted,
                'original_date': lines[1].strip()
            })
    
    return commits

def show_commit_summary(commits):
    """显示提交摘要"""
    if not commits:
        print("没有找到提交")
        return False
    
    print(f"\n找到 {len(commits)} 个提交:")
    print("=" * 80)
    for i, commit in enumerate(commits):
        # 只显示前20个提交，避免太多
        if i < 20:
            print(f"{commit['index']:3}. [{commit['date']}] {commit['msg']}")
        elif i == 20:
            print(f"... 还有 {len(commits) - 20} 个提交未显示")
    print("=" * 80)
    return True

def calculate_new_date(old_date_str, days):
    """计算新日期"""
    if not old_date_str:
        return None
    
    try:
        # 清理日期字符串
        date_str = old_date_str.strip()
        if 'Z' in date_str:
            date_str = date_str.replace('Z', '+00:00')
        
        # 解析日期
        old_date = datetime.fromisoformat(date_str)
        new_date = old_date + timedelta(days=days)
        return new_date.strftime("%Y-%m-%dT%H:%M:%S")
    except Exception as e:
        print(f"日期计算错误: {e}，原始日期: {old_date_str}")
        return None

def manual_adjust_mode(commits):
    """手动调整模式"""
    print("\n进入手动调整模式")
    print("=" * 60)
    
    adjustments = {}
    
    for commit in commits:
        print(f"\n提交 #{commit['index']}")
        print(f"日期: {commit['date']}")
        print(f"消息: {commit['msg']}")
        print("-" * 40)
        
        while True:
            print("选项:")
            print("  <数字>  : 调整天数（正数向前，负数向后）")
            print("  skip    : 跳过此提交")
            print("  view    : 查看提交详情")
            print("  quit    : 退出程序")
            
            choice = input("请输入选择: ").strip().lower()
            
            if choice == 'quit':
                print("退出程序")
                return None
            
            elif choice == 'skip':
                adjustments[commit['hash']] = 0
                print(f"跳过提交 #{commit['index']}")
                break
            
            elif choice == 'view':
                # 显示提交详情
                details = run_git(f"git show {commit['hash']} --stat --no-patch")
                if details:
                    print("\n提交详情:")
                    print(details[:500])  # 限制输出长度
                else:
                    print("无法获取提交详情")
            
            else:
                try:
                    days = int(choice)
                    adjustments[commit['hash']] = days
                    
                    if days == 0:
                        print(f"提交 #{commit['index']} 将保持不变")
                    else:
                        direction = "向前" if days > 0 else "向后"
                        # 计算新日期
                        new_date_str = calculate_new_date(commit.get('original_date'), days)
                        if new_date_str:
                            try:
                                new_date_display = datetime.fromisoformat(
                                    new_date_str.replace('Z', '+00:00')
                                ).strftime("%Y-%m-%d %H:%M")
                                print(f"提交 #{commit['index']} 将{direction}调整 {abs(days)} 天")
                                print(f"新日期: {new_date_display}")
                            except:
                                print(f"提交 #{commit['index']} 将{direction}调整 {abs(days)} 天")
                        else:
                            print(f"提交 #{commit['index']} 将{direction}调整 {abs(days)} 天")
                    break
                    
                except ValueError:
                    print("无效输入，请输入数字或命令")
    
    return adjustments

def batch_adjust_mode(commits):
    """批量调整模式"""
    print("\n进入批量调整模式")
    print("-" * 60)
    
    try:
        days = int(input("为所有提交输入统一调整天数: "))
    except ValueError:
        print("请输入有效的数字")
        return None
    
    adjustments = {}
    for commit in commits:
        adjustments[commit['hash']] = days
    
    direction = "向前" if days > 0 else "向后"
    print(f"所有 {len(commits)} 个提交将{direction}调整 {abs(days)} 天")
    
    return adjustments

def show_adjustment_summary(commits, adjustments):
    """显示调整摘要"""
    if not adjustments:
        return False
    
    print("\n调整摘要:")
    print("=" * 80)
    
    has_changes = False
    for commit in commits:
        days = adjustments.get(commit['hash'], 0)
        if days != 0:
            has_changes = True
            direction = "向前" if days > 0 else "向后"
            new_date_str = calculate_new_date(commit.get('original_date'), days)
            if new_date_str:
                try:
                    new_date_display = datetime.fromisoformat(
                        new_date_str.replace('Z', '+00:00')
                    ).strftime("%Y-%m-%d %H:%M")
                    print(f"提交 #{commit['index']:3}: {direction:2} {abs(days):3} 天 | {commit['date']} → {new_date_display} | {commit['msg'][:40]}")
                except:
                    print(f"提交 #{commit['index']:3}: {direction:2} {abs(days):3} 天 | {commit['msg'][:40]}")
            else:
                print(f"提交 #{commit['index']:3}: {direction:2} {abs(days):3} 天 | {commit['msg'][:40]}")
    
    if not has_changes:
        print("没有需要调整的提交")
        return False
    
    print("=" * 80)
    return True

def simple_execution_method(commits, adjustments):
    """简单的执行方法：提供操作指南"""
    print("\n请按照以下步骤手动执行:")
    print("=" * 60)
    
    # 找出需要调整的提交
    edit_commits = []
    for commit in commits:
        days = adjustments.get(commit['hash'], 0)
        if days != 0:
            edit_commits.append((commit, days))
    
    if not edit_commits:
        print("没有需要调整的提交")
        return True
    
    print(f"\n需要调整 {len(edit_commits)} 个提交")
    
    # 获取根提交或第一个提交的父提交
    first_commit = commits[0]
    parent_commit = run_git(f"git rev-list --max-parents=0 {first_commit['hash']}")
    if not parent_commit:
        parent_commit = run_git(f"git rev-parse {first_commit['hash']}^")
    
    if not parent_commit:
        # 如果没有父提交，从第一个提交开始
        print("\n1. 开始交互式变基:")
        print(f"   git rebase -i --root")
    else:
        print("\n1. 开始交互式变基:")
        print(f"   git rebase -i {parent_commit}")
    
    # 步骤2: 修改待办列表
    print("\n2. 在编辑器中，将以下提交前的 'pick' 改为 'edit':")
    for commit, days in edit_commits:
        direction = "向前" if days > 0 else "向后"
        print(f"   - 提交 #{commit['index']} [{commit['hash'][:8]}]: {direction} {abs(days)} 天")
    
    # 步骤3: 保存后执行命令
    print("\n3. 保存退出后，对每个暂停的提交执行:")
    for commit, days in edit_commits:
        new_date_str = calculate_new_date(commit.get('original_date'), days)
        if new_date_str:
            print(f"\n   提交 #{commit['index']} [{commit['hash'][:8]}]:")
            print(f"   git commit --amend --no-edit --date=\"{new_date_str}\"")
            print(f"   GIT_COMMITTER_DATE=\"{new_date_str}\" git commit --amend --no-edit --date=\"{new_date_str}\"")
        print(f"   git rebase --continue")
    
    print("\n4. 如果遇到错误，可以取消变基:")
    print("   git rebase --abort")
    
    print("\n5. 完成后查看结果:")
    print("   git log --oneline -10")
    
    return True

def main():
    print("=" * 70)
    print("Git提交日期调整工具")
    print("=" * 70)
    
    # 检查是否在git仓库中
    if not os.path.exists(".git"):
        print("错误: 当前目录不是Git仓库")
        print("请将此脚本放在.git文件夹所在的目录下运行")
        input("按Enter键退出...")
        return
    
    # 获取提交列表
    commits = get_commits()
    if not commits:
        print("\n可能的原因:")
        print("1. 仓库中没有提交")
        print("2. 当前在空分支上")
        print("3. Git配置问题")
        print("\n建议检查:")
        print("  git log --oneline")
        input("按Enter键退出...")
        return
    
    if not show_commit_summary(commits):
        input("按Enter键退出...")
        return
    
    # 选择操作模式
    print("\n选择操作模式:")
    print("1. 逐个提交调整（推荐）")
    print("2. 批量统一调整")
    print("3. 退出")
    
    try:
        mode = input("\n请选择模式 (1/2/3): ").strip()
    except KeyboardInterrupt:
        print("\n操作中断")
        return
    
    if mode == '3':
        print("退出程序")
        return
    
    # 获取调整设置
    adjustments = None
    
    if mode == '1':
        adjustments = manual_adjust_mode(commits)
    elif mode == '2':
        adjustments = batch_adjust_mode(commits)
    else:
        print("无效选择")
        return
    
    if adjustments is None:
        print("操作取消")
        return
    
    # 显示调整摘要
    if not show_adjustment_summary(commits, adjustments):
        input("按Enter键退出...")
        return
    
    # 确认执行
    print("\n确认执行调整吗？")
    print("这将修改提交历史，可能会导致提交哈希改变。")
    print("如果已经推送到远程仓库，需要强制推送。")
    
    confirm = input("\n确定要执行吗？(输入 'yes' 继续): ").strip().lower()
    if confirm != 'yes':
        print("操作已取消")
        return
    
    # 执行调整
    success = simple_execution_method(commits, adjustments)
    
    if success:
        print("\n✅ 操作指南已生成")
    else:
        print("\n❌ 操作失败或已取消")
    
    input("\n按Enter键退出...")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n操作被用户中断")
        input("按Enter键退出...")
    except Exception as e:
        print(f"\n发生错误: {e}")
        import traceback
        traceback.print_exc()
        input("\n按Enter键退出...")