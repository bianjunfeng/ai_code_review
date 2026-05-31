# AI PR Review - 一键部署

## 环境要求

- Docker ≥ 20.10
- Docker Compose ≥ 2.0

## 快速开始

```bash
# 1. 克隆项目
git clone <仓库地址>
cd ai-pr-review/deploy

# 2. 配置环境变量
cp .env.example .env
vim .env    # 填入 GitHub Token 和 AI API Key

# 3. 启动（在 deploy 目录执行，Compose 会使用项目根目录作为构建上下文）
docker compose up -d --build
```

## 访问

浏览器打开 `http://<服务器IP>`

## 常用命令

```bash
docker compose ps              # 查看容器状态
docker compose logs -f app     # 查看应用日志
docker compose restart         # 重启所有服务
docker compose down            # 停止并删除容器
docker compose down -v         # 停止并删除容器+数据（⚠️ 数据库数据会丢失）
```

## 服务架构

```
┌─────────────────┐
│   Nginx (:80)   │  ← 前端静态文件 + API 代理
├─────────────────┤
│  Java (:8080)   │  ← Spring Boot 后端
├─────────────────┤
│  MySQL (:3306)  │  ← 数据库
│  Redis (:6379)  │  ← 缓存
└─────────────────┘
```
