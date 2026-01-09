#!/bin/bash
# EasyGit 版本发布脚本
# 用法: ./scripts/release.sh <版本号>
# 示例: ./scripts/release.sh 1.1.0

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查参数
if [ -z "$1" ]; then
    echo -e "${RED}错误: 请提供版本号${NC}"
    echo "用法: $0 <版本号>"
    echo "示例: $0 1.1.0"
    exit 1
fi

VERSION=$1
TAG="v${VERSION}"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  EasyGit 版本发布脚本${NC}"
echo -e "${GREEN}  目标版本: ${VERSION}${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 1. 检查当前分支
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo -e "${YELLOW}1. 检查当前分支...${NC}"
if [ "$CURRENT_BRANCH" != "develop" ]; then
    echo -e "${RED}错误: 请在 develop 分支上执行发布操作${NC}"
    echo -e "当前分支: ${CURRENT_BRANCH}"
    exit 1
fi
echo -e "${GREEN}✓ 当前在 develop 分支${NC}"
echo ""

# 2. 检查工作区状态
echo -e "${YELLOW}2. 检查工作区状态...${NC}"
if ! git diff-index --quiet HEAD --; then
    echo -e "${RED}错误: 工作区有未提交的更改${NC}"
    git status
    exit 1
fi
echo -e "${GREEN}✓ 工作区干净${NC}"
echo ""

# 3. 拉取最新代码
echo -e "${YELLOW}3. 拉取最新代码...${NC}"
git pull origin develop
echo -e "${GREEN}✓ 代码已更新${NC}"
echo ""

# 4. 更新版本号
echo -e "${YELLOW}4. 更新版本号...${NC}"
sed -i.bak "s/^pluginVersion=.*/pluginVersion=${VERSION}/" gradle.properties
rm gradle.properties.bak
echo -e "${GREEN}✓ 版本号已更新为 ${VERSION}${NC}"
echo ""

# 5. 提示更新 CHANGELOG
echo -e "${YELLOW}5. 请确认 CHANGELOG.md 已更新${NC}"
echo "请检查 CHANGELOG.md 中是否包含 [${VERSION}] 的更新内容"
read -p "CHANGELOG.md 是否已更新? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${RED}取消发布，请先更新 CHANGELOG.md${NC}"
    git checkout gradle.properties
    exit 1
fi
echo -e "${GREEN}✓ CHANGELOG.md 已确认${NC}"
echo ""

# 6. 提交版本更新
echo -e "${YELLOW}6. 提交版本更新...${NC}"
git add gradle.properties CHANGELOG.md
git commit -m "chore(release): 发布 v${VERSION}"
echo -e "${GREEN}✓ 版本更新已提交${NC}"
echo ""

# 7. 合并到 main
echo -e "${YELLOW}7. 合并到 main 分支...${NC}"
git checkout main
git pull origin main
git merge develop -m "chore(release): 合并 v${VERSION} 到 main"
echo -e "${GREEN}✓ 已合并到 main${NC}"
echo ""

# 8. 创建 Tag
echo -e "${YELLOW}8. 创建 Git Tag...${NC}"
git tag -a "${TAG}" -m "Release ${TAG}"
echo -e "${GREEN}✓ Tag ${TAG} 已创建${NC}"
echo ""

# 9. 推送到远程
echo -e "${YELLOW}9. 推送到远程仓库...${NC}"
read -p "确认推送到远程? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}取消推送${NC}"
    echo "您可以稍后手动执行:"
    echo "  git push origin main"
    echo "  git push origin ${TAG}"
    exit 0
fi

git push origin main
git push origin "${TAG}"
echo -e "${GREEN}✓ 已推送到远程${NC}"
echo ""

# 10. 切回 develop 并更新
echo -e "${YELLOW}10. 切回 develop 分支...${NC}"
git checkout develop
git merge main
git push origin develop
echo -e "${GREEN}✓ develop 分支已同步${NC}"
echo ""

# 11. 构建插件
echo -e "${YELLOW}11. 构建插件...${NC}"
./gradlew clean buildPlugin
echo -e "${GREEN}✓ 插件构建完成${NC}"
echo ""

# 完成提示
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  发布完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${GREEN}版本: ${VERSION}${NC}"
echo -e "${GREEN}Tag: ${TAG}${NC}"
echo ""
echo -e "${YELLOW}下一步操作:${NC}"
echo "1. 访问 GitHub Releases 页面:"
echo "   https://github.com/ddgao/EasyGit/releases/new"
echo ""
echo "2. 选择 Tag: ${TAG}"
echo ""
echo "3. 上传插件文件:"
echo "   $(ls build/distributions/*.zip)"
echo ""
echo "4. 从 CHANGELOG.md 复制发布说明"
echo ""
echo -e "${GREEN}发布流程已完成！${NC}"
