# 企业知识库智能问答系统 — 项目交接文档

## 项目概述

基于 Spring Boot 3 + Spring AI + DeepSeek + Milvus + Elasticsearch + Redis + RabbitMQ + MySQL 的 RAG 全链路知识库问答系统。

- **项目路径:** `C:\Users\86150\knowledge-qa-system`
- **Java 版本:** Amazon Corretto 17.0.19 (`D:\develop\jdk17\jdk17.0.19_10`)
- **Maven:** 3.6.1 (`D:\develop\apache-maven-3.6.1-bin\apache-maven-3.6.1`)
- **Spring Boot:** 3.3.5

---

## 当前进度

### 已完成（阶段一：环境准备）

1. Java 17 安装并配置 JAVA_HOME
2. Maven 编译通过（11个源文件 → BUILD SUCCESS）
3. 项目骨架创建完毕
4. 5个JPA实体类已编写并编译通过
5. 5个配置类已编写并编译通过（Milvus/ES/Redis/RabbitMQ/AppProperties）
6. docker-compose.yml 完整编写（7个服务）
7. application.yml 配置完成

### 待完成

- 阶段二：启动 Docker 基础设施 → 创建 Repository 层 → SQL 初始化
- 阶段三：文档处理流水线（解析/切分/Embedding/双写）
- 阶段四：检索与生成（混合检索+LLM+SSE）
- 阶段五：多轮对话与上下文管理
- 阶段六：版本管理与分类体系

---

## 环境配置要点

### .bash_profile 关键配置

```
export JAVA_HOME="D:/develop/jdk17/jdk17.0.19_10"
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="-Djdk.tls.client.protocols=TLSv1.2 -Dhttps.protocols=TLSv1.2 -Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true"
```

### 关键问题：SSL/TLS

本机 TLS 1.3 握手会失败（"Remote host terminated the handshake"），已通过以下方式解决：
- 强制 TLS 1.2：`-Djdk.tls.client.protocols=TLSv1.2`
- 跳过 SSL 证书验证：`-Dmaven.wagon.http.ssl.insecure=true`
- 阿里云 Maven 镜像的 SSL 也不行，已从全局 settings.xml 中删除
- 使用 Maven Central 直接下载（`repo.maven.apache.org`）

**每个新的 Bash 会话都会自动加载 `.bash_profile` 中的 MAVEN_OPTS。**

### Maven 全局 settings.xml 修改

`D:\develop\apache-maven-3.6.1-bin\apache-maven-3.6.1\conf\settings.xml` 中：
- 阿里云镜像已删除（SSL 不能用）
- 直接使用 Maven Central

---

## 已创建的 Java 文件

### 实体类（src/main/java/com/kqa/entity/）

| 文件 | 对应表 | 说明 |
|------|--------|------|
| Document.java | documents | 文档主表（标题/类型/大小/状态/版本号） |
| Category.java | categories | 分类表（支持二级分类） |
| Tag.java | tags | 标签表 |
| DocumentChunk.java | document_chunks | 文本块（chunk内容/向量ID/ES_ID/元数据） |
| DocumentVersion.java | document_versions | 版本管理（版本号/变更说明/内容哈希） |

### 配置类（src/main/java/com/kqa/config/）

| 文件 | 说明 |
|------|------|
| AppProperties.java | 应用自定义配置（切分/检索/对话参数） |
| MilvusConfig.java | Milvus 向量数据库连接 |
| ElasticsearchConfig.java | ES 客户端配置 |
| RedisConfig.java | Redis 序列化配置（Key=String, Value=JSON） |
| RabbitMQConfig.java | 声明 embedding.task.queue + 消费者配置 |

---

## pom.xml 依赖清单

当前已加入的依赖：
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-data-redis
- spring-boot-starter-amqp
- spring-boot-starter-validation
- mysql-connector-j
- elasticsearch-java (8.15.0)
- milvus-sdk-java (2.4.5)
- lombok

待加入（后续阶段需要时再添加）：
- spring-ai-openai-spring-boot-starter (DeepSeek 集成)
- pdfbox (2.0.31)
- poi-ooxml (5.2.5)

**注意:** maven-compiler-plugin 被降级到 3.10.1（3.13.0 需要 Maven 3.6.3，本机是 3.6.1）。

---

## Docker 服务端口映射

| 服务 | 容器端口 | 映射端口 |
|------|---------|---------|
| MySQL 8.0 | 3306 | 13306 |
| Redis 7 | 6379 | 16379 |
| Elasticsearch 8 | 9200 | 19200 |
| RabbitMQ AMQP | 5672 | 5672 |
| RabbitMQ Mgmt | 15672 | 15672 |
| Milvus | 19530 | 19530 |
| MinIO (Milvus依赖) | 9000/9001 | 9000/9001 |
| etcd (Milvus依赖) | 2379 | 2379 |

---

## 下一步操作指南

### 第1步：启动 Docker Desktop 并运行服务

```bash
cd C:\Users\86150\knowledge-qa-system
docker compose up -d
```

等待所有容器 healthy（约2-3分钟），检查：
```bash
docker compose ps
```

### 第2步：配置 DeepSeek API Key

编辑 `.env` 文件，填入真实的 API Key：
```
DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
```

### 第3步：创建 SQL 初始化脚本

需要创建 `sql/init.sql`，内容包括：
- categories 预置数据（技术文档/管理制度/产品手册/培训材料）
- 数据库字符集确认

### 第4步：创建 Repository 层

为每个实体创建 Spring Data JPA Repository 接口。

### 第5步：编写 Service 和 Controller 层

按阶段三~六的计划逐步实现。

---

## 验证命令

```bash
# 编译
cd C:\Users\86150\knowledge-qa-system
mvn compile -DskipTests

# 启动应用（等Docker服务就绪后）
mvn spring-boot:run

# 健康检查
curl http://localhost:8080/actuator/health
```

---

## 完整架构流程

```
文档入库:
  PDF/Word/TXT → 文档解析 → 文本切分(chunk) → RabbitMQ异步Embedding
  → Milvus(向量) + ES(文本)双写 → MySQL(元数据+版本)

问答:
  用户提问 → 问题改写 → ES关键词检索 + Milvus向量检索
  → RRF融合排序 → Top-K文档片段 → Prompt组装
  → DeepSeek LLM → SSE流式输出
  ↑ Redis多轮对话上下文
```
